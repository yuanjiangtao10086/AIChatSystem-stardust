# 技术债清单（三端架构巡检）

> 巡检日期：2026-09-11
> 巡检范围：`stardust_vue/`、`stardust_springboot/`、`stardust_ai/`
> 巡检性质：**只读巡检 + 安全修复**。严重/高等级且可安全修改的问题已直接修复（见第一节）；
> 中低等级、以及高风险（需契约/数据库/较大重构）的问题记录在本文中。
> 原则：**不因本文档而提前做大规模无理由重构**，每条都标注了触发时机。

---

## 0. 本次已直接修复的问题（严重/高，安全）

| 端 | 问题 | 处理 |
| --- | --- | --- |
| Vue | SSE `reader` 从不释放，`api/conversations.ts:77` 无 `try/finally`，异常/中止时响应体长期锁定 | 包 `try/finally`，`finally` 中 `reader.cancel()` |
| Vue | `useChatWorkspace` 无 `onUnmounted`，切会话/离开页面后旧流继续把 delta 写入新会话 | 新增 `onUnmounted` abort；`openConversation` 先 abort 旧流；`onEvent` 增加会话一致性守卫 |
| Vue | 一次「停止」触发两次全量刷新（`run.finally` 与 `stop()` 各一次，单次 3 个请求） | `run` 识别 `AbortError`，中止时不再刷新；仅 `stop()` 刷新 |
| Vue | `App.vue:48` 定义 `isAdminRoute` 但未在 `setup` 返回，模板恒为 `!undefined`，`/admin` 下会串显前台顶栏 | 补进返回值 |
| Vue | 4 处搜索防抖 `setTimeout` 未在卸载时清理（`AdminUsersView`、`AdminKnowledgeView`、`AdminFilesView`、`AdminConversationsView`） | 各补 `onUnmounted(() => window.clearTimeout(searchTimer))` |
| 三端 | 管理台可配 `defaultTemperature/defaultMaxOutputTokens`，但 Vue `AiModel` 无 `defaultModel`，前端硬选第一个模型 | `types/conversation.ts` 补 `defaultModel`；`useChatWorkspace` 优先选默认模型 |
| Python | **严重**：`vectorstores/sqlite.py` 全部 SQLite 操作在 `async def` 内同步执行，阻塞事件循环，一次慢查询冻结所有并发流 | 改为单一专用工作线程（`ThreadPoolExecutor(max_workers=1)`）+ `run_in_executor` 卸载，并把 `sqlite3.Error` 归一化为 `VectorStoreError` |
| Python | `services/chat.py` 仅当 provider 返回 usage 才发 `usage` 事件，缺失时 Spring 侧用量为 null | 未发送过则在 `done` 前补发零值 `usage` 并告警 |
| Python | `provider.py:_raise_for_status` 把 401/403/404/400 一律折叠为 `502 PROVIDER_REQUEST_REJECTED`，配置错误被当成上游故障且无日志 | 细分 `PROVIDER_AUTHENTICATION_FAILED` / `PROVIDER_RESOURCE_NOT_FOUND`，并补脱敏状态日志 |
| Python | `main.py` 模块导入即 `create_app()`，产生打开 sqlite、写 `data/rag-vectors.sqlite3` 的导入副作用 | 改为 PEP 562 惰性 `__getattr__`（带缓存），`uvicorn app.main:app` 行为不变 |
| 三端 | **严重**：Python/RAG 网关的结构化错误码在 Spring 侧无任何映射，413/429/502/504 全部降级为 `50001` | 新增 `common/exception/GatewayErrorCodeMapper` 与 `RagGatewayExceptionHandler`、`AiGatewayExceptionHandler` |

---

## 1. Vue

### 中

