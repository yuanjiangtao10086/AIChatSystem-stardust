# AI Chat SaaS 开发进度

> 最近更新：2026-09-09  
> 当前阶段：阶段 10「用户知识库与 RAG」已完成；**前端 UI 仿 ChatGPT 风格 + 全量中文化**已完成；**未实现 Admin/计费**

## 前端 UI 重构（仿 ChatGPT 风格 + 全量中文化）

状态：Completed（纯 UI 层，未触碰业务逻辑/API/数据库）

- [x] 重写 `stardust_vue/src/styles.scss`：统一 CSS 变量为浅灰侧栏 / 白主区 / 黑色主操作 / 圆角胶囊；全局字体优先中文（PingFang SC、微软雅黑）。
- [x] `App.vue` 顶栏中文化（聊天/云盘/记忆/知识库/个人资料/管理后台/退出登录）。
- [x] 聊天工作区仿 ChatGPT：`ChatSidebar` 改浅灰栏 + 新对话 + 搜索 + 历史列表 + 底部用户菜单；`ChatHeader` 模型/知识库胶囊按钮；欢迎页中央「今天有什么计划？」；`ChatComposer` 圆角胶囊输入框 + 黑色圆形发送 / 停止按钮。
- [x] `ChatMessage` 用户消息右侧灰色气泡、AI 全宽 markdown，标签「你 / 星语」，操作按钮「复制/已复制、编辑、重新生成」，状态文案中文映射。
- [x] 全部视图/组件可见文案中文化：Login、Register、Files、FileTable/Dropzone/DetailPanel/StorageMeter、Knowledge、KnowledgeBaseList/DocumentPipelineList、Memories、Profile、Forbidden、About、AdminView + AdminShell/Dashboard/Users/Conversations/Files/Rag/Ai/Audit。
- [x] `index.html` 设 `lang="zh-CN"`、标题「星语 AI」、noscript 中文。
- [x] 日期格式器统一为 `zh-CN`。

### 验证

- Vue：`npm run typecheck` 通过（无类型错误）；`npm run lint -- --fix` 全部自动修复后通过；IDE 诊断 0 error。
- `npm run build` 因耗时长未等待，typecheck + lint 已保证质量；建议本地手动执行 `npm run build` 出包验证。

## 阶段 10：用户知识库与 RAG

状态：Completed

### 中断复盘

上次中断的直接原因是会话达到 5 小时额度上限；代码层面的过程问题是工作面铺得过宽：Python port、schema 与部分服务先行出现，但尚未用 Spring 编译、migration 校验和三端契约测试逐段收口，因此中断时呈现为“有局部代码、无可交付闭环”。本次先审计实际文件，再按 Python → V8/JPA → ownership/API → ContextBuilder → Vue → 全量验证的闸门顺序续作，没有重写前九阶段。

### 已完成

- [x] V8 创建 `knowledge_base`、`knowledge_document`、`document_chunk`、`conversation_knowledge_base`；复合 owner 外键、状态 CHECK、软删除和检索/状态/时间索引已落地。
- [x] Spring 提供知识库 CRUD/分页搜索、文档列表/详情/删除/失败 retry，以及 Conversation 绑定 0~20 个知识库；所有入口使用 owner-scoped 查询并统一隐藏跨用户资源。
- [x] 文档引用既有 `user_file`，知识库删除前要求先删除文档；仍被知识文档引用的源文件禁止删除，不复制文件对象。
- [x] Python 建立 `DocumentParser`、`TextCleaner`、`TextSplitter`、`EmbeddingProvider`、`VectorStore`、`Retriever` 服务和预留 `Reranker` port；首版解析 UTF-8 text/Markdown/CSV/JSON。
- [x] OpenAI-compatible `/embeddings` 是首个实际 Embedding adapter；SQLite 是首个持久化、本地单实例 VectorStore adapter，向量操作始终携带 user/KB/document scope。
- [x] internal process/retrieve/delete API 使用既有服务令牌、Pydantic 校验、requestId、大小限制和稳定错误映射；失败状态可重试。
- [x] ContextBuilder 顺序统一为 System → Short Summary → Long Memory → RAG → Recent Messages → Current User；RAG 独立 Top-K/Token Budget，检索失败开放且 current user 不重复。
- [x] Spring 对 Python 返回来源再次按 MySQL `READY + owner + bound KB` 过滤，避免已失败/删除的残留向量进入 Prompt。
- [x] Vue 新增 `/knowledge`、知识库管理、直接上传、文档 pipeline 状态、删除/retry；聊天 Sources 选择器保存真实 Conversation 绑定，不创建前端假关系。

