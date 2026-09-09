# AI Chat SaaS 架构决策记录

> 规则：ADR 只追加或标记为 Superseded，不删除历史。  
> 当前状态：阶段 0–10 已同步；Admin/Audit/Usage 仍待后续阶段决策。

## ADR-001：Vue 不直接调用 Python

- 状态：Accepted
- 决策：所有浏览器业务请求经过 Spring Boot，链路固定为 Vue → Spring Boot → Python AI → Provider。
- 原因：统一认证、RBAC、ownership、配额、审计、持久化与密钥边界。
- 后果：Spring 必须提供 AI Gateway/SSE bridge；Python internal API 不暴露公网用户入口。

## ADR-002：Spring Boot 是业务事实源，Python 不管理用户身份

- 状态：Accepted
- 决策：用户、权限、会话、消息、文件元数据、Memory 元数据、usage 和审计由 Spring/MySQL 管理。Python 不解析用户 JWT、不写业务主库。
- 原因：避免双重事实源和跨语言授权漂移。
- 后果：Spring 传递已授权的最小上下文；Python 只返回算法结果与运行状态。

## ADR-003：外部流式协议采用 SSE

- 状态：Accepted
- 决策：聊天生成使用 POST + `text/event-stream`，事件包含 `start/delta/reasoning/usage/done/error`，预留 tool/citation。
- 原因：主要流量为服务器单向增量输出，SSE 易于穿透 HTTP 基础设施且契约清晰。
- 后果：Vue 使用 fetch 流解析而非原生 EventSource；必须单独处理取消、断线、幂等和终止状态。

## ADR-004：保留 Spring MVC，隔离 SSE 桥接

- 状态：Accepted
- 决策：不因 AI 流式请求推翻当前 `spring-boot-starter-webmvc`。外部先采用 `SseEmitter`，内部流客户端藏在 `AiGateway` adapter 后。
- 原因：当前工程已经是 MVC，业务 CRUD 也适合 MVC；保持改动范围可控。
- 后果：必须使用有界执行资源、超时、取消传播和背压策略。WebClient 或其他内部客户端在阶段 3 验证 Spring Boot 4.1.1 兼容性后选择。

## ADR-005：数据库管理 Refresh Token family

- 状态：Accepted
- 决策：Refresh Token rotation 状态落 `refresh_token`，只保存哈希，支持复用检测、设备撤销和全量退出。
- 原因：纯无状态 refresh token 难以安全撤销和检测重放。
- 后果：需要 token family 状态机、清理任务与事务测试；Redis 可做缓存但不是事实源。
- 阶段 2 验证：V2 已建立 `refresh_token`；rotation、reuse 后整族撤销、logout、logout-all 和密码修改撤销均有集成测试。

## ADR-006：文件元数据和权限由 Spring 管理，存储可插拔

- 状态：Accepted
- 决策：Spring 管理 `user_file`、quota、ownership；业务层依赖 StorageProvider。初版允许 LocalStorage，后续可换 MinIO/S3/OSS/COS。
- 原因：把业务权限与物理存储分离。
- 后果：Python 只能通过短时只读引用读取已授权对象；禁止硬编码本地路径。

## ADR-007：聊天附件与知识库文档分离

- 状态：Accepted
- 决策：附件通过 `chat_message_attachment` 引用 `user_file`；知识库通过 `knowledge_document` 独立引用 `user_file`。
- 原因：两者有不同权限、处理流水线和生命周期。
- 后果：上传文件不会自动进入 RAG；从知识库移除也不默认删除云盘源文件。

## ADR-008：聊天记录、短期记忆、长期记忆与 RAG 分离

- 状态：Accepted
- 决策：Message 保存事实对话；Conversation Summary + Recent Messages 构成短期上下文；`user_memory` 保存长期记忆；RAG 使用 KB/Document/Chunk。
- 原因：四类数据来源、召回方式、可编辑性和生命周期不同。
- 后果：Context Builder 按预算选择内容，不能把全部历史/Memory/Chunk 每轮塞入 Prompt。

## ADR-009：Provider 能力采用 Port/Adapter

- 状态：Accepted
- 决策：Python services 只依赖 LLMProvider 等协议，Provider Registry/Factory 集中完成运行时选择。
- 原因：支持 OpenAI、Anthropic、Gemini、DeepSeek、Ollama 和兼容 API，避免业务层厂商条件分支。
- 后果：统一能力模型、finish reason、usage 和错误映射；厂商特殊能力通过 capability 声明暴露。