| 编号 | 问题 | 位置 | 说明与建议 |
| --- | --- | --- | --- |
| V-M1 | 组件越权承担数据获取（5 处），其中 1 处为写操作 | `components/chat/ChatAttachment.vue:46,69`、`components/chat/KnowledgeBaseSelector.vue:37-41,54-67,78`、`components/files/FileDetailPanel.vue:51,75`、`components/usage/UsagePanel.vue:50`、`components/admin/AdminUsage.vue:74` | `KnowledgeBaseSelector` 直接 `setConversationKnowledgeBases` 是写操作，应上提到页面/composable。建议：展示型组件改为 props/emit，数据获取统一在 view/composable。 |
| V-M2 | `query()` 查询串工具重复两份且行为不一致 | `api/admin.ts:10-19` vs `api/usage.ts:8-18` | admin 版空参留 `?`，usage 版返回裸路径。抽到 `api/client.ts` 统一。 |
| V-M3 | `formatBytes` 重复两份，且数据层导出 UI 函数 | `api/files.ts:74-84` vs `utils/admin.ts:5-15` | `api/files.ts` 被 5 个组件当 utils 引用。迁到 `utils/format.ts`。 |
| V-M4 | 「解析错误响应 + 抛 `ApiError`」重复 3 处 | `api/conversations.ts:71-74`、`api/files.ts:56-60`、`api/admin.ts:184-189` | 抽 `unwrap(response)`。 |
| V-M5 | 下载 blob 逻辑整体复制两份，且靠 1 秒定时器释放 URL | `api/files.ts:64-72`、`api/admin.ts:177-196` | 抽公共函数；参照 `FileDetailPanel.vue:61-88`（全仓唯一正确做法）做确定性释放。 |
| V-M6 | 类型重复：同一概念多套定义 | `types/admin.ts:85-87`（`AdminMessage` 内联角色/内容类型、`status` 退化为 `string`）、`:71-81` 与 `:93-105`（`AdminConversation/Detail` 前 8 字段重复）、`:106-117` 与 `:123-142`（`AdminFile/Detail` 重复且 `mime` vs `declaredMime/detectedMime` 不一致）、`types/auth.ts:4-10` 与 `types/admin.ts:54-70`（两套 User） | 用 `extends` 复用，复用 `MessageRole`/`MessageContentType`/`MessageStatus`。 |
| V-M7 | 401 自动重试会重放非幂等 POST | `api/client.ts:88-93`（`retryAfterRefresh` 默认 `true`）+ `:82` 原样重发 | `createAdminUser`、`createKnowledgeBase` 等 POST 401 后会带原 body 重发，存在重复创建风险。建议 POST 默认 `retryAfterRefresh=false` 或依赖 `Idempotency-Key`。 |

### 低

| 编号 | 问题 | 位置 | 说明与建议 |
| --- | --- | --- | --- |
| V-L1 | 死代码：重复组件 + 7 个 0 字节 `.vue` | `components/admin/AdminUsers.vue`（244 行，全仓 import 命中 0 次）、`AdminAi.vue`、`AdminAudit.vue`、`AdminConversations.vue`、`AdminDashboard.vue`、`AdminFiles.vue`、`AdminRag.vue`、`views/AdminView.vue` | 删除或标注为占位。 |
| V-L2 | 展示型组件耦合全局 store / 微定时器未清理 | `components/chat/ChatSidebar.vue:81-91`（叶子组件 dispatch `auth/logout`）、`ChatMessage.vue:91`、`ChatCodeBlock.vue:29`（`setTimeout` 无卸载清理） | 副作用上提；定时器在 `onUnmounted` 清理。 |

### 高（未修复，需较大改动）

| 编号 | 问题 | 位置 | 为何不在本次修复 |
| --- | --- | --- | --- |
| V-H1 | 巨型页面：`AdminAiView.vue` 691 行（Tab 状态机 + 两张表格 + 3 个弹窗 + 6 类确认状态机 + 排序算法 + 10 个 API，setup 返回 34 个绑定） | `views/admin/AdminAiView.vue` | 拆分需按「列表/表单弹窗/确认」重组，风险高于本次巡检预期；建议独立任务。 |
| V-H2 | `AdminUsersView.vue` 551 行：列表 + 4 弹窗 + 6 态状态机 + 防抖 | `views/admin/AdminUsersView.vue` | 同上。 |
| V-H3 | `KnowledgeView.vue` 599 行：列表 + 流水线 + 上传 + 内联 Modal + 290 行 scoped CSS，未复用 `AdminModal.vue` | `views/KnowledgeView.vue` | 同上。 |
| V-H4 | `AdminUserDetailView.vue` 441 行，与 `AdminUsersView` 的角色/改密/确认三段逐行同构（`:311/:333/:366` vs `:367/:392/:459`） | `views/admin/AdminUserDetailView.vue:274-399` | 需先抽 `useUserRoleActions` 之类 composable，属重构。 |
| V-H5 | access token 存在两份真相源 | `api/client.ts:31`（模块级内存）vs `store/auth.ts:11`（Vuex state），依赖 5 处手工 `setAccessToken()` 同步 | 统一需改动所有调用点，建议独立任务：store 改为只读 `getAccessToken()`。 |
| V-H6 | 认证端点硬编码在 Vuex store，`api/` 层缺 auth 模块 | `store/auth.ts:64,73,83,112,120,131,143` | 分层重构，与 V-H5 一并处理。 |