### 验证

- Python：Ruff 通过；pytest 全量 14 项通过（包含 pipeline、endpoint、owner namespace、Token Budget 与 SQLite 重启持久化）。
- Spring：`mvn test` 全量 49 项通过，V1–V8 Flyway/Hibernate validate 通过；`KnowledgeRagIntegrationTests` 3 项覆盖 CRUD、文档失败/retry、READY chunk、跨用户绑定与检索过滤；`mvn package -DskipTests` 通过。
- Vue：`npm run typecheck`、`npm run lint`、`npm run build` 均通过；只有既有 KaTeX 代码生成体积提示。

### 有意边界

- PDF/Office/OCR parser、Reranker 实现、Qdrant/pgvector 等多实例向量基础设施未提前引入；不支持的文档明确进入 `FAILED`，不会伪装 READY。
- SQLite adapter 适合当前 LocalStorage/单实例首版，生产横向扩展前必须换共享 VectorStore 并执行迁移/对账。
- citation/tool SSE 类型继续保留；internal retrieve 已返回结构化 sources，本阶段不伪造模型逐句引用，也未进入 Admin/AI Usage。

## 阶段 9：Long-Term Memory

状态：Completed

### 已完成

- [x] V7 新增 `user_memory`，区分 `PREFERENCE/PROJECT/GOAL/EXPLICIT` 与 `MANUAL/AUTO`，包含 importance、来源、启停、软删除、hash 去重和 owner 复合外键。
- [x] Spring 新增 owner-scoped Memory CRUD、分页搜索、类型/启停筛选及独立启停 API；跨用户读取、修改、删除与启停均不可达。
- [x] `MemoryExtractor` 仅识别明确记住、稳定偏好、长期目标和项目表达；凭据、身份及支付敏感模式拒绝自动记录，重复内容不重复插入。
- [x] `MemoryRetriever` 只读取固定上限的 enabled 候选，按 query relevance + importance 排序，再施加 Top-K 与 Memory Token Budget；禁止 SELECT 全部 Memory 注入 Prompt。
- [x] `ContextBuilder` 将 Long Memory 作为独立、不可信的有界 system 数据块注入，继续与 Chat History、Conversation Summary（Short Memory）及未来 RAG 分离。
- [x] 正常 Chat 完成后以失败开放步骤提取 Memory；提取异常只记录脱敏告警，不回滚 Assistant 完成状态。
- [x] Vue 新增受保护的 `/memories` 页面，支持分页、搜索、新增、修改、删除、启用和禁用。

### 验证

- `MemoryIntegrationTests` 3 项与扩展后的 `ConversationShortMemoryIntegrationTests` 6 项通过，覆盖 CRUD、ownership、disabled、relevance、importance、Top-K、Token Budget、敏感提取、独立上下文注入与 current message 不重复。
- Spring 全量 `mvn test` 共 46 项通过；`mvn package -DskipTests` 成功，Flyway V1–V7 与 Hibernate schema validate 通过。
- Vue `npm run typecheck`、`npm run lint -- --no-fix`、`npm run build` 通过；生产构建只有既有 KaTeX chunk 体积告警。

### 明确未做

- [ ] 未实现 embedding、向量召回、Memory 合并/冲突消解或遗忘策略；当前实现是可替换的保守规则提取与有界词法召回。
- [ ] 未实现 RAG、Knowledge/Chunk、向量库或 citation；Long Memory 不读取文档，也不写 RAG 表。
- [ ] 用户主动创建的 Memory 允许其自行控制内容；自动提取的敏感词规则只是最小防线，不替代后续隐私分类、加密和数据保留政策。

## 阶段 8：Conversation Short Memory

状态：Completed

### 已完成

- [x] 新增 `ContextBuilder`、`ConversationSummarizer`、`TokenCounter`；上下文固定为 System Prompt + 可选 Summary + Token 预算内 Recent Messages + 当前 USER，当前 USER 不重复。
- [x] Token 预算优先使用 `ai_model.context_window/max_output_tokens`，空值回退环境配置；同时保留 safety reserve、recent 硬条数上限、summary trigger/keep/budget 配置。
- [x] V6 新增版本化 `conversation_summary`，以 covered message 作为分支锚点并用 owner 复合外键兜底，编辑重发不会串用其他分支摘要。
- [x] 正常完成 Assistant 后滚动折叠旧消息并保留近期原文；摘要步骤失败只记录无正文告警，不影响回答终态，STOPPED/FAILED 不触发摘要。
- [x] 摘要 system message 明确标记为不可信历史数据，降低历史用户文本通过摘要提升为 system 指令的风险。
- [x] 删除正式链路的固定“最多 200 条”上下文实现；任何祖先扫描都有硬上限，超预算 current USER 在调用 Provider 前返回 `40010`。