## ADR-010：关系数据库先选 MySQL，migration 先选 Flyway

- 状态：Accepted（阶段 1）
- 决策：采用 MySQL 8.x + Flyway + Spring Data JPA，不同时引入 MyBatis/JPA 两套栈；Flyway 是 schema 变更唯一入口，Hibernate 固定 `ddl-auto=validate`。
- 原因：目标业务以强关系、事务和 CRUD 为主，Flyway 可显式审计 schema 演进。
- 验证：Spring Boot 4.1.1 使用模块化 `spring-boot-starter-flyway`，V1 migration、JPA 校验及 Repository 集成测试已在 H2 MySQL compatibility mode 通过。
- 后果：生产前仍必须在实际 MySQL 小版本验证 DDL、字符集、排序规则和索引执行计划；禁止 Hibernate 自动更新 schema。

## ADR-011：Redis 只存可重建短时状态

- 状态：Accepted
- 决策：Redis 用于限流、缓存、短时幂等/取消信号和分布式协调；MySQL 保存最终消息、配额、refresh 和审计。
- 原因：防止 Redis 丢失导致业务事实消失。
- 后果：Redis 故障需要清晰降级策略；关键计费和 ownership 不依赖缓存命中。

## ADR-012：外部 ID 与内部主键分离

- 状态：Accepted（阶段 1）
- 决策：内部使用 `BIGINT UNSIGNED`；用户拥有的外部资源使用唯一 ULID `public_id`，Role/Permission 等平台字典资源可直接暴露稳定 `code`。
- 原因：兼顾关系索引效率、日志排序和 API 不可枚举标识。
- 验证：已提供统一 26 字符 Crockford Base32 ULID 生成器，并验证长度、字母表、时间排序和批量唯一性。
- 后果：后续创建业务资源统一复用生成器，并对极小概率唯一冲突做有限重试；不可把不透明 ID 当成授权机制。

## ADR-013：空 Vuex 迁移到 Pinia，Vue CLI 迁移到 Vite

- 状态：Proposed（阶段 1 分两个可回滚任务）
- 决策：由于现有 Store 为空且页面仅为脚手架，建议在业务开发前迁移 Pinia；恢复基线绿灯后再从 Vue CLI/Webpack 迁移 Vite。
- 原因：减少在旧脚手架上累积新业务，使用 Vue 3 主流生态；当前没有业务状态迁移成本。
- 后果：先解决 `vue-router` 类型/锁定问题并建立 typecheck；迁移不得与登录或聊天业务混在一次变更中。

## ADR-014：Markdown 采用安全优先的渲染管线

- 状态：Accepted（具体库待阶段选择）
- 决策：原始 HTML 默认关闭，Markdown 渲染后执行 DOM 消毒、URL scheme 白名单和安全代码高亮；LaTeX 也按不可信输入处理。
- 原因：AI 内容和用户内容均可能携带 XSS payload。
- 后果：不能只依赖 `v-html` 或 Markdown 库默认行为；应建立攻击样例测试。

## ADR-015：VectorStore 暂不选型

- 状态：Deferred to 阶段 5
- 决策：阶段 0 只冻结 VectorStore 接口和 metadata filter 要求，不盲选 pgvector/Qdrant/Milvus/Elasticsearch/Chroma。
- 原因：当前没有文档规模、向量维度、延迟、过滤或部署约束数据。
- 后果：阶段 5 用真实样本做召回质量、过滤正确性、成本和运维基准，再新增 ADR。

## ADR-016：Provider 密钥使用 Secret 引用

- 状态：Accepted
- 决策：`ai_provider` 默认保存 `credential_ref` 和非敏感配置，不保存明文密钥；日志和 API 永不返回密钥。
- 原因：降低数据库、日志、管理接口泄漏半径。
- 后果：部署需要 Secret Manager/workload secret；本地 `.env` 不提交，并提供无真实值的 `.env.example`。

## ADR-017：requestId 与 traceId 分工

- 状态：Accepted
- 决策：`requestId` 标识一次入口请求并对用户可见；`traceId` 遵循 W3C Trace Context 跨服务传播；业务生成另用 `aiRequestId`。
- 原因：HTTP 重试、分布式调用和长生命周期生成不是同一个概念。
- 后果：日志、错误 envelope、SSE 和 AI request log 都需按契约记录对应标识。