---

## 2. Spring Boot

> 说明：本轮 Spring 端巡检以手工核查 + 既有集成测试为据。已确认的事实：
> - 全局**未使用** `@PreAuthorize`/方法级安全，管理端权限由 `SecurityConfiguration` 的 URL matcher 统一拦截（`/api/v1/admin/**`）。新增管理端接口只要落在 `/api/v1/admin/**` 即受保护；**但新增非 `/admin` 前缀的高危接口不会有方法级兜底**。
> - 未发现 Controller 直接返回/接收 JPA Entity（DTO 边界保持完好）。
> - `IllegalStateException`/`IllegalArgumentException` 共 32 处，绝大多数是实体不变式与配置校验（fail-fast，可接受）；少数 `.orElseThrow(() -> new IllegalStateException("... row is missing"))` 属内部不变式，非对外错误语义。
> - 引入 `spring-boot-starter-data-redis` 后，Spring Data 进入 strict repository 模式，启动日志出现「Could not safely identify store assignment for repository candidate」INFO 噪音（JPA 仓库仍以 DEFAULT 模式正确引导）。

### 中

| 编号 | 问题 | 位置 | 说明与建议 |
| --- | --- | --- | --- |
| S-M1 | 无方法级安全兜底 | 全仓 `@PreAuthorize` 命中 0 次 | 现状可用；建议在 `SecurityConfiguration` 之外，对非 `/admin` 前缀的敏感接口补 `@PreAuthorize`，形成双保险。 |
| S-M2 | 内部不变式使用 JDK 异常 | `file/service/UserFileService.java:97`、`file/service/FilePersistenceService.java:110`、`auth/service/RefreshTokenService.java:68` 等 `.orElseThrow(IllegalStateException)` | 若这些分支理论上不可达，可保留；若可能由数据损坏触发，应改为 `BusinessException(ErrorCode.PERSISTENCE_ERROR)`。 |
| S-M3 | 引入 Redis 后 Spring Data 严格模式噪音 | 启动日志（多模块仓库识别） | 若后续继续用 Redis，建议用 `@EnableJpaRepositories`/`@EnableRedisRepositories` 显式划分包，消除歧义。 |

### 低

| 编号 | 问题 | 位置 | 说明与建议 |
| --- | --- | --- | --- |
| S-L1 | God Controller 倾向 | `admin/controller/AdminController.java`（约 14.7KB，承担用户/会话/文件/知识库/Provider/Model/AI 请求/审计等全部管理端点） | 当前按 URL matcher 授权尚可维护；建议未来按资源拆分为多个 Controller（用户/内容/AI 目录/审计）。 |
| S-L2 | `AdminResourceService` / `AdminAiService` 体量偏大 | `admin/service/AdminResourceService.java`、`admin/service/AdminAiService.java`（各约 15KB） | 与 S-L1 一并按资源拆分。 |

---

## 3. Python AI

### 中

