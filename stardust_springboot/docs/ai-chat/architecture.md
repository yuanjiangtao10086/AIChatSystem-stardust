# AI Chat SaaS 架构设计

> 状态：阶段 0 基线设计；阶段 2–14 已落地；阶段 14 AI 生成文件（Artifact，见 ADR-065）已落地；阶段 13 统计聚合/共享限流/可观测性已落地；阶段 12 AI Usage/限流/对账已落地  
> 盘点日期：2026-09-07  
> 适用目录：`stardust_vue/`、`stardust_springboot/`、`stardust_ai/`

## 1. 文档边界

本文区分三种状态：

- **当前事实**：已经存在于仓库并经过文件检查的内容。
- **目标决策**：后续阶段应遵守的边界和契约。
- **候选实现**：需要在对应阶段通过兼容性验证后再引入，不代表已经安装。

后续 Owner 指令采用了新的阶段编号：认证/RBAC 为阶段 2，Conversation/Message 非流式基础为阶段 3。实现进度以 `progress.md` 和 Owner 最新指令为准，本文早期路线编号只保留为历史基线。

阶段 0 当时未实现业务、未增加运行时依赖、未创建数据库表；后续各阶段的落地事实以下文和 `progress.md` 为准。

## 2. 当前真实技术栈

### 2.1 Vue

| 项目 | 当前事实 |
| --- | --- |
| Vue | `package.json` 声明 `^3.2.13`；锁文件/已安装为 `3.5.42` |
| 构建工具 | Vue CLI `5.0.9` + Webpack；**没有 Vite** |
| TypeScript | `4.5.5`，`strict: true`；已有独立 `typecheck` script |
| Router | Vue Router `4.1.6`；聊天路由为 `/chat`、`/chat/:conversationId` |
| 状态管理 | Vuex `4.1.0`；认证状态复用 Vuex，聊天页面状态封装在 composable；**没有 Pinia** |
| UI 框架 | 无 |
| HTTP Client | 统一 fetch API 层；普通 JSON 与 POST SSE 共享内存 Access Token/refresh-once 处理 |
| Markdown | markdown-it 14.1.0 + DOMPurify 3.2.6 + highlight.js 11.11.1 + KaTeX 0.16.22 |
| CSS | 全局/组件内 SCSS；`sass` + `sass-loader` |
| lint | ESLint 7 + Vue 3 essential + TypeScript + Prettier；`npm run lint` |
| build | `vue-cli-service build` |
| 页面 | 登录、注册、Profile、聊天、Files、Memory、Knowledge 与 `/admin` 权限占位页 |
| 登录/Guard | Vuex Auth Store、内存 Access Token、HttpOnly Refresh Cookie、401 refresh、Router Guard |
| Chat UI | 响应式 Sidebar、会话搜索/历史、模型选择、安全 Markdown、fetch SSE、Stop、Regenerate、Edit-and-resend |

可复用内容：Vue 3 工程、严格 TypeScript 配置、Router 基础、路径别名 `@/*`、SCSS 和 lint 配置。Home/About/HelloWorld 与空 Vuex Store 没有业务复用价值。

### 2.2 Spring Boot

| 项目 | 当前事实 |
| --- | --- |
| Java | `pom.xml` 指定 Java 21；验证环境为 Java `21.0.9` |
| Spring Boot | `4.1.1` |
| 构建 | Maven；系统 Maven `3.9.4` 可用 |
| Web | `spring-boot-starter-webmvc` |
| ORM/MySQL/Redis | Spring Data JPA + MySQL；测试为 H2 MySQL mode；Redis 经 Spring Data Redis + Lettuce 引入，仅作可切换的共享限流计数（非事实源；默认 `memory` 内存实现，可选 `redis` 适配器） |
| Security/JWT | Spring Security、短时 JWT、数据库 Refresh Token rotation/revoke |
| Result/Exception | 统一 `ApiResult`、`ErrorCode`、`BusinessException` 和全局异常处理 |
| 分层代码 | 已有 auth/user/conversation/AI gateway/streaming 的 entity/repository/service/controller/DTO |
| migration | Flyway V1–V8；Hibernate `ddl-auto=validate` |
| 日志 | requestId/MDC、AI 生命周期结构化字段与敏感信息禁记规范 |
| 测试 | migration、认证、ownership、SSE 生命周期和真实 internal SSE parser 集成测试 |
| AI Bridge | `AiGateway` port + JDK HttpClient streaming adapter + `SseEmitter` |