## ADR-018：AI quota 使用 Account + 不可变 Ledger

- 状态：Accepted
- 决策：`ai_usage_account` 提供并发余额视图，`ai_usage_ledger` 记录 `RESERVE/SETTLE/RELEASE/ADJUST`，`ai_request_log` 只承担请求事实与诊断，不兼任计费账本。
- 原因：单个累计字段或请求日志无法可靠处理流式预留、真实 usage 结算、超时释放和对账。
- 后果：生成前在同一事务更新 account 并追加 reserve，终态事务追加 settle/release；Ledger 行不可变并以 operation key 幂等，恢复任务处理悬挂 reservation，并定期由 ledger 对账 account。首版 account 周期内使用统一结算币种。

## ADR-019：Spring Boot 4 使用模块化 Flyway Starter

- 状态：Accepted（阶段 1）
- 决策：使用 `spring-boot-starter-flyway` 与 `flyway-mysql`，不只直接依赖 `flyway-core`。
- 原因：Spring Boot 4 将 Flyway 自动配置拆为模块化 starter；只引入 core 时 migration 不会在 JPA schema validate 前自动执行。
- 后果：依赖由 Spring Boot BOM 管理；测试与运行配置都显式启用 Flyway 并固定 migration 路径。

## ADR-020：首批 migration 只创建七张近期核心表

- 状态：Accepted（阶段 1）
- 决策：V1 创建 `app_user`、`app_role`、`app_user_role`、`conversation`、`chat_message`、`ai_provider`、`ai_model`。`permission`、`role_permission`、`refresh_token` 随身份/RBAC 切片创建；File、Memory、RAG、usage、audit 继续延后。
- 原因：这些表是最近的用户、会话和模型配置基础，同时避免为尚未开始的登录、文件、RAG 等业务提前制造空表和 Java 代码。
- 后果：当前角色只能完成用户与角色关联，不能宣称已实现完整 RBAC；登录开发必须先补 migration 和安全设计。

## ADR-021：软删除由显式状态转换与受限查询共同保证

- 状态：Accepted（阶段 1）
- 决策：可删除聚合使用 `status + deleted_at`，实体通过显式 `softDelete()` 同步写入，数据库 CHECK 保证二者一致；资源 Repository 显式使用 owner + `deleted_at IS NULL` 查询，不启用隐式全局 Hibernate filter。
- 原因：显式条件便于审查管理员、恢复和历史查询，也避免全局 filter 在特殊查询中被无意绕过或造成行为不透明。
- 后果：新增 Repository 方法必须审查 ownership 与删除范围；物理删除只允许在单独的数据保留/清理流程中设计。

## ADR-022：浏览器使用内存 Access Token + HttpOnly Refresh Cookie

- 状态：Accepted（阶段 2）
- 决策：Access JWT 只存在 Vuex/API module 内存，不进入 `localStorage`、`sessionStorage` 或普通 Cookie；Refresh Token 只存在 `Secure + HttpOnly + SameSite=Lax` Cookie，页面重载通过 refresh 恢复 session。
- 原因：持久化 JavaScript 可读 Token 会扩大 XSS 后的长期凭据泄漏范围；把 Access Token 放普通 Cookie 又会扩大全部业务 API 的 CSRF 面。
- 后果：Vue 启动必须先执行一次 refresh；Refresh/logout 使用额外 `X-CSRF-Guard` 请求头并默认同源部署。若未来必须跨域部署，需另立 CORS、Origin 校验和 Cookie 属性 ADR。

## ADR-023：Access JWT 使用短时 HS256，授权事实仍实时读取数据库

- 状态：Accepted（阶段 2）
- 决策：当前单一 Spring 发行方/验证方使用环境注入的至少 256-bit HS256 secret、15 分钟 TTL 和 issuer 校验。JWT 只携带 subject 与 `authVersion`，Filter 每次从数据库读取用户状态和角色。
- 原因：当前没有多个独立 Token 验证服务，HS256 的密钥边界简单；实时数据库校验保证 BANNED/DISABLED/DELETED 与角色变更立即生效，而不是等待 JWT 过期。
- 后果：受保护请求多一次用户/角色查询，后续可在不改变事实源的前提下加入短 TTL 可失效缓存。出现多验证服务或外部验签方时，应迁移为非对称签名和 key rotation。

## ADR-024：密码修改通过 authVersion 立即失效旧 Access Token

