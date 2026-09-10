# AI Chat 首字响应（TTFT）性能诊断与修复

> 范围：本次专项任务只做「定位延迟 + 修复首字慢 + 强化诊断日志」，不新增业务功能，不改动 Memory / RAG / Provider 既有架构契约。
> 涉及模块：Spring `ai.stream`、Spring `conversation.memory`、Python `providers` / `services/chat`。

## 1. 目标与判定口径

- **TTFT（Time To First Token）**：用户点击发送 → 浏览器第一次渲染出 AI 增量文本。
- **不看**：完整回答生成总时长、后处理（摘要 / 记忆抽取）时延。
- **禁止**：任何一层把 SSE 缓冲成「生成完成后一次性返回」。
- 验收：Provider 一旦产出首个 token，必须尽快（逐 token）抵达 Vue。

## 2. 延迟层与定位结论

链路：`Vue → Spring Boot → Python AI → LLM Provider`，全程 SSE 流式。

| 层 | 是否流式 | 本次发现的问题 | 结论 |
|----|----------|----------------|------|
| Vue `useChatStream` | 是（`fetch` + `ReadableStream` 逐行解析 `event:`） | 收到 `start` 即 `addPlaceholders`（骨架/思考态） | 正常，不是瓶颈 |
| Spring `JdkHttpAiGateway` | 是（`HttpClient.send` + `ofInputStream` 逐行解析） | 解析正确，无缓冲 | 正常，不是瓶颈 |
| Python `ChatService.stream` | 是（`async for` 逐 chunk 转发） | 转发前**无隐藏缓冲** | 正常，不是瓶颈 |
| Python `OpenAICompatibleProvider.stream_chat` | 是（`httpx.stream` + `aiter_lines`） | 单组合超时曾误杀慢 provider | 修复见 §4 |
| Spring `AiStreamingService` / `Persistence` | 是（worker 线程逐 `delta` 发 SSE） | **`prepare()` 在控制器线程、SSE 未开启前就同步做 RAG 检索（含 query embedding 远程调用）并持有 `FOR UPDATE` 行锁** | **根因，修复见 §3** |

### 根因（P0）

原本的 `AiStreamPersistenceService.prepare()` 在 **HTTP 控制器线程** 上：

1. 短事务保存 USER / ASSISTANT 占位 / `ai_request_log`；
2. **同步调用 `contextBuilder.build()`** 组装上下文 —— 其中 `RagContextService.retrieve()` 会向 Python 发起 **query embedding（远程调用）**，并可能做 Memory 检索；
3. 这一步发生在 `SseEmitter` 真正打开并向浏览器 `send("start")` **之前**，且整个过程持有着 conversation 的 `FOR UPDATE` 悲观锁。

后果：RAG embedding 的远程往返（数十毫秒到数秒，甚至冷启动更长）全部阻塞在「用户点击发送 → 浏览器可见第一字节」之间，浏览器在此期间完全空白；同时该事务期间其他会话/该会话的并发请求也被行锁挡住。

> 注意：本次为专项性能修复，上下文内容顺序、Memory / RAG / Summary 的组装规则**完全不变**，只是把这段构建从「控制器线程、SSE 开启前」挪到「流式 worker 线程、已发出 `start` 之后」。

## 3. Spring 侧修复

文件：`ai/stream/AiStreamPersistenceService.java`、`ai/stream/AiStreamingService.java`、`conversation/memory/ConversationContextBuilder.java`

- `prepare()` / `prepareRegenerate()` / `prepareEditAndResend()` 仍在同一短事务里做快速 DB 落库 + 额度预留（保持「超额即回滚、不建占位/不建 SSE」的 HTTP 契约）。但额度估计所用的上下文只走 **本地部分**（system + summary + memory + 近期消息 + 当前消息），**不包含远程 RAG query embedding**，因此控制器线程上没有任何网络调用。
- 远程 RAG embedding 被推迟到 **worker 线程、`start` 已发出之后** 才执行：新增 `AiStreamPersistenceService.buildContext(PreparedAiStream)`（`@Transactional(readOnly = true)`）在流式 worker 中构建**完整上下文**（含 RAG）。此时 `prepare()` 的 `FOR UPDATE` 锁早已释放，RAG 远程时延**不再阻塞**其他请求，也**不再阻塞首字前的可见反馈**。
- `AiStreamingService.run()`：先 `markStreaming` + `send("start")`（浏览器立即进入「生成中」骨架态），**再** `buildContext`（含 RAG 远程调用），随后才向 Python 发流。
- `PreparedAiStream.messages()` 在 `prepare()` 阶段仅填入**本地估算上下文**（供 `reserveUsage` 计数，且为保守上估）；真正发给 Provider 的 `AiGatewayRequest` 由 worker 的 `buildContext()` 结果填充，二者互不影响。