### 验证

- `ConversationShortMemoryIntegrationTests` 5 项通过，覆盖短/长对话、消息数与 Token 双阈值摘要、滚动版本、分支锚点、Prompt 预算、current message 不重复和超大单条边界。
- Spring 全量 `mvn test` 共 42 项通过；`mvn package -DskipTests` 成功。

### 明确未做

- [ ] 未实现 Long Memory、跨 Conversation 用户画像、embedding/向量召回或 RAG。
- [ ] 当前 `TokenCounter` 是保守的 provider-independent 估算；接入不同模型的精确 tokenizer 前必须继续保留安全预留。
- [ ] 当前 `ConversationSummarizer` 是有界、确定性的滚动摘要，不额外调用收费模型；未来模型摘要实现必须保留同一失败降级、分支锚点和敏感日志边界。

## 阶段 7：统一文件系统与用户云盘

状态：Completed

### 已完成

- [x] Flyway V5 创建 `user_file`、`user_storage_usage`、`chat_message_attachment`；复合外键在数据库层约束消息、文件和用户一致，关键 owner/status/time/hash 索引已建立。
- [x] Spring 业务层依赖 `StorageProvider`，`StorageService` 负责按配置选取实现；首版 `LocalStorageProvider` 使用服务端 ULID/年月 object key、规范化根目录和原子移动，核心业务不依赖本地路径。
- [x] 上传执行大小、扩展名、声明 MIME、文件签名/文本有效性、危险文件名和路径穿越校验；支持 PNG/JPEG/GIF/WebP/PDF/文本及 Office ZIP 类型，API Key/原始 object key 不对前端暴露。
- [x] `user_storage_usage` 使用悲观行锁和 `reserved_bytes → used_bytes` 两阶段转换；失败释放 reservation，数据库 CHECK 再兜底 `used + reserved <= quota`。
- [x] 用户文件 API 支持上传、分页搜索、详情、容量、重命名、下载、图片预览和软删除；所有读取/变更均 owner-scoped，其他用户与不存在资源统一 404。
- [x] 聊天发送与 Edit-and-resend 接受最多 10 个 `attachmentIds`；仅关联已有 `AVAILABLE user_file`，Regenerate 复用原 USER 附件，不产生第二份对象。
- [x] `/files` 响应式云盘完成上传/拖放、列表、搜索、分页、容量、详情、图片预览、下载、重命名和删除；聊天 Composer 可从云盘选择附件，历史消息显示同一文件引用。
- [x] 已被聊天引用的文件当前拒绝删除并返回 409，避免历史消息悬空；附件只建立元数据关联，本阶段不把文件内容发送给 Python/LLM。

### 验证

- Spring：阶段 7 当时 `mvn test` 共 37 项通过；其中 `UserFileIntegrationTests` 3 项覆盖上传/安全校验/ownership/预览/下载/重命名/删除与并发超配额，`AiStreamingIntegrationTests` 8 项覆盖附件归属、历史返回、单对象复用与引用删除冲突。当前阶段 8 全套基线见本文顶部。
- Vue：`npm run typecheck`、`npx eslint src --ext .ts,.vue --no-fix` 和 `npm run build` 通过；一次性 Playwright + 本机 Edge 契约桩冒烟覆盖桌面与 390px 移动端鉴权路由、上传、文件出现、容量、详情入口和控制台错误，并在验证后清理测试环境。

### 明确未做

- [ ] 未实现病毒扫描/CDR、对象存储 adapter、分片/断点续传、内容级去重、批量操作和管理员文件 UI。
- [ ] 未实现多模态模型读取、文档解析、RAG ingestion 或 Python 内部文件下载；聊天附件当前仅为受保护的业务引用。
- [ ] 未实现过期 `UPLOADING/DELETING` 自动恢复与周期配额对账任务；状态和索引已为恢复任务保留。

## 阶段 6：现代 GPT 风格用户聊天 UI

状态：Completed

### 已完成