- 状态：Accepted（阶段 2）
- 决策：`app_user.auth_version` 进入 Access JWT；密码修改在事务内递增版本并撤销全部 ACTIVE Refresh Token，再签发新 session。
- 原因：只比较秒级 JWT `iat` 与毫秒级 `password_changed_at` 存在同秒边界；只撤销 Refresh Token 又会让旧 Access Token 继续可用至过期。
- 后果：Filter 必须比较数据库与 JWT 的版本；普通 logout 只撤销当前 refresh family，Access Token 由前端立即丢弃并在最多 15 分钟后自然过期。

## ADR-025：阶段 2 RBAC 使用固定角色层级，细粒度 Permission 延后

- 状态：Accepted（阶段 2）
- 决策：V2 初始化 `USER`、`ADMIN`、`SUPER_ADMIN`，层级为 `SUPER_ADMIN > ADMIN > USER`；`/api/v1/admin/**` 由 Spring Security 服务端限制为 ADMIN/SUPER_ADMIN。Vue Guard 只镜像角色用于导航体验。
- 原因：当前没有管理员敏感业务动作，提前创建 permission/audit 空模型没有实际授权对象；固定管理员入口已满足本阶段边界。
- 后果：普通用户的 `/admin` 前端导航和 API 均被拒绝。首个管理员管理功能必须同步设计 permission、role_permission 和 audit，不能仅继续扩大角色名判断。

## ADR-026：阶段 2 复用 Vuex 与 fetch，不夹带 Pinia/Vite 迁移

- 状态：Accepted（阶段 2）
- 决策：认证状态复用现有 Vuex 4；普通 JSON API 采用单一 fetch wrapper。锁定 `vue-router@4.1.6` 以恢复 TypeScript 4.5 typecheck；Pinia/Vite 迁移继续作为独立工程任务。
- 原因：本阶段目标是认证闭环，同时迁移构建工具和状态库会扩大回归面。现有 Vuex 原先为空，不存在双 Store 冲突。
- 后果：Access Token 只保存在 Vuex/模块内存；未来迁移 Pinia 时必须保持相同 API client 和 Token 安全语义。

## ADR-027：Ownership 必须进入 Repository 查询并对外统一 404

- 状态：Accepted（阶段 3）
- 决策：Conversation/Message 普通用户 API 只能使用 `publicId + authenticated userId + deletedAtIsNull` 查询；分配消息序号时使用同样 owner-scoped 的悲观锁查询。其他用户资源和不存在资源均返回 `40401`。
- 原因：先按 ID 读取再在 Service 比较 owner 容易在新代码路径漏检，也会通过 403/404 差异泄露资源存在性。数据库复合外键继续作为写入端最后防线。
- 后果：测试逐一覆盖列表、详情、修改、删除、消息列表、消息创建和 parent message 越权。未来管理员跨用户查询必须使用独立 Repository/API，并同步细粒度权限与审计，不能复用普通用户入口绕过 scope。

## ADR-028：阶段 3 沿用页码分页并限制可选排序字段

- 状态：Accepted（阶段 3）
- 决策：复用现有 `PageResult`，Conversation 支持 `page/size/search/status/sort/direction`，排序字段只允许 `LAST_MESSAGE_AT/CREATED_AT/UPDATED_AT/TITLE`；Message 固定按顺序号升序。
- 原因：公共页码基础设施已经落地，当前数据规模和并发尚不足以证明游标复杂度；直接接收任意 JPA 属性作为排序字段也会扩大内部模型暴露面。
- 后果：接口分页从 0 开始，最大 100。未来若压测证明需要游标，应新增明确版本化契约，不能静默改变现有参数和响应语义。

## ADR-029：Mock 回复是阶段适配器，不是未来 AI Gateway

- 状态：Superseded（阶段 5 已删除运行时 Mock）
- 决策：同步 Mock 回复由 `app.chat.reply-mode=mock` 控制的独立 adapter 提供，响应显式携带 `replyMode=MOCK`，Vue 显示 AI offline。真实 LLM 不得通过把该同步 bean 换成网络调用来接入。
- 原因：阶段 3 需要验证消息持久化和 UI，但真实 AI 需要独立的 request log、额度预留、流式事务边界、取消与错误恢复；把远程调用塞入当前短事务会破坏这些边界。
- 后果：进入 Streaming 阶段时保留 Conversation/Message domain，新增正式 `AiGateway`/orchestrator 和 SSE 生命周期，并删除或仅在测试 profile 保留该 Mock adapter。