可复用内容：Java 21、Spring Boot 4.1.1、MVC 启动骨架和上下文测试。Windows Maven Wrapper 的 `.mvn/wrapper/maven-wrapper.properties` 缺失，当前 `mvnw.cmd` 不可用。

### 2.3 Python AI

| 项目 | 当前事实 |
| --- | --- |
| Python 约束 | `>=3.12,<3.15`；当前 `.venv` 使用 Python `3.14.2` 验证 |
| 包管理 | PEP 621 `pyproject.toml` + Poetry Core；运行时/测试顶层依赖精确锁版，可信 `poetry.lock` 待在可访问官方索引的环境生成 |
| Web/配置 | FastAPI 0.141.1、Uvicorn 0.52.4、Pydantic 2.13.5、pydantic-settings 2.15.0 |
| HTTP/Provider | httpx 0.28.1；async `LLMProvider`、Registry、OpenAI-compatible adapter |
| 路由/Service | `/health`、`/internal/chat`、`/internal/chat/stream`；`ChatService` 负责协议编排 |
| LLM/Embedding/Streaming | OpenAI-compatible chat、SSE stream、embedding 与阶段 10 RAG pipeline 已实现 |
| 安全/可观测性 | 环境变量配置、内部服务令牌、requestId、结构化错误映射和脱敏日志 |
| 测试/lint | pytest 9.1.1、pytest-asyncio 1.4.0、Ruff 0.16.6；阶段 10 已覆盖 RAG pipeline/API/ownership/持久化 |

结论：阶段 0 扫描确认没有遗留 AI 实现需要迁移；阶段 4 在原 PEP 621/Poetry 骨架上完成最小可扩展服务，阶段 10 又沿同一 port/adapter 边界增加 RAG。Provider、Chat Service 与 RAG Service 保持解耦，Python 仍不访问业务数据库。

## 3. 主要技术债与优先级

| 优先级 | 技术债 | 影响 | 建议处理阶段 |
| --- | --- | --- | --- |
| Resolved | Vue 的 TS7016/router 版本漂移 | 阶段 2 已锁定兼容版本并建立 typecheck | 已完成 |
| P0 | Spring Maven Wrapper 元数据缺失 | 新环境无法复现构建 | 阶段 1 基线治理 |
| P0 | 三端均无 `.env.example`/配置分层/Secret 约定 | 易产生密钥泄漏和环境漂移 | 阶段 1 |
| P1 | Vue CLI、TS 4.5、ESLint 7 较旧，依赖范围导致声明版与安装版漂移 | 升级和兼容风险 | 阶段 1 以独立迁移任务处理 |
| Resolved | Vue 尚无 Markdown 安全渲染 | 阶段 6 已完成解析、高亮、LaTeX 与最终 DOM 消毒 | 已完成 |
| P1 | Vue 尚无仓库内长期浏览器 E2E 基础 | 阶段 6 使用一次性 Playwright/Edge 冒烟验证，尚未形成 CI 套件 | 测试基础设施阶段 |
| P1 | Spring 当前取消注册表仅限单实例内存 | 多实例部署时 Stop 不能跨节点路由 | 扩容前引入粘性路由或分布式取消信号 |
| Resolved | AI usage 只有 request fact log，无 quota/ledger | 阶段 12 已建立 account + 不可变 ledger、reserve/settle/release、管理员 ADJUST 与周期对账 | 已完成 |
| Resolved | 限流已实现并抽为可插拔 `RateLimiter`（memory 默认 + 可选 Redis 适配器），注册/刷新/上传/登录均接入；X-Forwarded-For 仅信任白名单内可信代理 | 阶段 13 已落地，见 ADR-056 | 已完成 |
| P1 | 对账任务全表扫描 `ai_usage_account` | 用户量增长后单次扫描成本上升 | 数据量验证后改为分片/游标扫描 |
| P2 | 根目录不是 Git 仓库，只有 `stardust_vue` 是独立 Git 仓库 | 跨三端变更难以统一审计 | 由项目 Owner 决定仓库策略 |
| P2 | Spring Boot 4.1.1 与未来第三方库的兼容性尚未验证 | ORM/JWT/文档库选择可能受限 | 每次引入依赖前验证；阶段 0 不降级 |