行为变化：浏览器从「发送后空白 N 秒」变为「发送后几乎立即显示生成骨架，N 秒后出首字」。首字总时延 = 上下文构建（Memory/RAG）+ Python→Provider 往返，二者均有日志可观测（见 §5）。

## 4. Python 侧修复

文件：`app/core/settings.py`、`app/providers/openai_compatible/provider.py`、`app/main.py`、`app/services/chat.py`

- `settings.provider_stream_read_timeout_seconds`（默认 600s）：为**已打开的流式连接**设置独立的「两次字节之间」空闲读超时，远大于原本单一的 `provider_timeout_seconds`（连接/写/池超时仍保持较紧）。避免 provider 在 token 之间停顿（思考 / 调度）时被组合超时误杀，从而中断本就健康的流。
- `OpenAICompatibleProvider.stream_chat`：用 `httpx.Timeout(connect, read=stream_read_timeout, write, pool)` 仅覆盖流式请求；非流式 `chat` / `embedding` 仍用较紧的默认超时。
- 新增首字计时日志（见 §5）。

## 5. 端到端诊断日志（按 requestId 关联）

**关联键**：Spring 侧的 `requestId` 与 Python 侧收到的 `ai_request_id` 是**同一个值**（Spring `JdkHttpAiGateway` 以 `aiRequestId` 下发，Python `ChatRequest.ai_request_id` 接收）。可直接跨服务 `grep` 串联。

### Spring 侧（INFO，按 `requestId=` 过滤）

```
AI stream started requestId={id} userId=... conversationId=... provider=... model=...
AI stream context built requestId={id} contextMs={c} messageCount={n}
AI stream first token requestId={id} springPreprocessMs={spring} pythonTtftMs={py} totalTtftMs={tot}
AI stream completed requestId={id} status=COMPLETED totalTokens={t}
```

### Python 侧（INFO，按 `ai_request_id=` 过滤）

```
chat stream start ai_request_id={id} provider_key=... model=... message_count={n}
provider first token base_url=... model=... ttft_ms={p}
chat stream first token ai_request_id={id} serviceTtftMs={s}
```

### 诊断决策树（TTFT 分桶）

设 Spring `totalTtftMs = springPreprocessMs + pythonTtftMs`，且 `springPreprocessMs ≈ contextMs`。

1. **`contextMs` 高（如 > 1s）** → Memory / RAG 检索慢，通常是 **RAG query embedding 远程调用** 耗时。
   - 检查 `Context build requestId=... memoryMs=... ragMs=...`（见 `ConversationContextBuilder`）。
   - 方向：embedding 结果缓存、RAG 检索异步化、缩小 RAG Top-K / token budget。
2. **`pythonTtftMs` 高** → Spring→Python 或 Python→Provider 这一腿慢。
   - 对照 Python `provider first token ttft_ms={p}`：
     - `p` 高 → provider 冷启动 / 排队 / 推理首 token 慢（provider 侧问题）。
     - `serviceTtftMs({s})` 明显 > `p` → Python 在 provider 与转发之间有额外开销（当前无缓冲，理论上 `s ≈ p`；若偏差大需查 Python 中间件）。
   - 网络 / 网关：确认 `X-Accel-Buffering: no`、`Cache-Control: no-cache` 已下发（Spring `JdkHttpAiGateway` 与 Python router 均已设置）。
3. **`contextMs` 与 `pythonTtftMs` 都小，但浏览器仍明显延迟** → 多半是客户端渲染 / 首屏骨架未立即出现，复核 Vue `useChatStream` 是否收到 `start` 才 `addPlaceholders`（当前实现已是）。

> 计时全部基于 `System.nanoTime()`（Spring）/ `time.perf_counter_ns()`（Python），仅记录毫秒级时延，不记录 Prompt、Token 内容、密钥或 Provider body。

## 6. 回归与边界

- 不变量：上下文构建顺序（System → Short Summary → Long Memory → RAG → Recent Messages → Current User）、Memory/RAG 开关与额度预留逻辑均未改变；`buildContext` 仅在 worker 阶段执行一次，结果等价。
- 仍保留的全局总超时 `app.ai.stream.request-timeout`（默认 PT2M）由 Spring watchdog 兜底整体生成时长，与「两次 token 间空闲读超时」分离，互不干扰。
- 测试：延用既有 `AiStreamingIntegrationTests`（FakeAiGateway 同步消费事件）、`JdkHttpAiGatewayTests`（真实 HTTP SSE 解析）、`ConversationContextBuilder` 相关测试；`buildContext` 在 worker 阶段调用，`PreparedAiStream.messages()` 初始为 `null`，不再依赖控制器阶段赋值。