## ADR-030：Python Provider 使用 async Port/Registry 与 OpenAI-compatible HTTP Adapter

- 状态：Accepted（阶段 4）
- 决策：核心 Chat Service 只依赖 async `LLMProvider`，统一暴露 `chat()`、`stream_chat()`、`embedding()`；Registry 通过稳定 key 选择 adapter。首个实现使用 httpx 直接调用 OpenAI-compatible `/chat/completions` 和 `/embeddings`，不依赖 OpenAI 厂商 SDK。
- 原因：OpenAI-compatible 协议可覆盖 OpenAI、DeepSeek、OpenRouter 与多数本地兼容服务；直接 HTTP adapter 能保持统一错误和流事件，不让 SDK 类型进入核心业务。
- 后果：Anthropic、Gemini、Ollama 原生协议后续新增 adapter 即可，不在 Chat Service 中增加厂商条件分支。Provider 特殊能力必须归一化或通过 capability 扩展，不能泄漏原始响应对象。

## ADR-031：Python internal API 使用私网 + 共享服务令牌的阶段性认证

- 状态：Accepted（阶段 4）
- 决策：`/internal/**` 要求 `X-Service-Authorization`，令牌只来自 `STARDUST_AI_INTERNAL_SERVICE_TOKEN`，以常量时间比较；未配置或不足 32 字符时 internal API fail-closed。`/health` 仅用于探针且服务整体仍不得公网暴露。
- 原因：当前尚无 service mesh/workload identity，轮换共享令牌可以建立最小 Spring→Python 服务身份边界；用户 JWT 不应进入 Python。
- 后果：Spring 后续 AiGateway 必须从 Secret 配置注入该 header，禁止日志记录。生产具备基础设施后迁移 mTLS/workload identity，并将共享令牌标记为 Superseded。

## ADR-032：Provider 连接信息只由 Python Settings 配置

- 状态：Accepted（阶段 4）
- 决策：base URL、API Key、默认模型和 timeout 仅从 `STARDUST_AI_*` 环境变量加载；Chat 请求只传 `providerKey` 和可选 model，不接受 URL、密钥或 credential envelope。API Key 可为空以兼容无需鉴权的本地 OpenAI-compatible 服务。
- 原因：让请求决定目标 URL 会形成 SSRF 面，让密钥跨服务逐请求传递会扩大日志、trace 和异常泄漏范围。
- 后果：新增或轮换 Provider 需要部署配置变更；未来若接入 Secret Manager，在 Settings/adapter 构建层实现，不改变 Chat Service 契约。

## ADR-033：流期间内存聚合，终态短事务一次持久化

- 状态：Accepted（阶段 5）
- 决策：入口短事务写 USER、ASSISTANT placeholder 和 request log；远程生成期间不持有事务、不按 delta 更新数据库；worker 在内存累计 visible content/usage，结束时通过独立事务写入 `COMPLETED/STOPPED/FAILED`，事务提交后才发送 public `done`。
- 原因：逐 token 写库会制造写放大和热点，远程调用持有事务会长期占用连接与锁；先发 done 再提交会向客户端承诺不存在的成功。
- 后果：进程崩溃可能丢失尚未落库的增量，因此启动恢复将遗留活动记录标为 `FAILED/SERVER_RESTART`。需要断点恢复时必须另行设计 checkpoint，而不是静默改变当前语义。

## ADR-034：Spring MVC 使用 AiGateway port、JDK HttpClient 与虚拟线程桥接 SSE

- 状态：Accepted（阶段 5）
- 决策：保持 Spring MVC/SseEmitter；application service 只依赖 `AiGateway`，JDK HttpClient adapter 以 InputStream 增量解析 internal SSE，worker/watchdog 使用 Java 21 虚拟线程执行器。
- 原因：现有项目是 MVC，阶段 5 不需要为单一流端点迁移整套 WebFlux；port 可让未来替换 transport，而不把 HTTP 类型侵入编排和领域状态。
- 后果：adapter 必须校验 event/type/request/seq 并限制单事件大小；Spring request timeout 与 Provider timeout 分层生效。若负载测试证明 MVC bridge 不满足容量，再以 ADR 评估 WebFlux。

## ADR-035：Stop 使用 owner-scoped 单实例注册表并提供终态恢复