| 编号 | 问题 | 位置 | 说明与建议 |
| --- | --- | --- | --- |
| P-M1 | `ChatService.stream()` 职责混杂：provider 解析 + 模型解析 + 事件构造 + 三类异常分支 + 手工 SSE 编码 | `app/services/chat.py:44-161`、`_sse()` `:190-196` | SSE **编码**在 chat.py 手工拼字符串，**解码**在 `provider.py:149-170`，编解码不对称。建议抽 `SseEncoder` / 事件工厂。 |
| P-M2 | `seq` 由 5 处事件分支手工自增，新增事件漏写即触发 Spring `AI_PROTOCOL_ERROR` | `app/services/chat.py:49,66,80,93,106,115,137,154` | 建议封装事件发射器统一分配 `seq`。 |
| P-M3 | 路由层绕过 `RagService` 直接操作 VectorStore | `app/api/routes.py:139-141`（`service.vector_store.delete_document(...)`） | 删除无校验/无审计/无错误归一化，`RagService` 封装被穿透。应加 `RagService.delete_document()`。 |
| P-M4 | `RagService.__init__` 硬编码 pipeline 各阶段实现，Protocol 抽象无注入点 | `app/services/rag.py:118-123` | 已定义 `DocumentParser/TextCleaner/TextSplitter/EmbeddingProvider` 四个 Protocol 却无法替换。改为构造注入 + 工厂默认。 |
| P-M5 | `Reranker` Protocol 已声明但全仓无实现、无注入、无调用 | `app/services/rag.py:38-39` | 要么实现，要么删除；当前粗召回 `top_k*3`（`:177`）无实际收益。 |
| P-M6 | RAG pipeline 解析/清洗/切分/嵌入/落库串成单一方法；`retrieve()` 又耦合嵌入、检索、预算、截断 | `app/services/rag.py:126-169`、`:171-200` | 抽 pipeline/stage 编排对象。 |
| P-M7 | Token 估算用 `len(text)/4` 启发式，中文场景偏差可达数倍，且被持久化成为预算与计费依据 | `app/services/rag.py:202-203`、`sqlite.py` 持久化字段 | 与 P-H1（预算职责）一并处理：改由 Spring 提供真实计数或使用真实 tokenizer。 |
| P-M8 | `provider_key` 默认值 `"openai-compatible"` 在 3 处硬编码 | `app/schemas/chat.py:24`、`app/schemas/rag.py:29`、`app/api/routes.py:101` | 收敛为常量或配置项。 |
| P-M9 | Provider 工厂为硬编码单点，无注册机制 | `app/main.py:27-34`、`providers/registry.py:5-17`（只有 `get()`/`keys`） | 新增 provider 必须改 `main.py`；补 `register()`。 |

### 低

| 编号 | 问题 | 位置 | 说明与建议 |
| --- | --- | --- | --- |
| P-L1 | `app/core/security.py` 是未被使用的重复鉴权实现（与 `app/api/security.py` 同名函数） | `app/core/security.py:8-19`，全仓引用 0 次 | 删除死代码，避免将来出现分歧实现。 |
| P-L2 | `vectorstores/__init__.py` 未导出生产唯一的 `SQLiteVectorStore` | `app/vectorstores/__init__.py:1-3`（`__all__` 只有 `InMemoryVectorStore`） | 补导出，统一包门面。 |
| P-L3 | 事件契约声明 `citation`/`tool_*` 但 Python 从不产生；`ChatService` 未 import `RagService`，RAG 与 Chat 未接线 | `app/schemas/chat.py:57-68`、`app/services/chat.py` | 要么实现，要么从契约中移除（Spring 侧对应分支同样不可达）。 |
| P-L4 | 流式响应无心跳，`start` 到首个 `delta` 之间空档可能被 Spring `requestTimeout` 误判为 `AI_TIMEOUT` | `app/services/chat.py:44-108` | 补 SSE 注释帧心跳，或调大 Spring 首字节超时。 |

### 高（未修复，需架构决策）

| 编号 | 问题 | 位置 | 为何不在本次修复 |
| --- | --- | --- | --- |
| P-H1 | Token 预算职责落在 Python 侧，与 Spring 双头管理 | `app/services/rag.py:180-197`、`app/schemas/rag.py:28`（`token_budget` 裁剪） | 架构规定预算由 Spring 负责；迁移需同时改两侧契约与预算口径，属跨端改造，需 Owner 授权。建议：Python 只返回候选 chunk 与其 `token_count`，裁剪交给 Spring。 |
| P-H2 | RAG 检索为全表扫描 + Python 侧逐行反序列化与余弦计算，无 LIMIT/ANN 下推 | `app/vectorstores/sqlite.py`（`_search_sync`） | 已解除事件循环阻塞（本次修复），但 O(全库 chunk) 仍在。需要向量索引/ANN 或 SQL 侧预筛，属独立性能优化任务。 |
| P-H3 | `create_app()` 默认构造 `SQLiteVectorStore`，测试未注入时真实写盘 | `app/main.py:65-67` | 已消除「导入即构造」，但 `create_app` 默认行为未改；建议测试统一注入内存 store，或改为惰性代理。 |