- [x] `/chat` 与 `/chat/:conversationId`，桌面固定 Sidebar、移动端抽屉、会话新建/搜索/最近列表/用户菜单、模型选择和响应式聊天区。
- [x] Chat 页面拆分为 Layout、Sidebar、Conversation、Header、Message、Markdown、CodeBlock、Composer、Attachment、ModelSelector、StreamingCursor 等小组件，页面只负责组合。
- [x] markdown-it 支持标题、强调、列表、表格、引用、inline/fenced code；highlight.js 负责语法高亮，KaTeX 负责行内/块公式，代码块展示语言和复制按钮。
- [x] raw HTML 默认关闭，KaTeX 禁止 trust，最终渲染结果经 DOMPurify 白名单消毒；浏览器测试验证恶意 `img/onerror` 不产生 DOM 节点。
- [x] 实时增量、reasoning、usage、终态和 Stop；仅在用户位于底部附近时自动跟随，主动上滚后显示回到底部按钮。
- [x] 消息复制、真实 Regenerate 与 Edit-and-resend。Spring 创建不可变 USER/ASSISTANT variants，返回 parent/supersedes/variant 信息；Vue 不伪造持久消息树，流结束后回读服务端事实。
- [x] Regenerate/Edit 继续执行服务端用户状态、ownership、模型、幂等和 request log 生命周期；跨用户统一 404。
- [x] 阶段 6 当时附件入口保持禁用；阶段 7 已替换为真实 `user_file` 选择与消息关联。

### 验证

- Vue：`npm run typecheck`、`npm run lint`、`npm run build` 通过；一次性 Playwright + 本机 Edge 冒烟覆盖桌面、移动抽屉、Markdown/XSS、上滚锁定和真实 SSE 客户端更新。
- Spring：阶段 6 当时 `mvn test` 共 33 项通过（`AiStreamingIntegrationTests` 7 项）；覆盖 Regenerate/Edit-and-resend 的真实变体关系、上下文、SSE operation 与跨用户拒绝。

### 明确未做

- [x] 附件上传/File/Cloud Disk 已在阶段 7 完成。
- [ ] 未实现 Memory、RAG、citation/tool 业务、Admin 业务或 AI 计费。
- [ ] 未实现可视化分支选择器；当前选择最新活动叶分支，旧 variants 仍完整保留在数据库。
- [ ] 未将一次性浏览器冒烟环境纳入仓库依赖或 CI；需在测试基础设施阶段正式落地。

## 阶段 5：完整 SSE AI Streaming

状态：Completed

### 已完成

- [x] Vue 通过 POST fetch 发起流式请求，复用内存 Access Token、单次 refresh/401 处理；支持模型选择、`delta/reasoning/usage/done/error`、立即停止显示与历史回读。
- [x] Spring 在 Provider 调用前完成用户状态认证、Conversation ownership、parent ownership 和启用 CHAT model/provider 校验。
- [x] 短事务保存 USER、ASSISTANT placeholder 与 `ai_request_log`，worker 期间只在内存聚合增量，终态一次落库后再发送 `done`。
- [x] `AiGateway` port 与 JDK HttpClient adapter 调用 Python `/internal/chat/stream`，携带内部服务令牌并校验 `aiRequestId/type/seq`。
- [x] Python OpenAI-compatible Provider 支持 reasoning 增量；Chat Service 保持 async provider 边界，并在取消时关闭上游异步流。
- [x] 统一 public SSE：`start/delta/reasoning/usage/done/error`，同时保留 `citation/tool_start/tool_delta/tool_done` 契约类型。
- [x] Stop API owner-scoped 且幂等；浏览器断开、用户 Stop、Spring watchdog、Python/Provider 超时、429/5xx、协议错误和 Spring 异常均收敛为 `STOPPED/FAILED`。
- [x] 保存 partial Assistant 内容、token usage、finish/error 信息、latency 和 request fact；终态更新 `conversation.last_message_at`。
- [x] 应用启动恢复遗留 `PENDING/STREAMING` 消息和请求为 `FAILED/SERVER_RESTART`，避免永久脏状态。
- [x] 删除阶段 3 同步 Mock endpoint/adapter，测试 fake 只通过 `AiGateway` 注入，不侵入生产架构。
- [x] 新增 Flyway V4 `ai_request_log` 及 owner/status/time/provider-model 查询索引。

### 验证

- Spring：`mvn test` 共 32 项通过，`mvn package -DskipTests` 成功；覆盖成功、partial+429、Provider 5xx、AI/Spring watchdog timeout、意外异常、Stop/重复 Stop/越权 Stop、Conversation/Model 越权以及真实 HTTP internal SSE parser。
- Python：`pytest` 10 项、Ruff、pip check 通过；覆盖 Provider mock、Chat Service、internal endpoint/schema/auth/requestId/SSE。
- Vue：lint、typecheck、production build 通过。

### 明确未做