- 状态：Accepted with limitation（阶段 5）
- 决策：活动请求注册于 Spring 进程内的并发表，Stop 只允许请求 owner，向 gateway 传播 cancellation 并等待最多 2 秒完成终态；重复 Stop 返回数据库当前状态。浏览器断开和应用关闭也触发取消。
- 原因：这是当前单实例部署下最小、可验证的真实取消闭环；把 Redis 当数据库终态源或仅在前端 Abort 都不能保证消息状态正确。
- 后果：多实例扩容前必须使用粘性路由或可靠的分布式取消信号/worker ownership；当前实现不得宣称跨节点 Stop。启动恢复负责将崩溃遗留活动记录收敛为失败。

## ADR-036：外部扁平 SSE 与内部版本化 SSE 分离

- 状态：Accepted（阶段 5）
- 决策：Python internal SSE 使用 `schemaVersion/type/aiRequestId/requestId/seq/timestamp/payload`；Spring 校验后转换为 Vue 需要的扁平 `type/requestId/conversationId/messageId` 加事件字段。当前实现 `start/delta/reasoning/usage/done/error`，保留 citation/tool 事件名但不提前实现其业务。
- 原因：内部契约需要严格顺序和跨服务诊断，浏览器增量更新更适合稳定的扁平结构；分层避免 Python 获得 conversation ownership 职责。
- 后果：三端 event 类型必须通过契约测试同步演进；未来新增 citation/tool payload 时不得复用现有字段表达不同语义。

## ADR-037：阶段 5 移除同步 Mock 生成入口

- 状态：Accepted（阶段 5）
- 决策：删除阶段 3 的 `/messages` 同步 Mock、reply generator 和 `app.chat.reply-mode`；正式生成只走 SSE orchestrator。测试通过 `@Primary` fake `AiGateway` 隔离外部 Provider。
- 原因：保留可被生产调用的第二套生成路径会绕过 request log、取消、错误收敛和统一状态机。
- 后果：本地无 Provider 时聊天生成明确失败为未配置，而不是伪装成 AI 成功；单元/集成测试仍可稳定、无网络运行。

## ADR-038：编辑与重新生成采用不可变消息变体

- 状态：Accepted（阶段 6）
- 决策：Regenerate 创建相同 sequence 的新 Assistant variant；Edit-and-resend 创建相同 sequence 的 USER variant 及下一 sequence 的 Assistant variant。两者用 `parent_message_id`、`supersedes_message_id`、`variant_no` 表达真实关系，均通过 owner-scoped API 和完整 AI request 生命周期执行。
- 原因：原地覆盖会丢失审计事实，仅在 Vue 替换数组又会产生刷新即消失的假分支。V1 数据模型与索引已能表达不可变修订，无需新增表。
- 后果：历史 API 可返回全部 variants，当前 UI 从最新叶节点沿 parent 链选择活动分支。未来分支切换 UI 可以复用同一事实模型，不得另造浏览器私有树。

## ADR-039：Markdown 采用双重安全边界

- 状态：Accepted（阶段 6）
- 决策：markdown-it 禁用 raw HTML；代码高亮仅使用 highlight.js 的转义输出；KaTeX 使用 `trust=false`；组合后的最终 HTML 统一经 DOMPurify 消毒并禁止脚本、表单、嵌入内容与事件属性。
- 原因：模型输出属于不可信输入，单靠模板插值无法提供 Markdown，单靠 parser 的 HTML 开关也不能覆盖插件及未来渲染扩展的输出。
- 后果：模型输出中的 HTML 标签作为文本展示。新增 Markdown plugin 必须先通过危险协议、事件属性、SVG/MathML 与资源加载回归，不可绕过最终消毒。

## ADR-040：聊天 UI 保持自定义组件并复用现有 Vuex/fetch 基础

- 状态：Accepted（阶段 6）
- 决策：采用石墨侧栏、雾白阅读面与星蓝/紫流式状态的响应式界面；页面拆分到 `components/chat` 与 `useChatWorkspace`，继续复用 Vuex Auth 和统一 fetch/SSE client，不夹带 Vue CLI/Vite 或 Vuex/Pinia 迁移。
- 原因：聊天阅读、长代码、流式状态和移动抽屉需要精确布局；同时迁移构建与状态基础会扩大阶段风险。
- 后果：阶段 7 已用真实云盘选择器替换禁用入口；Vue CLI、TypeScript 和 ESLint 的升级仍是独立任务。一次性 Playwright 冒烟已验证关键交互，正式 CI E2E 仍待建设。