## 4. 目标总体架构

```text
Browser / Vue 3
  ├─ 用户端与 /admin
  ├─ REST JSON、multipart、fetch SSE
  └─ 只信任 Spring Boot 暴露的资源与权限结果
          │ HTTPS
          ▼
Spring Boot（系统唯一业务入口与事实源）
  ├─ Identity / RBAC / Admin / Audit
  ├─ Conversation / Message / File / Quota / User Memory
  ├─ Provider & Model 元数据 / AI Usage
  ├─ ContextBuilder / Short Summary / bounded Memory Retrieval
  ├─ ownership、限流、幂等、事务、持久化
  └─ AI Orchestrator + SSE Bridge
          │ 内网认证 + JSON/SSE + traceparent
          ▼
Python AI（无用户身份事实、无业务主库写权限）
  ├─ Chat Service（消费 Spring 提供的有界 messages）
  ├─ LLM & Embedding Provider Adapter
  ├─ Parse / Chunk / Retrieve / Rerank
  └─ 可替换的未来语义算法 adapter（不拥有 Memory 事实）
          │ Provider-specific SDK/API
          ▼
LLM / Embedding / Vector Store / Parser
```

禁止 Vue 直接调用 Python。MySQL 是业务事实源；Redis 只承担缓存、限流、短时幂等/取消信号等可重建状态，不能成为用户、消息、配额或审计的唯一存储。

### 4.1 推荐实现栈（已落地项与后续目标）

| 层 | 推荐方向 | 说明 |
| --- | --- | --- |
| Vue | Vue 3 + Vite + TypeScript + Vue Router + Pinia | 当前 Vue CLI/Vuex 仅为脚手架且 Store 为空，适合在业务前做独立迁移 |
| UI/CSS | Element Plus（管理端/表单基础）+ SCSS + CSS Variables | 聊天主界面保持自定义组件和 design tokens，避免页面被组件库结构绑死 |
| HTTP/SSE | 统一 fetch wrapper + fetch stream（POST SSE） | 阶段 5 已落地；两者共享内存 Access Token 与一次 refresh 重试 |
| Markdown | markdown-it + DOMPurify + highlight.js + KaTeX | 阶段 6 已落地；原始 HTML 关闭，最终 HTML 再消毒 |
| Spring | Java 21 + Spring Boot 4.1.1 + Maven + MVC | SSE 使用 `SseEmitter`，internal stream 使用 JDK HttpClient；核心依赖 `AiGateway` port |
| Persistence | Spring Data JPA + MySQL 8.x + Flyway | 当前无 ORM，可选一套栈；先做 Boot 4.1.1 兼容性验证 |
| Security | Spring Security + Nimbus JOSE/JWT 能力 + DB refresh family | Access JWT 短时有效；Refresh rotation/revocation 有状态管理 |
| Cache/Observability | Redis + Actuator/Micrometer + OpenTelemetry/W3C Trace Context | Redis 非事实源；指标、日志和 trace 统一关联 |
| Python | Python 3.12 作为部署基线 + FastAPI/Uvicorn + Pydantic Settings + httpx | 阶段 4 已落地并在 3.14.2 验证；生产镜像仍应显式锁定 Python 小版本 |
| Python quality | pytest + pytest-asyncio + Ruff；mypy 后续按收益引入 | pytest/Ruff 已落地，不为尚未实现的模块创建空测试 |

表中 Python 阶段 4 条目已经落地，其余未实施部分仍是推荐方向。若兼容性 spike 失败，必须通过新 ADR 调整，不得悄然引入第二套 ORM、迁移工具或 HTTP 封装。

## 5. 三端职责边界

### 5.1 Vue