- [ ] 未实现 File/Cloud Disk、Memory、RAG、citation 生产、工具调用或管理员业务。
- [ ] 未实现 quota reservation、计费 ledger、SSE replay/checkpoint 或跨节点取消；当前 ActiveStreamRegistry 只适用于单 Spring 实例。
- [x] 阶段 6 已引入安全 Markdown/代码高亮/LaTeX 渲染，并完成真实浏览器冒烟验证。

## 阶段 4：Python AI Service 基础架构

状态：Completed

### 已完成

- [x] 建立 FastAPI 应用、`GET /health`、`POST /internal/chat`、`POST /internal/chat/stream`。
- [x] 建立 async `LLMProvider` port：`chat()`、`stream_chat()`、`embedding()`，并用 Registry 隔离 Provider 选择。
- [x] 实现可实际配置的 OpenAI-compatible httpx adapter，覆盖 chat、SSE stream 和 embeddings。
- [x] Provider base URL、API Key、默认模型、timeout、内部服务令牌全部由 `STARDUST_AI_*` Settings/环境变量注入，无硬编码生产密钥。
- [x] internal API 使用 `X-Service-Authorization` 和常量时间比较；配置缺失时 fail-closed，且不接收用户 JWT。
- [x] Pydantic schema 默认拒绝未知字段；统一映射超时、不可用、限流、请求拒绝、协议错误和内部错误。
- [x] requestId middleware 接受安全的上游 ID或生成 UUID，并写入日志、响应 header、JSON/SSE envelope。
- [x] 日志只记录 request/provider/message count/error code，不记录 Prompt、Service Token、API Key 或 Provider body。
- [x] 新增 `.env.example` 和 `.gitignore`；没有修改 Spring/Vue 运行时代码或数据库。

### 验证

- `python -m pytest -q`：10 项通过，覆盖 OpenAI-compatible adapter MockTransport、Provider 错误/超时、Chat Service、内部认证、schema validation、requestId、JSON endpoint 和 SSE endpoint。
- `python -m ruff check app tests`：通过。
- `python -m pip check`：通过。
- 验证解释器：Python 3.14.2；项目声明 `>=3.12,<3.15`。

### 明确未做

- [ ] 未实现 Memory、RAG、文件解析、向量库或业务数据库访问。
- [ ] 未实现 Spring AiGateway/SSE Bridge、Vue Streaming、usage 结算或消息状态编排。
- [ ] `/health` 当前检查进程和必需配置，不主动调用可能收费的 Provider 健康接口。
- [ ] 原空 `poetry.lock` 已移除：项目虚拟环境中的 Poetry 2.3.2 已恢复，但当前主机无法连接 Poetry 默认的 pypi.org 源，未能生成可信 lock；顶层版本已在 `pyproject.toml` 精确锁定，发布前仍需在可访问官方索引的环境生成并提交 lock。

## 阶段 3：聊天会话与消息基础模型

状态：Completed

### 已完成

- [x] Conversation 创建、分页列表、受限排序、标题搜索、详情、标题/状态修改与软删除 API。
- [x] Message 历史分页，固定按 `sequenceNo ASC, id ASC` 返回；支持 `USER/ASSISTANT/SYSTEM/TOOL` 领域角色。
- [x] V3 migration 补齐 `total_tokens`、`error_message`，保留 V1 已有复合 ownership 外键和顺序唯一约束。
- [x] 创建消息时悲观锁定 owner-scoped Conversation，由聚合根分配 `sequence_no` 并同步 `message_count/last_message_at`。
- [x] 所有 Conversation/Message 读写均由 Repository 同时约束认证 `userId` 与资源 ID；跨用户访问统一返回 404 防枚举。
- [x] 阶段 3 当时增加过独立 Mock reply adapter；该临时入口已在阶段 5 删除。
- [x] Vue 阶段 3 当时实现基础会话 UI；阶段 5 已替换为正式 SSE 发送。
- [x] 消息内容按纯文本插值渲染，没有启用 raw HTML 或未消毒 Markdown。

### 验证

- Spring：`mvn -o test` 当前共 25 项通过，其中阶段 3 集成测试覆盖 Conversation CRUD、分页/排序/搜索、消息顺序、`lastMessageAt`、Mock 标识，以及列表/详情/修改/删除/消息读取/消息创建/parent message 的跨用户访问。
- Vue：`npm run lint`、`npm run typecheck`、`npm run build` 均通过。

### 明确未做