## ADR-041：StorageProvider port + LocalStorage 首版

- 状态：Accepted（阶段 7）
- 决策：文件业务只依赖 `StorageProvider` 的 `put/open/delete` 与 provider key；`StorageService` 通过注册表按 `app.storage.provider` 选取实现。首版 `LocalStorageProvider` 的根目录来自环境配置，object key 仅接受服务端生成的 `user ULID/yyyy/mm/file ULID.ext`，先写临时文件再原子移动。
- 原因：本地存储能完成开发与单机部署，同时 port 保持 MinIO/S3/OSS/COS 的替换边界。把原始文件名拼路径或在 Service 中判断厂商会把安全与业务耦合。
- 后果：数据库只保存 provider/object key，API 不泄漏物理路径。切换对象存储需新增 adapter 与数据迁移方案，不修改文件/聊天业务；当前不宣称支持多节点共享 LocalStorage。

## ADR-042：存储容量使用 reservation 与行锁

- 状态：Accepted（阶段 7）
- 决策：上传前悲观锁定 `user_storage_usage`，校验 `used + reserved + size <= quota` 后增加 reservation；对象成功后短事务转换为 used/file_count，失败则释放。数据库 CHECK 为最终兜底。
- 原因：只在上传结束读取并回写 used bytes 会让并发请求同时通过额度校验；在写大对象期间持有数据库事务又会扩大锁时间和连接占用。
- 后果：容量在上传期间可见为 reserved，明显超配额并发只能有可用额度内的请求成功。必须在生产加固阶段增加悬挂 reservation 恢复与周期对账。

## ADR-043：附件是 user_file 引用，已引用文件首版禁止删除

- 状态：Accepted（阶段 7）
- 决策：聊天上传入口只选择已有 `AVAILABLE user_file`，`chat_message_attachment` 用复合外键约束同 owner；发送/编辑写引用，重新生成沿用 USER parent 引用。存在消息引用时删除返回 409，不复制对象、不级联破坏历史。
- 原因：聊天附件与云盘若各存一份会造成配额、ownership、扫描和生命周期双轨；立即级联删除则会让不可变消息历史出现断链。
- 后果：用户要删除已引用文件需等待未来的“解除引用/保留快照/合规擦除”产品策略。阶段 7 只关联元数据，不把文件内容送给文本 LLM，也不等同于 RAG ingestion。

## ADR-044：短记忆采用 Token Budget 与分支锚点滚动摘要

- 状态：Accepted（阶段 8）
- 决策：Spring 是 Prompt 编排责任方。`ContextBuilder` 按模型 context window 扣除 output/safety reserve，按 System Prompt、可选 Summary、Recent Messages、当前 USER 的顺序构建；recent 既受 Token Budget 也受硬消息数上限约束，禁止全历史读取。`conversation_summary` 以 `covered_through_message_id` 锚定不可变消息分支，滚动生成新版本而不覆盖旧分支所需摘要。
- 原因：固定 N 条无法适应中英文、代码与不同 context window；会话级单一 summary 在 Edit-and-resend 分支下可能混入未发生于当前分支的内容。锚点命中同时提供有界读取与分支正确性。
- 后果：当前 provider-independent `TokenCounter` 必须配 safety reserve；模型精确 tokenizer 可以替换计数实现，但不得移除总预算。摘要同时由消息数阈值或 Token 阈值触发，recent 原文另有 Token 预算与硬消息数上限。摘要作为不可信历史 system 数据包装，成功回答后的摘要失败只告警并降级到 recent context。首版 `ConversationSummarizer` 使用确定性、有界滚动压缩，不新增收费 LLM 调用；未来切换模型摘要仍必须保留超时、降级、owner、锚点和禁止记录完整正文的边界。

## ADR-045：Long Memory 事实与 Prompt 编排由 Spring 管理

- 状态：Accepted（阶段 9）
- 决策：`user_memory`、CRUD、ownership、启停、候选检索和最终 Prompt 组装均由 Spring/MySQL 管理。Python 保持无业务数据库权限，只消费 Spring 发来的有界 messages；未来如引入 embedding/语义提取，Python 仅提供无状态算法 adapter，不能成为 Memory 事实源。
- 原因：用户身份、删除/禁用状态、Conversation/Message 来源和 Prompt 总预算已经由 Spring 掌握；把召回 ownership 放到 Python 会形成第二套权限与数据访问边界。
- 后果：Chat History、Short Memory、Long Memory、RAG 继续拥有独立表、生命周期和预算。Memory 以标记为不可信用户数据的独立 system block 注入，不与 Conversation Summary 合并，也不允许全量装入 Prompt。