- 渲染用户端和管理员端；维护页面级/会话级 UI 状态。
- 调用 Spring Boot REST、上传和 SSE；不持有 Provider 密钥。
- 对 Markdown 使用白名单渲染与 HTML 消毒；默认禁用原始 HTML。
- 客户端路由守卫只改善体验，不作为权限边界。
- 建议在阶段 1 将**空 Vuex Store**有边界地迁移为 Pinia；建议迁移 Vue CLI 到 Vite，但必须作为可回滚的基线任务并先恢复绿灯构建。
- 目标目录按现有 `src` 渐进增加：`api/`、`types/`、`stores/`、`composables/`、`layouts/`、`components/chat/`、`views/admin/`；页面只负责布局和组合。

### 5.2 Spring Boot

- 唯一外部业务 API；负责认证、Refresh Token rotation、RBAC、ownership 和管理员授权。
- 管理 Conversation、Message、File、Knowledge Base、Memory 元数据、配额、Provider/Model 配置与审计。
- 在调用 Python 前完成用户状态、资源归属、额度和幂等检查。
- 在流式开始前事务性保存 USER 消息并创建 `STREAMING` ASSISTANT 消息；结束、停止或失败时持久化最终状态和已生成内容。
- 采用按业务能力分包，包内再分 `controller/service/repository/entity/dto/vo/converter`；避免全局 God Controller/Service。
- 当前保持 Spring MVC。目标 SSE 桥接可使用 `SseEmitter`，内部 Python 流客户端封装在 AI infrastructure adapter 中；是否引入 WebClient需在实现阶段做 Boot 4 兼容性验证。

### 5.3 Python AI

- 接收 Spring Boot 已授权、最小化的数据；不解析用户 JWT，不直接决定 quota/ownership/RBAC。
- 不直接写业务 MySQL，不把聊天记录当作自己的事实源。
- 通过接口隔离 `LLMProvider`、`EmbeddingProvider`、`VectorStore`、`StorageProvider`、`DocumentParser`、`TextSplitter`、`Reranker`、`MemoryRetriever`、`MemoryExtractor`、`TokenCounter`。
- 推荐渐进结构：

```text
app/
  api/             # internal API/router
  core/            # settings, errors, tracing, security
  schemas/         # API models; 与 Spring DTO 分离
  services/
    chat/
    rag/
    document/
    embedding/
    memory/
  providers/       # adapter + registry/factory
  repositories/    # vector/object access abstractions only
  utils/
tests/
```

Provider 选择集中在 Registry/Factory，不允许 `if model == ...` 散落业务层。

## 6. 业务能力分解

| 能力 | Spring Boot 责任 | Python 责任 | Vue 责任 |
| --- | --- | --- | --- |
| 用户系统 | 注册、登录、密码、状态、Token rotation | 无 | 表单、会话态、错误展示 |
| RBAC | Role/Permission、方法与资源权限 | 无 | 菜单/路由可见性（非安全边界） |
| Conversation | 标题、归属、归档/删除、检索 | 上下文输入消费者 | 列表、搜索、编辑 |
| Message | 顺序、分支/重生成、状态、持久化 | 内容生成 | 消息树/列表与交互 |
| Streaming | 鉴权、编排、SSE 转发、落库、取消 | Provider 流、统一事件 | fetch SSE、增量渲染、停止 |
| File/Cloud Disk | 元数据、权限、配额、StorageProvider | 受控读取/解析 | 上传与管理 UI |
| Short Memory | Token 预算、分支祖先裁剪、summary 版本/锚点与 Prompt 组装 | 只消费 Spring 提供的有界 messages | 无需展示；继续显示消息事实 |
| Long Memory | CRUD、归属、启停、保守提取、有界检索、Prompt 注入 | 当前只消费已组装 Prompt；未来可提供无状态语义提取/embedding adapter | `/memories` 管理、搜索、启停 UI |
| RAG | KB/Document 归属、任务状态 | parse/chunk/embed/retrieve/rerank | KB 与引用 UI |
| Provider/Model | 管理元数据、启停、授权、密钥引用 | Adapter 执行与能力归一化 | 用户选模型、管理员配置 |
| Admin | 管理 API、敏感读取审计 | 无独立管理权限 | `/admin` 页面 |
| Audit | 追加式记录管理员敏感行为 | 仅返回运行诊断 | 展示审计数据 |
| AI Usage | 预留额度、结算 token/cost/latency、不可变 ledger、管理员 ADJUST、周期对账 | 返回 usage | 用户用量面板、管理员额度字段与调整入口 |