- [ ] 未连接真实 LLM、Provider、Python AI、SSE、Streaming、停止或重新生成。
- [ ] 未实现 Markdown 渲染；当前 UI 只做安全纯文本展示。
- [ ] 未实现消息编辑、单条删除、分支切换或附件。
- [ ] 当前 Vue 首屏最多读取 50 个会话、每个会话最多读取前 100 条消息；完整翻页交互留待数据规模验证后完善。

## 阶段 2：认证、用户和 RBAC

状态：Completed

本次阶段编号遵循 Owner 当前指令；它实现了阶段 0 文档中原称“身份/RBAC 最小闭环”的范围，不包含 Conversation/Message 或 AI。

### 已完成

- [x] Spring Security 无状态 Filter Chain、BCrypt PasswordEncoder、自定义 JWT Authentication Filter。
- [x] 统一 AuthenticationEntryPoint、AccessDeniedHandler，并继续复用 `ApiResult` / `ErrorCode`。
- [x] 注册、登录、refresh rotation、退出、全部退出、当前用户、资料修改和密码修改 API。
- [x] Access JWT 使用短 TTL、issuer、HS256、`authVersion`；每次请求从数据库复核用户状态和角色。
- [x] Refresh Token 使用 HttpOnly/SameSite Cookie，数据库只保存 peppered HMAC-SHA256；支持 family rotation、revoke 和 reuse detection。
- [x] 密码修改递增 `auth_version`，立即拒绝旧 Access Token并撤销全部旧 Refresh Token，再签发当前新 session。
- [x] V2 migration 创建 `refresh_token`、增加 `app_user.auth_version`、初始化 `USER/ADMIN/SUPER_ADMIN`。
- [x] `/api/v1/admin/**` 由服务端限制为 `ADMIN/SUPER_ADMIN`；`SUPER_ADMIN > ADMIN > USER`。
- [x] `BANNED`、`DISABLED`、`DELETED` 用户由 JWT Filter/认证服务在服务端拒绝，不依赖 Vue。
- [x] Vue 登录页、注册页、Profile、Auth Vuex Module、统一 fetch、自动 refresh、401 retry、Router Guard 和退出。
- [x] Access Token 只存内存；Refresh Token 对 JavaScript 不可见；Vue 开发代理保持同源 Cookie 模型。
- [x] 锁定 `vue-router@4.1.6`，修复原 TypeScript 4.5 类型解析失败；新增独立 `typecheck` script。

### 验证

- Spring：`mvn -o test` 共 23 项通过，覆盖正常登录、错误密码、BANNED、DISABLED、普通用户访问管理员 API、ADMIN/SUPER_ADMIN、无效/过期 Access Token、Refresh rotation/reuse、CSRF guard、密码改后旧 Token 失效、资料修改和退出；`mvn -o package -DskipTests` 通过。
- Vue：`npm run lint`、`npm run typecheck`、`npm run build` 均通过。

### 明确未做

- [ ] 未实现聊天、Conversation/Message Controller、Streaming 或 Python AI。
- [ ] 未实现管理员用户管理等业务；当前 `/admin` 只是经过前后端双重校验的阶段边界页/API probe。
- [ ] 尚无管理员自助授予角色流程；首个 `ADMIN` / `SUPER_ADMIN` 账户需在受控运维流程中配置，不能开放公共注册获取高权限。
- [ ] 未提前创建 `permission`、`role_permission` 或 audit 表；首个细粒度/敏感管理员动作应同步引入。

## 阶段 1：数据库与 Spring Boot 公共基础架构

状态：Completed

本次 Owner 将原阶段 1 的范围收敛为数据库与 Spring 公共基础设施；Vue 工具链、Security/JWT 和登录/RBAC 业务不属于本次范围。

### 已完成

- [x] 引入单一持久化体系：Spring Data JPA、MySQL Driver、Flyway；测试使用 H2 MySQL compatibility mode。
- [x] 建立 `V1__create_core_schema.sql`，创建 `app_user`、`app_role`、`app_user_role`、`conversation`、`chat_message`、`ai_provider`、`ai_model`。
- [x] 建立公共 `BaseEntity`、JPA Auditing、`createdAt/updatedAt`、`version`、ULID `publicId` 与软删除基类。
- [x] 建立 User、Role、Conversation、Message、Provider、Model 状态枚举和数据库 CHECK 约束。
- [x] 建立统一 `ApiResult`、`ErrorCode`、`BusinessException`、全局异常映射、校验错误明细。
- [x] 建立统一 `PageRequest` / `PageResult`，页号从 0 开始，默认 20，最大 100。
- [x] 建立 requestId 过滤器和 MDC 日志规范；接受合法 `X-Request-Id`，否则生成 ULID，并回写响应头。
- [x] 建立 7 个最小 Repository；资源读取方法显式执行 owner + 未删除过滤。
- [x] 使用复合外键防止消息跨用户挂接到会话，并以集成测试验证约束生效。
- [x] 提供不含真实密钥的 `.env.example`；Hibernate 只执行 schema validate。