---

## 4. 三端契约

### 中

| 编号 | 问题 | 位置 | 说明与建议 |
| --- | --- | --- | --- |
| C-M1 | `MessageStatus` 枚举 Java 多出 `DELETED`，Vue 联合类型缺失 | `conversation/entity/MessageStatus.java:3-10` vs `types/conversation.ts:6-11` | Vue 补 `"DELETED"`；建议以 JSON Schema/代码生成消除手工同步。 |
| C-M2 | `ConversationStatus` 枚举 Java 多出 `DELETED`，Vue 缺失 | `conversation/entity/ConversationStatus.java:3-7` vs `types/conversation.ts:3` | 同上。 |
| C-M3 | Spring `MessageView` 有 `finishReason`，Vue `ChatMessage` 缺 | `conversation/dto/MessageView.java:26` vs `types/conversation.ts:32-52` | 补字段；同时统一大小写（`AiStreamingService` 归一化 Python 小写 `stop` 为 `STOP`，而 SSE 侧 Vue 拿到裸 `string`）。 |
| C-M4 | Vue `AdminMessage` 与后端实际返回的 `MessageView` 字段不匹配，缺 `errorCode/errorMessage/finishReason/modelId/attachments` | `admin/controller/AdminController.java:53` + `conversation/dto/MessageView.java:11-30` vs `types/admin.ts:82-92` | 补字段或复用 `ChatMessage`。 |
| C-M5 | Vue 解析 SSE 时完全忽略 `event:` 名与 `seq` | `api/conversations.ts:166-181` vs `ai/stream/AiStreamingService.java:216`（下发事件名）、`JdkHttpAiGateway.java:137`（内部严格校验 seq） | 浏览器侧丢包/乱序无法检测。建议 Spring 向浏览器下发 `seq`，Vue 校验连续性。 |
| C-M6 | SSE `error` 结构字段不齐：Python→Spring 无 `partial` | `services/chat.py:138-142`（`code/message/retryable`）vs `AiStreamingService.java:148-152`（补 `partial`）vs `types/conversation.ts:87-90` | 统一契约：`partial` 要么两端都有，要么移除。 |
| C-M7 | 角色大小写依赖隐式约定，无契约校验 | `conversation/entity/MessageRole.java:3-8`（大写）vs `app/schemas/chat.py:9-13`（小写），唯一转换点 `ConversationContextBuilder.java:208` | 加测试保护，或在序列化层统一。 |
| C-M8 | Python `ChatRequest` 无 `top_p`，而管理台可配置 `defaultTopP` | `admin/dto/AdminDtos.java:143` vs `app/schemas/chat.py:21-28`（`extra="forbid"`） | 与 C-H2 一并处理：补齐字段或移除配置项。 |

### 低

| 编号 | 问题 | 位置 | 说明与建议 |
| --- | --- | --- | --- |
| C-L1 | API drift：后端存在但 Vue 从未调用 | `ConversationController.java:73,80`、`KnowledgeDocumentController.java:20`、`MemoryController.java:24`、`AuthController.java:81`、`UserController.java:61`、`AdminController.java:37` | 前端补齐入口（重命名会话/删除会话/登出所有设备等），或标注为预留。 |
| C-L2 | API drift：Python `POST /internal/chat` 非流式契约整条是死代码 | `app/api/routes.py:47-64`、`schemas/chat.py:38-52`（`ChatResponse`/`TokenUsage`） | Spring 只暴露 `streamUri()`。要么启用（批量/短任务），要么删除。 |
| C-L3 | `stopAiRequest` 返回类型声明偏窄，漏 `requestId` | `ai/stream/StopStreamResponse.java:3` vs `api/conversations.ts:157` | 补字段类型。 |

### 高/严重（未修复，需跨端改造）