聊天记录、短期记忆、长期记忆、RAG 是四类独立数据和生命周期，不能混为一个消息表或每轮全部注入 Prompt。

阶段 7 已落地 File/Cloud Disk 的首个纵向切片：Vue `/files` 与聊天附件选择器只调用 Spring；Spring 以 MySQL 保存 metadata/ownership/quota，以 `StorageProvider` port 管理对象，首个 adapter 为 LocalStorage。聊天消息只保存到 `chat_message_attachment` 的引用，不复制对象。Python 尚不读取附件，未来多模态或 RAG 必须通过 Spring 授权的单对象、短时只读能力取得内容，不能让 Vue 直连 Python 或暴露本地路径。

## 7. Streaming 生命周期

1. Vue 使用 `Idempotency-Key` 发起 `POST /api/v1/conversations/{id}/messages:stream`。
2. Spring Security 实时拒绝非 `NORMAL` 用户；应用层以 owner-scoped lock 校验 conversation、parent message 和启用的 CHAT model/provider，并在同一短事务按「上下文估算 + 输出预留」预留额度，不足时返回 `42902 AI_QUOTA_EXCEEDED` 并回滚，不进入 SSE。
3. Spring 在短事务中保存已完成的 USER 消息、`PENDING` ASSISTANT placeholder 与 `PENDING` `ai_request_log`，随后释放数据库事务。
4. Spring `ContextBuilder` 分别召回有界 Long Memory 与当前 Conversation 已绑定知识库的有界 RAG sources；按模型 context window 扣除输出和安全预留，组装 System Prompt + Short Summary + Long Memory + RAG + Recent Messages + 当前 USER。每类派生上下文独立，遍历有硬上限且当前消息只出现一次；Memory/RAG 失败均可降级。
5. Python Provider Adapter 发出版本化 `start/delta/reasoning/usage/done/error`；Spring 校验 `aiRequestId`、类型和严格递增 `seq`，再转换为外部 SSE。
6. Spring 只在内存累计 visible content 和 usage，不为每个 delta 写数据库。
7. 正常结束先在独立终态事务写入完整 Assistant 内容/token、`COMPLETED` request log、`conversation.last_message_at` 和 `SETTLE` 用量结算；随后以独立、可降级步骤滚动刷新分支摘要并对 USER 消息做保守长期记忆提取，最后发送 `done`。摘要或提取失败只记录脱敏告警，不回滚已完成回答。
8. 用户 Stop、浏览器断开、Spring watchdog 超时、Python/Provider 错误均传播取消并保存 partial content，终态为 `STOPPED` 或 `FAILED`；应用启动时把遗留 `PENDING/STREAMING` 标为 `FAILED/SERVER_RESTART`。
9. `Idempotency-Key` 以 `(user_id, client_request_id)` 唯一约束阻止重复创建；当前重复提交返回 `IDEMPOTENCY_CONFLICT`，尚不提供 SSE 重放。

阶段 5 暂不实现 quota reservation/ledger、增量 checkpoint 或跨节点取消。这些属于后续 Usage/生产加固能力，不能把当前内存注册表误认为分布式保证。

## 8. 安全与可观测性基线