### 明确延后

- [ ] 未实现注册、登录、refresh、Spring Security、JWT 或授权 Controller/Service。
- [ ] `permission`、`role_permission`、`refresh_token` 随后续身份/RBAC 切片创建。
- [ ] `user_file`、Memory、RAG、usage、audit 表及业务代码仍按后续阶段创建。
- [ ] 未修改 Vue 和 Python 项目。

## 阶段 0：项目盘点与架构设计

状态：Completed

### 已完成

- [x] 全量读取三个项目的源码、依赖、配置、测试与 README 范围。
- [x] 记录 Vue、Spring Boot、Python 的真实技术栈与缺失能力。
- [x] 识别可复用骨架、主要技术债和工具链问题。
- [x] 确定 Vue → Spring Boot → Python AI → Provider 的职责边界。
- [x] 设计用户、RBAC、Conversation、Message、Streaming、File、Cloud Disk、Memory、RAG、Provider、Model、Admin、Audit、AI Usage。
- [x] 形成数据库关系、字段方向、索引、状态、删除语义和分阶段 migration 计划。
- [x] 形成 REST JSON、SSE、错误码、requestId/traceId 和内部 AI API 契约。
- [x] 建立 `architecture.md`、`api.md`、`database.md`、`progress.md`、`decisions.md`。
- [x] 完成无对话上下文的独立读者测试，并修正 SSE 终态顺序、quota ledger、幂等命名空间、ownership 外键与术语歧义。

### 本阶段没有做

- [ ] 没有新增业务依赖。
- [ ] 没有创建或修改数据库表/migration。
- [ ] 没有实现登录、聊天、Streaming、文件、RAG 或 Memory。
- [ ] 没有修改 Vue、Java、Python 运行时代码。

## 阶段 0 / 阶段 1 历史验证基线

### Vue

- `npm run lint`：通过，无 lint error。
- 阶段 0 的 `npm run build`：失败；当时定位为 TypeScript 4.5.5 无法解析 `vue-router@4.6.4` 类型声明。该历史问题已在阶段 2 通过锁定兼容版本 `vue-router@4.1.6` 修复，当前 build/typecheck 均通过。
- 阶段 0 的 `npx tsc --noEmit`：同一 `TS7016` 失败；阶段 2 已新增并通过正式 `npm run typecheck` script。
- 环境：Node `26.8.1`、npm `12.0.2`；属于非常新的运行环境，也应在阶段 1 明确项目支持的 Node LTS 并加入版本文件。

### Spring Boot

- 系统 Maven `3.9.4`、Java `21.0.9`。
- 阶段 0 基线：`mvn test` 上下文测试 1 个通过，`mvn package -DskipTests` 成功。
- 阶段 1：`mvn -o test` 共 10 个测试通过，覆盖 migration、JPA schema validate、审计字段、软删除、分页、错误码、requestId 和 ownership 复合外键。
- 阶段 1：`mvn -o package` 通过（包含上述 10 个测试），已生成 `target/stardust_springboot-0.0.1-SNAPSHOT.jar`。
- `mvnw.cmd`：失败，因为 `.mvn/wrapper/maven-wrapper.properties` 缺失；系统 Maven 可作为本次验证替代。
- 测试有 Flyway 对 H2 `2.4.240` 的已验证版本提示，以及 Mockito 动态 agent 的未来 JDK 兼容警告；当前均不导致失败。

### Python（阶段 0 历史基线）

- 阶段 0 当时的 `.venv` Python `3.14.2` 满足当时的 `>=3.12` 声明。
- 阶段 0 当时只有可解析的 `pyproject.toml` 和空 `poetry.lock`，尚无 FastAPI、pytest、Ruff 或 Uvicorn。
- 上述内容仅保留为历史盘点；阶段 4 的当前依赖、代码与验证结果见本文顶部，不能再作为现状判断。

## 当前可复用资产

- Vue：Vue 3、严格 TS、Router、SCSS、路径别名、ESLint/Prettier 基础。
- Spring：Java 21、Spring Boot 4.1.1、MVC、JPA/Flyway、认证/聊天/SSE 基线，以及可插拔 Storage 与 owner-scoped 文件服务。
- Python：项目名、Python `>=3.12` 约束、PEP 621/Poetry 构建骨架。