## ADR-046：首版自动记忆采用保守规则并失败开放

- 状态：Accepted（阶段 9）
- 决策：首版 `MemoryExtractor` 只接受明确“记住”、稳定偏好、长期项目和长期目标表达，并拒绝凭据、身份及支付敏感模式；Chat 成功持久化后再尝试提取，异常只写脱敏日志。检索先取 owner-scoped enabled 固定上限候选，按 relevance + importance 排序，再受 Top-K 与 Token Budget 限制。
- 原因：每轮额外调用提取模型会增加成本、延迟与隐私面；把每句话保存会迅速污染用户画像。失败开放保证个性化派生能力不能降低主聊天可用性。
- 后果：规则存在漏记和有限语义召回，这是有意的安全优先基线。未来模型提取、embedding、合并/冲突消解必须通过新 ADR，并保留敏感过滤、用户可见/可删/可禁用、owner 过滤、候选上限、Top-K、Token Budget 和 Chat 失败隔离。

## ADR-047：RAG ownership 在 Spring 与 VectorStore 双重收口

- 状态：Accepted（阶段 10）
- 决策：Conversation 只能绑定当前用户的 ACTIVE KB；Spring retrieve 只发送 owner-scoped 绑定，Python VectorStore 再按 `userId + knowledgeBaseId` 命名空间过滤；返回 source 还必须经 Spring MySQL `READY + owner + bound KB` 复核。
- 原因：Python 不拥有业务身份事实，单靠向量 metadata 不能证明文档仍 READY/未删除；单靠 Spring 请求过滤又不足以防 VectorStore adapter 查询遗漏 tenant 条件。
- 后果：跨用户 KB 对普通 API 表现为 404。残留、失败或已删除向量不会进入 Prompt；管理员未来跨用户检索必须设计独立受审计入口。

## ADR-048：首版 VectorStore 使用可替换的持久化 SQLite adapter

- 状态：Accepted with limitation（阶段 10）
- 决策：Python core 只依赖 async `VectorStore` port；首版部署 adapter 以本地 SQLite 持久化向量和 source metadata，并在进程重启后保留索引。路径来自 `STARDUST_AI_RAG_VECTOR_STORE_PATH`。
- 原因：纯内存 adapter 会造成 MySQL 文档仍为 READY、重启后却无法检索的不一致；当前项目的 LocalStorage/单实例基线无需提前引入外部向量服务。
- 后果：SQLite 采用 owner-scoped 候选读取和进程内 cosine 排序，仅适合当前数据量和单实例。横向扩展或基准超过容量前，新增 Qdrant/pgvector 等 adapter、迁移和 READY 对账，不修改 RAG service/业务 API。

## ADR-049：RAG 是独立、有界、失败开放的 Prompt 数据块

- 状态：Accepted（阶段 10）
- 决策：Prompt 固定按 System → Conversation Summary → Long Memory → RAG → Recent Messages → Current User 组装；RAG 有独立 Top-K/Token Budget，片段标为不可信 reference，检索异常返回空上下文而不使主 Chat 必然失败。
- 原因：Chat History、Short Memory、Long Memory 与 RAG 的来源、生命周期和安全语义不同；将文档并入 Memory 或全量装入 Prompt 会破坏用户控制和上下文预算。
- 后果：Current User 仍只出现一次，总 context window 是最终硬边界。Reranker/citation 可在 port/事件契约上扩展，但不能绕过 owner、READY、Top-K 和 Token Budget。

## 待决事项

以下问题不阻塞阶段 0，但必须在相应阶段进入新 ADR：

1. 当前默认 Vue/Spring 同源；若生产改为跨域，需确定精确 allowlist、Origin 校验与 Cookie 策略。
2. Spring Security/JWT 库兼容性，以及 V1 在目标 MySQL 8.x 小版本的验证结果。
3. 管理端 UI 框架仍待选择；用户聊天 Markdown/高亮/LaTeX 已采用 ADR-039 的技术组合。
4. 生产对象存储采用 MinIO/S3/OSS/COS 中哪一种，以及从 LocalStorage 的迁移窗口。
5. VectorStore、Embedding、Parser、Reranker 的实测选型。
6. 数据保留、用户注销匿名化、管理员敏感读取的合规周期。