- 所有用户资源查询都带 `user_id` ownership scope，不能只按资源 ID 查询；当前模型不宣称支持 organization/tenant。管理员跨用户读取需要显式权限并写 `admin_audit_log`。
- Access Token 短时有效；Refresh Token 只保存哈希，按 family rotation，复用检测后撤销整族。Token 不放 URL。
- 登录失败按客户端地址与邮箱双维度计数并冷却；客户端地址由 `ClientIpResolver` 解析，默认只取直连地址，仅当直连 peer 命中 `app.security.rate-limit.trusted-proxies`（精确地址或 IPv4 CIDR）时才取 `X-Forwarded-For` 最左跳（ADR-052 延续）；阶段 13 起限流抽为可插拔 `RateLimiter`（见 ADR-056）。限流状态默认在 JVM 堆内（`memory` 实现），`store=redis` 时跨实例共享。
- 上传校验大小、扩展名、声明/实际 MIME、magic/text 有效性和文件名规范化；Storage key 由服务端生成并在 provider 根目录内规范化，禁止原始路径拼接。阶段 7 已实现这些基线；病毒扫描/CDR 属于生产加固，不得把 magic 校验误称为恶意内容扫描。
- 管理员跨用户文件管理（阶段 11C）只能以文件 `publicId` 寻址，物理对象一律经 `StorageService` 打开/删除；响应永不返回 `object_key` 与服务器绝对路径，打开/删除前再拒绝含 `..`、以 `/` 开头或含反斜杠的 object key；查看/下载/删除分别写入 `VIEW_USER_FILE`/`FILE_DOWNLOAD`/`FILE_DELETE` 审计，且受 `requireCanView` 约束（见 ADR-059）。
- 管理员知识库 / RAG 管理（阶段 11D）遵守三层边界：Vue 只调 `/api/v1/admin/**`，需要 AI 处理的动作（重新处理、删除向量）由 Spring 经 `RagGateway` 调用 Python，**Vue 永不直连 Python**；查看/重试/删除文档/删除向量分别写入 `VIEW_KNOWLEDGE_BASE`/`RAG_DOCUMENT_RETRY`/`RAG_DOCUMENT_DELETE`/`RAG_VECTOR_REMOVE` 审计，并受 `requireCanView` 约束（见 ADR-060）。
- 管理员 AI 服务商 / 模型目录（阶段 11E）的密钥**只写不读**：库中只存 `credential_ref` 引用，响应只返回 `hasApiKey` 与**运行时**解析出的 `maskedApiKey`；写入只接受 `env:NAME`/`vault:…` 引用，裸 Key 返回 `40001`；审计元数据只记布尔与非敏感字段，不含引用与密钥（见 ADR-061）。该目录是平台级资源，不归属用户，因此不适用 ADR-058 的所有者限制。
- 管理端总览、审计日志与 AI 调用日志（阶段 11F）**全部只读**：总览是既有表的实时聚合，不建物化表；审计与调用日志只有分页与筛选，没有写入/修改/删除入口，审计行只能由业务 Service 产生（见 ADR-062）。日志类接口不适用 `requireCanView` 的所有者限制。
- Markdown 默认不允许 raw HTML；渲染后消毒，链接协议白名单，代码高亮输出也需视为不可信内容。
- Provider API Key、JWT secret、数据库/Redis密码只来自环境变量或 Secret Manager；数据库只存 `secret_ref` 或加密密文，不记录明文。
- 管理员可配置的 Provider `base_url` 必须使用 HTTPS allowlist、规范化 URL、DNS/IP 重绑定防护和网络 egress policy，禁止访问 loopback、link-local、metadata 与内网管理地址。
- 使用 W3C Trace Context；日志允许 `request_id/user_id/conversation_id/provider/model/latency/token/status/error_code`，禁止记录密码、Token、Cookie、Authorization、API Key 和默认完整 Prompt。
- 阶段 13 引入 Spring Boot Actuator + Micrometer，暴露 `/actuator/prometheus`（仅 `ADMIN`/`SUPER_ADMIN`，`health`/`info` 公开）；对账漂移与限流命中从纯日志升级为可采集指标（见 ADR-057），告警规则由运维侧 Grafana/Prometheus 配置，后端只产指标不内置告警。
- AI 审计和 usage 记录采用独立保留策略；管理员审计追加写，不允许普通 CRUD 覆盖历史。
- 用户注销/删除时，业务行、对象、向量、缓存、日志和备份按数据保留政策分别处理；Prompt/Memory/RAG 内容不得因删除业务行而成为无人清理的孤儿数据。

## 9. 部署与依赖方向

- Vue 仅依赖 Spring 公共契约。
- Spring domain/application 不依赖 Python SDK；通过 `AiGateway` port 调用 infrastructure adapter。
- Python services 依赖 Provider/VectorStore 等协议，不依赖具体 SDK。
- 初期可单机部署 MySQL + Redis + LocalStorage + Python SQLite VectorStore；StorageProvider/VectorStore 均保持可替换。SQLite 只作为单实例持久化 adapter，扩容前需以基准和运维约束选定共享向量设施。
- Python internal API 只在私网暴露；阶段 4 使用环境注入的 `X-Service-Authorization` 共享服务令牌并做常量时间比较，缺失或短于 32 字符时 fail-closed。生产仍建议升级为 mTLS 或 workload identity。