| 编号 | 问题 | 位置 | 为何不在本次修复 |
| --- | --- | --- | --- |
| C-H1 | **严重**：管理台可配 `defaultTemperature` / `defaultTopP` / `defaultMaxOutputTokens`，但 Spring→Python 请求体根本不下发，参数形同虚设 | `ai/gateway/AiGatewayRequest.java:5-8`（无这些字段）、`JdkHttpAiGateway.java:45-50`、`admin/dto/AdminDtos.java:143-144`；Python 侧 `schemas/chat.py:27-28` 已定义 `temperature`/`max_output_tokens` | 需扩展网关请求契约 + 在 `AiStreamingService` 解析模型默认值并注入，同时处理 `top_p` 缺失（Python `extra="forbid"` 会拒绝）。属契约变更，需 Owner 授权后单独实施。 |
| C-H2 | **严重**：`errorCode` 字段混装两套取值域（Java `ErrorCode` 名与 Python 码），泄漏到管理台且 Vue 无标签表 | `knowledge/service/KnowledgeDocumentService.java:69-70` → `admin/dto/AdminDtos.java:107,172` → `types/admin.ts:185,302`；`utils/admin.ts:141-147` 只有状态标签 | 本次只修了「HTTP 响应码映射」，**持久化到文档的 `errorCode` 仍是原始 Python 码**。需统一取值域（建议统一为平台 `ErrorCode` 名 + 保留原始 provider 码到独立字段），并补 Vue 标签表。 |
| C-H3 | **高**：`reasoningContent` 三端断裂——Python 有、Spring 只转发不落库、Vue 有字段但刷新即丢 | `app/providers/types.py:44`、`services/chat.py:68-79`；`AiStreamingService.java:186-187`；`conversation/dto/MessageView.java:11-30` 与 `conversation/entity/`（全目录搜索 `reasoning` 0 命中）；`types/conversation.ts:50` | 需要新增列 + Flyway 迁移（且需同步 H2 测试迁移语法），属数据库变更，不在「安全修改」范围。 |
| C-H4 | **高**：RAG 检索失败被静默吞掉，用户无感知 | `knowledge/service/RagContextService.java:49-55`（`catch (RuntimeException)` 仅日志后返回 `RagContext.empty()`） | 降级本身符合设计（架构文档允许 Memory/RAG 降级），但缺「未命中/降级」提示通道。需设计前端提示契约（例如 SSE 事件携带 degraded 标记）。 |
| C-H5 | **高**：错误码体系二元分裂——REST `ApiResult.code` 是 int，SSE `error.code` 是 String | `common/api/ApiResult.java:10` vs `AiStreamingService.java:136-159` vs `api/client.ts:17`（number）/`types/conversation.ts:87`（string）；`useChatWorkspace.ts:236` 把 String 写进与 REST 同源的 `errorCode` | 统一需前后端同步改造（建议 SSE 也用数值码，或 REST 侧并存字符串 code）。属契约变更。 |

---

## 5. 已验证一致（无需整改，供回归参考）

- SSE usage 字段三端统一：`promptTokens/completionTokens/totalTokens`。
- Spring↔Python 内部鉴权头 `X-Service-Authorization` 双向一致。
- RAG 三端点路径/方法/头逐项对齐（`X-User-Id`/`X-Knowledge-Base-Id`/`X-Document-Id`/`X-Document-Name`/`X-Document-Mime`/`X-Provider-Key`/`X-Embedding-Model`）。
- 命名转换：Python `alias_generator=to_camel` + `populate_by_name` + `by_alias=True`，与 Spring camelCase 互通，无 drift。
- CSRF 守卫范围与 Vue `X-CSRF-Guard` 发送点一致。
- 审计动作枚举 32 值、用户/角色/文件/知识库/记忆/Provider/Model/AI 请求状态枚举，Java 与 Vue 字面量一一对应。
- Python 侧无裸 `except`，provider 原始响应体不外泄（测试显式断言）；VectorStore 抽象干净，`sqlite3` 类型未泄漏到 `base.py` 或上层。

---

## 6. 建议的处理顺序

1. **C-H1 / C-H2**（严重，跨端契约）：与 Owner 确认后单独排期，两个问题都涉及网关/错误码契约。
2. **C-H3**（高，需迁移）：`reasoningContent` 落库，与下一次数据库迁移一并做。
3. **V-H5 / V-H6**（高，分层）：token 单一真相源 + 抽出 `api/auth.ts`，可与 V-M2~V-M5 的重复消除合并为一次「前端 API 层治理」任务。
4. **P-H1**（高，架构）：Token 预算回归 Spring，配合 P-M7 的真实计数。
5. **V-H1~V-H4**（高，巨型页面）：每次改到哪个页面就顺带拆哪个，避免一次性大改。
6. 其余中低项：在相关功能迭代时顺手处理，不单独排期。