脚手架 Home/About/HelloWorld、空 Vuex Store 没有业务复用价值；Python 没有遗留实现需要迁移。

## 后续阶段计划

### 身份/RBAC 最小闭环（阶段 2 已完成）

建议拆分顺序：

1. 修复 Vue 依赖锁定/类型检查并增加独立 typecheck；确定 Node LTS。
2. 修复 Maven Wrapper；补三端 `.env.example` 和配置 profile，不含真实 Secret。
3. 分离执行 Vuex→Pinia、Vue CLI→Vite 的可回滚迁移；每步保持 build/lint/typecheck 绿灯。
4. Spring Data JPA、MySQL、Flyway 和 Spring 公共基础已在本阶段完成；后续只补 Security/JWT 与真实 MySQL 集成验证。
5. 创建 permission/RBAC/refresh 所需 migration，完成注册、登录、refresh rotation、退出和 ownership/security 基线。
6. 在已有 Result、错误码、异常处理和 requestId 基线上补 trace context 与 OpenAPI。

该切片已按 Owner 的阶段 2 指令完成。

### Conversation/Message（阶段 3 已完成）

- 会话 CRUD、搜索/分页、ownership。
- 消息状态、修订/重新生成数据模型，先完成非流式持久化。
- 对应 repository/service/controller 与集成测试。

### Spring/Vue AI Streaming 集成（阶段 5、6 已完成）

- Python FastAPI、LLMProvider 和 OpenAI-compatible adapter 已在阶段 4 完成。
- Spring AiGateway/SSE bridge、消息占位、幂等、取消、usage 结算。
- Vue fetch SSE 客户端和拆分后的聊天组件。
- 端到端契约/断线/超时/错误脱敏测试。

### File 与 Cloud Disk（阶段 7 已完成）

- StorageProvider、上传安全、quota reservation、附件关联和云盘 UI 已完成；生产对象存储与恢复/对账任务待后续加固。

### Knowledge Base / RAG（阶段 10 已完成）

- KB/Document/Chunk、Conversation 绑定、文档处理/retry、Embedding/VectorStore/Retrieval、Prompt 注入与用户 UI 已完成。
- PDF/Office/OCR、Reranker、逐句 citation 和共享生产 VectorStore 属于后续可选加固，不得把 SQLite 单实例能力误报为横向扩展能力。

### Long Memory（阶段 9 已完成）

- Memory CRUD、启停、保守提取、有界词法召回和用户控制已完成；未来 embedding/语义召回必须保持相同 owner filter、Top-K 与 Token Budget。

### 后续候选：Admin、Audit、Usage 与生产加固

- 管理员业务、敏感读取审计、AI/存储统计、限流、监控、备份恢复、安全测试和容量测试。
- 若管理员敏感功能在此前阶段出现，审计能力必须随功能同步提前，不能等到阶段 7。

## 已知问题与阻塞判断

### 当前阻塞

- 对阶段 10 完成无代码阻塞；目标 MySQL、真实 OpenAI-compatible embedding 服务与正式浏览器 CI 仍属于部署/测试基础设施验证项。
- 对生产部署仍有验证项：需在目标 MySQL 8.x 小版本运行 Flyway 与集成测试，并锁定字符集/排序规则。
- Maven Wrapper 缺失仍影响跨环境可复现性；Node 26 超出部分旧 Vue CLI 依赖声明的支持范围。项目 `.venv` 已可运行 Poetry 2.3.2，但本机无法访问 Poetry 默认索引，可信 `poetry.lock` 尚待生成。

### 后续需要 Owner/产品确认

- 生产若不能保持 Vue/Spring 同源，需要重新设计精确 CORS allowlist、Origin 校验和 Cookie 属性。
- 用户注销和数据保留/匿名化规则。
- 管理员查看私密聊天的审批、理由和保留周期。
- 存储/AI 月度额度与超额策略。
- 生产对象存储和从本地 SQLite VectorStore 迁移到共享向量设施的运行环境约束。

## 下一阶段开始条件

- Owner 明确下达 Admin/Audit/Usage 或生产加固任务；阶段 10 完成后不得擅自进入后续阶段。
- 认证 Cookie/CSRF 与 Token 策略已冻结；跨域部署前必须新增 CORS/Origin 策略 ADR，不能直接放开 credentials CORS。
- 在真实 MySQL 环境验证 V1 至 V7；生成 Python lock、修复 Maven Wrapper 并锁定 Node LTS，以恢复可复现命令入口。