## 10. 阶段边界

- Owner 阶段 0：盘点与设计。
- Owner 阶段 1：数据库与 Spring 公共基础。
- Owner 阶段 2：认证、用户与 RBAC。
- Owner 阶段 3：Conversation/Message 基础。
- Owner 阶段 4：Python AI Service 基础。
- Owner 阶段 5：Vue → Spring → Python → LLM 完整 SSE Streaming（当前已完成）。
- Owner 阶段 6：现代 GPT 风格用户聊天 UI、安全 Markdown 与真实消息分支交互（当前已完成）。
- Owner 阶段 7：统一 `user_file`、用户云盘、LocalStorage adapter、容量 reservation 与聊天附件引用（当前已完成）。
- Owner 阶段 8：Conversation Short Memory、Token Budget 与滚动分支摘要（当前已完成）。
- Owner 阶段 9：Long-Term Memory CRUD、启停、保守提取、有界检索与 ContextBuilder 注入（当前已完成）。
- Owner 阶段 10：Knowledge Base、Document pipeline、VectorStore、RAG Context 与用户 UI（当前已完成）。
- Owner 阶段 11：管理员后台与审计（`admin_audit_log`、用户/会话/文件/知识库/Provider/Model/AI 请求管理、敏感读取审计与 `/admin` UI，当前已完成）。11A 用户管理、11B 聊天记录管理、11C 文件与云盘管理（列表多维筛选、详情与引用统计、受审计下载/删除、不暴露内部路径）、11D 知识库与 RAG 管理（知识库详情与统计、文档状态/分块/失败原因、重新处理、删除文档与向量，AI 操作经 Spring 中转 Python）、11E AI 服务商与模型管理（Provider/Model CRUD、启停、结构化能力与参数、同类型唯一默认模型、同服务商内排序、密钥只写不读与掩码、用户侧目录下发 `defaultModel`）、11F 总览 / 审计日志 / AI 请求日志（实时只读聚合与 24 小时逐时趋势、审计与调用日志的筛选分页、单条调用详情）均已落地，见 ADR-058、ADR-059、ADR-060、ADR-061、ADR-062。
- Owner 阶段 12：AI 用量与额度。第一批（用量账户、ledger、reserve/settle/release 与 `/api/v1/usage`）、第二批（用户用量 UI、管理员额度字段与 `usage:adjust` 审计）、登录限流与周期对账均已落地；后续为按模型/Provider 的统计聚合、共享限流器与运维加固，必须由 Owner 另行授权并重新编号。
- Owner 阶段 13：统计聚合（`GET /api/v1/usage/breakdown` 与 admin 全站聚合，实时来自 `ai_request_log`，按日/模型/Provider）、共享限流器（`RateLimiter` 接口 + `InMemoryRateLimiter` 默认 + `RedisRateLimiter` 可选适配器 + `X-Forwarded-For` 可信代理白名单，覆盖注册/刷新/上传/登录）、可观测性（Actuator/Prometheus 指标：限流命中与对账漂移），均已落地（见 ADR-056、ADR-057）。
- Owner 阶段 14：AI 回答生成文件与下载（Artifact）。LLM 经 `create_artifact` function tool 触发；Python 生成文件（文本类直接包装，Office 类由 python-docx/python-pptx/openpyxl 生成，禁用宏）并经 `artifact_start/delta/done/error` SSE 回传；Spring `MessageArtifactService` 在 `artifact_done` 二次校验（重新 sniff 字节、禁止伪造 MIME/扩展名、<=10MB）→ 储备配额 → `StorageService` 落盘 → 建 `user_file`(AVAILABLE) → 建 `chat_message_attachment`(type=OUTPUT)；下载复用 `GET /api/v1/files/{id}/download`（owner 校验）。不支持 tool 的 Provider 自动降级为无 artifact（见 ADR-065）。

每个阶段都必须拆成小任务，遵循阅读、修改、编译、测试、修复、记录；不得因本文已设计未来能力而提前创建全部表或空壳模块。
