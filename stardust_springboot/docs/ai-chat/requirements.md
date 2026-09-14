# 星语 AI Chat SaaS 需求规格说明书（SRS）

> 文档编号：SRS-STARDUST-AICHAT-001
> 版本：v1.0（首版需求基线）
> 编制日期：2026-09-14
> 编制方式：**基于仓库现有代码与既有文档的逆向梳理**，非凭空设计
> 适用目录：`stardust_vue/`、`stardust_springboot/`、`stardust_ai/`
> 关联文档：`architecture.md`、`api.md`、`database.md`、`progress.md`、`decisions.md`

---

## 0. 文档说明

### 0.1 编制依据（证据来源）

本文件的每一条需求都尽量指向可核验的证据。梳理过程中实际读取的来源如下：

| 类别 | 证据来源 |
| --- | --- |
| 需求来源 | 仓库根 `README.md`（长期开发总控提示词，含 25 节规范与阶段 0–13 任务书） |
| 架构基线 | `stardust_springboot/docs/ai-chat/architecture.md` |
| 接口契约 | `stardust_springboot/docs/ai-chat/api.md` |
| 数据模型 | `stardust_springboot/docs/ai-chat/database.md` |
| 技术决策 | `stardust_springboot/docs/ai-chat/decisions.md`（ADR-001 ~ ADR-064） |
| 进度记录 | `stardust_springboot/docs/ai-chat/progress.md` |
| 前端事实 | `stardust_vue/package.json`、`src/router/index.ts`、`src/` 全量文件扫描 |
| 后端事实 | `stardust_springboot/pom.xml`、`src/main/resources/application.properties`、`.env.example`、`src/main/java/**`、`src/main/resources/db/migration/*.sql`、`src/test/java/**` |
| AI 服务事实 | `stardust_ai/pyproject.toml`、`.env.example`、`app/**/*.py`、`tests/**/*.py` |

### 0.2 状态标注约定

本文件严格区分以下四类陈述，不得混用：

| 标记 | 含义 | 判定标准 |
| --- | --- | --- |
| `[已实现]` | 仓库中已存在对应代码，且能在文档中找到实现记录 | 有文件路径证据 |
| `[部分]` | 核心能力已实现，但存在明确、已被记录的有意边界 | 代码存在 + 边界被记录 |
| `[未实现]` | 仓库中不存在该能力 | 无代码证据，或文档明确标注延后 |
| `[待验证]` | 文件存在但本次梳理**未执行运行时验证**，不作可用性承诺 | 需补充执行证据 |

同时区分三类陈述口气：

- **当前事实**：本次扫描在仓库中确认到的内容。
- **目标决策**：后续开发必须遵守的约束（来自 README 与 ADR）。
- **待确认**：本文件认为存在歧义或风险，需要 Owner 明确。

> 说明：`[待验证]` 不等同于「有问题」，只表示本次梳理是**只读审计**，没有运行构建、测试或服务。任何未执行过的命令都不得当作已验证结论。

### 0.3 与其他文档的边界

| 文档 | 职责 | 本文件的差异 |
| --- | --- | --- |
| `requirements.md`（本文） | 回答「系统要满足什么」 | 需求视角、可验收、可追溯 |
| `architecture.md` | 回答「系统按什么结构实现」 | 结构视角 |
| `api.md` | 回答「三端接口长什么样」 | 契约视角，本文只做索引与汇总 |
| `database.md` | 回答「数据怎么存」 | 数据视角，本文只做索引与汇总 |
| `progress.md` | 回答「做到哪一步了」 | 过程视角 |
| `decisions.md` | 回答「为什么这么定」 | 决策视角 |

本文**不重复**展开 `api.md` / `database.md` 的字段级细节，只建立需求 → 证据的索引关系。

---

## 1. 项目概述

### 1.1 产品定位

| 项 | 内容 | 状态 |
| --- | --- | --- |
| 产品名 | 星语 AI（Stardust AI Chat） | `[已实现]` 证据：`stardust_vue/src/views/LoginView.vue`、`RegisterView.vue`、`src/App.vue`、`components/chat/ChatSidebar.vue`、`public/index.html` 均出现「星语」 |
| 工程名 | `stardust_vue` / `stardust_springboot` / `stardust_ai` | `[已实现]` |
| 产品形态 | 多用户 Web 端 AI 对话 SaaS，含用户端与管理员端 | `[已实现]` |
| 目标水平 | 生产级、可长期维护、可扩展，对标现代 GPT Web 体验 | **目标决策**（README 第二十五节） |

### 1.2 建设目标（目标决策）

1. 提供与主流 AI 对话产品对等的核心体验：流式输出、Markdown/公式/代码渲染、消息分支、附件、云盘、记忆、知识库。
2. 提供一个真正可用（而非占位）的管理员后台：用户、聊天、文件、知识库、Provider/Model、用量、审计全链路可管。
3. 保证架构可替换：Provider、Storage、VectorStore、Embedding、Parser 均为可插拔抽象，未来接 DeepSeek / OpenAI / Anthropic / Gemini / Ollama、MinIO / S3 / OSS / COS、Qdrant / Milvus / pgvector 时不需要推翻系统。
4. 明确否决「页面能打开就算完成」的验收标准。

### 1.3 系统边界

**做（In Scope）**

- 多用户注册登录与账号体系、基于角色的权限控制。
- 会话与消息的全生命周期管理，含消息变体（编辑重发 / 重新生成）。
- Vue → Spring Boot → Python AI → LLM Provider 的完整 SSE 流式链路。
- 文件上传与用户云盘（元数据 + 配额 + 可替换存储）。
- 短期记忆（摘要 + Token 预算）与长期记忆（CRUD + 有界检索）。
- 知识库与 RAG（解析 → 分块 → 向量化 → 检索 → 注入）。
- AI 用量账户、额度预留/结算/释放、周期对账。
- 管理员后台十个功能域，敏感操作强制审计。
- 限流、可观测性（Actuator/Prometheus）。

**不做（Out of Scope，当前阶段）**

- 组织 / 租户（tenant）多层级：`architecture.md` §8 明确「当前模型不宣称支持 organization/tenant」。
- 工具调用 / Function Calling / MCP 的**实际执行**：SSE 仅**预留** `tool_start/tool_delta/tool_done` 事件类型。`[未实现]`
- 多模态图片理解的实际推理：附件目前只做引用与文本抽取，图片能力为**预留**。`[部分]`
- 图片生成、语音、联网搜索：无代码证据。`[未实现]`
- 向量数据库的生产级选型：首版为可替换的 SQLite adapter。`[部分]`
- 病毒扫描 / CDR 内容清洗：`architecture.md` §8 明确「不得把 magic 校验误称为恶意内容扫描」。`[未实现]`
- 多实例部署下的跨节点 Stop 路由：`architecture.md` §3 记录为 P1 技术债。`[未实现]`

### 1.4 术语表

| 术语 | 定义 |
| --- | --- |
| Conversation | 一次会话，聚合根，持有标题、归属、消息计数与最后消息时间 |
| Message | 消息事实，含 `role`、`sequenceNo`、`variantNo`、分支指针 |
| 变体（variant） | 同一 `sequenceNo` 下的不同版本消息；编辑重发与重新生成均**新增**变体，不覆盖历史 |
| 短期记忆 | System Prompt + Conversation Summary + Recent Messages，属 Prompt 编排 |
| 长期记忆 | 独立于聊天记录保存的用户级稳定事实（偏好 / 项目 / 目标 / 显式要求） |
| RAG | 检索增强生成：知识库文档 → 分块 → 向量 → 检索 → 上下文注入 |
| ownership | 资源归属校验；查询必须带 `user_id` 条件，禁止只按资源 ID 查询 |
| requestId | 标识一次入口 HTTP 请求 |
| traceId | 标识跨 Spring / Python / Provider 的完整调用链（W3C Trace Context） |
| aiRequestId | 一次 AI 生成的内部标识；与 public `requestId`、`ai_request_log.request_id` 同值 |
| reserve / settle / release | AI 额度的预留 / 结算 / 释放三段式记账语义 |

### 1.5 角色与权限层级

| 角色 | 权限 |
| --- | --- |
| `USER` | 仅访问 `/api/v1/**` 中属于自身的资源；访问 `/admin/**` 返回 `40301` |
| `ADMIN` | 上述 + 管理端全部只读/管理能力；**不得**触碰 `SUPER_ADMIN` 的私人资源；**不得**自我提权 |
| `SUPER_ADMIN` | 系统最高权限 |

层级：`SUPER_ADMIN > ADMIN > USER`（证据：`database.md` §4.2、ADR-025）。

账号状态（四态，**禁止**用单一 Boolean 表达）：
`NORMAL` / `BANNED` / `DISABLED` / `DELETED`（证据：`database.md` §3、`user/entity/UserStatus.java`）。

---

## 2. 总体需求

### 2.1 三端职责划分（硬约束）

```text
Browser / Vue 3
   │ REST JSON / multipart / fetch SSE
   ▼
Spring Boot  ← 系统唯一业务入口与事实源
   │ 内网认证 + JSON/SSE + traceparent
   ▼
Python AI  ← 无用户身份事实、无业务主库写权限
   │ Provider-specific SDK/API
   ▼
LLM / Embedding / Vector Store / Parser
```

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| TR-01 | **禁止 Vue 直接调用 Python** | `[已实现]` ADR-001 |
| TR-02 | Spring Boot 是唯一外部业务 API 与业务事实源 | `[已实现]` ADR-002 |
| TR-03 | Python 不解析用户 JWT、不建用户 session、不自行做 RBAC | `[已实现]` ADR-002 / ADR-031 |
| TR-04 | Python 不直连业务 MySQL | `[已实现]` ADR-002 |
| TR-05 | MySQL 为业务事实源；Redis 只存可重建短时状态，不得成为用户/消息/配额/审计的唯一存储 | `[已实现]` ADR-011 |
| TR-06 | Spring 只向 Python 传递 AI 计算所需的最小上下文 | `[已实现]` `api.md` §5.1 |

### 2.2 架构原则（目标决策）

高内聚、低耦合、单一职责、接口抽象、模块化、DTO/Entity 分离、API Model 与 DB Model 分离、可测试、可扩展、可观测、安全。

**禁止清单**：God Class / God Service / God Component / God Controller；单个 Controller 处理聊天+AI+文件+RAG+Memory；单个 `ChatService.py` 实现整个 AI 系统；单个 `Chat.vue` 包含整个聊天界面。

`[部分]` 现状核对：Vue 聊天界面已拆为 `components/chat/` 下 13 个组件（`ChatLayout`、`ChatSidebar`、`ConversationList`、`ConversationItem`、`ChatHeader`、`ChatMessageList`、`ChatMessage`、`ChatComposer`、`ChatMarkdown`、`ChatCodeBlock`、`ChatAttachment`、`MessageAttachments`、`ModelSelector`、`KnowledgeBaseSelector`、`StreamingCursor`）；后端按业务能力分包（`auth` / `user` / `admin` / `conversation` / `file` / `memory` / `knowledge` / `ai` / `usage`）。**本次未做类规模度量**，故标记为 `[待验证]`。

### 2.3 可插拔抽象清单

| 编号 | 抽象 | 首版实现 | 未来目标 | 状态 |
| --- | --- | --- | --- | --- |
| AB-01 | `LLMProvider` | OpenAI-compatible adapter（async） | OpenAI / Azure / Anthropic / Gemini / DeepSeek / Ollama / OpenRouter | `[部分]` ADR-030 |
| AB-02 | `EmbeddingProvider` | 走同一 OpenAI-compatible 通道 | 多厂商 | `[部分]` |
| AB-03 | `VectorStore` | `memory` + `sqlite` adapter | pgvector / Qdrant / Milvus / ES / Chroma | `[部分]` ADR-048 |
| AB-04 | `StorageProvider` | `LocalStorageProvider` | MinIO / S3 / OSS / COS | `[部分]` ADR-041 |
| AB-05 | `DocumentParser` | 由 Spring 侧 PDFBox + Python 侧处理 | 多格式 | `[部分]` |
| AB-06 | `TextSplitter` | 字符分块 + overlap | 语义分块 | `[已实现]` |
| AB-07 | `Reranker` | **未实现**（预留） | 精排模型 | `[未实现]` |
| AB-08 | `RateLimiter` | `InMemoryRateLimiter` | `RedisRateLimiter`（已实现可选适配器） | `[已实现]` ADR-056 |
| AB-09 | `AiGateway` | `JdkHttpAiGateway` | 可替换 | `[已实现]` ADR-034 |
| AB-10 | `RagGateway` | `JdkHttpRagGateway` | 可替换 | `[已实现]` |
| AB-11 | `MemoryRetriever` / `MemoryExtractor` | Spring Bean | 未来可换无状态语义实现 | `[已实现]` ADR-045/046 |
| AB-12 | `TokenCounter` | 估算实现 | 精确分词 | `[已实现]` |
| AB-13 | `ProviderCredentialResolver` | `EnvironmentProviderCredentialResolver`（按 `env:NAME` 解析） | Vault 等 | `[部分]` ADR-016/061 |

---

## 3. 功能需求

### 3.1 用户端（FR-U）

#### 3.1.1 认证与账号

| 编号 | 需求 | 状态与证据 |
| --- | --- | --- |
| FR-U-01 | 用户可注册（邮箱 + 密码 ≥12 位 + 显示名） | `[已实现]` `api.md` §3.1；`RegisterView.vue` |
| FR-U-02 | 用户可登录，Access Token 只放内存，Refresh Token 只放 HttpOnly Cookie | `[已实现]` ADR-022；`store/auth.ts` |
| FR-U-03 | Refresh Token 必须 rotation；旧 token 再用返回 `40103` 并撤销整族 | `[已实现]` ADR-005；`auth/service/RefreshTokenService.java` |
| FR-U-04 | 可修改个人资料（仅 `displayName`） | `[已实现]` `user/controller/UserController.java`、`ProfileView.vue` |
| FR-U-05 | 可修改密码；修改后递增 `auth_version` 使旧 Access Token 立即失效 | `[已实现]` ADR-024 |
| FR-U-06 | 可退出登录；支持撤销当前会话与撤销全部会话 | `[已实现]` `/auth/logout`、`/auth/logout-all` |
| FR-U-07 | 401 时前端自动 refresh 一次并重试原请求 | `[已实现]` `api/client.ts` |
| FR-U-08 | Router Guard：未登录跳登录页，已登录访问登录/注册页跳回聊天 | `[已实现]` `router/index.ts` §beforeEach |
| FR-U-09 | 登录连续失败按「客户端地址 + 邮箱」双维度冷却限流 | `[已实现]` ADR-052；`auth/security/LoginAttemptGuard.java` |

#### 3.1.2 会话与消息

| 编号 | 需求 | 状态与证据 |
| --- | --- | --- |
| FR-U-10 | 新建 / 分页 / 搜索 / 排序 / 改标题 / 归档 / 软删除会话 | `[已实现]` `api.md` §3.2；`conversation/controller/ConversationController.java` |
| FR-U-11 | 会话支持按 `lastMessageAt` / `createdAt` / `updatedAt` / `title` 排序 | `[已实现]` `conversation/service/ConversationSort.java` |
| FR-U-12 | 查看会话消息，固定按 `sequenceNo ASC, id ASC` | `[已实现]` |
| FR-U-13 | 消息搜索（跨会话全文检索） | `[已实现]` `conversation/controller/MessageSearchController.java`；**已超出 README 原始需求范围** |
| FR-U-14 | 会话导出 | `[已实现]` `conversation/service/ConversationExportService.java`；**已超出原始需求范围** |
| FR-U-15 | 会话可绑定 0~20 个知识库 | `[已实现]` `knowledge/controller/ConversationKnowledgeBaseController.java` |
| FR-U-16 | 用户 A 永远不能读取/修改/删除用户 B 的会话与消息；越权统一 404 | `[已实现]` ADR-027 |
| FR-U-17 | 会话标题可自动生成 | `[部分]` `conversation/service/ConversationTitleService.java` 存在；生成策略与触发时机本次未核验 `[待验证]` |

#### 3.1.3 流式生成与消息分支

| 编号 | 需求 | 状态与证据 |
| --- | --- | --- |
| FR-U-18 | 流式生成：`POST /api/v1/conversations/{id}/messages:stream` | `[已实现]` ADR-003；`ai/controller/AiStreamController.java` |
| FR-U-19 | 浏览器使用 `fetch` 流解析 POST SSE，不用原生 `EventSource` | `[已实现]` `api/sseParser.ts` |
| FR-U-20 | 支持主动停止生成；stop 接口 owner-scoped 且幂等 | `[已实现]` ADR-035 |
| FR-U-21 | 支持重新生成（Regenerate）：复用原 USER parent，新增同 `sequenceNo` 的 Assistant 变体 | `[已实现]` ADR-038 |
| FR-U-22 | 支持编辑问题并重发（Edit-and-resend）：新增 USER 变体及其对应的 Assistant 变体 | `[已实现]` ADR-038 |
| FR-U-23 | 前端按真实分支指针（`parentMessageId` / `supersedesMessageId` / `variantNo`）选择活动分支；刷新后以数据库为准 | `[已实现]` `composables/chatStreamReducer.ts`、`utils/conversationBranch.ts` |
| FR-U-24 | 必须处理：用户停止、浏览器断开、Python 失败、Provider 超时/429/500、DB 失败、重复请求、半生成状态 | `[已实现]` `ai/stream/AiStreamRecoveryService.java` |
| FR-U-25 | 启动时把遗留 `PENDING`/`STREAMING` 消息收敛为 `FAILED/SERVER_RESTART` | `[已实现]` `architecture.md` §7.8 |
| FR-U-26 | 生成前完成额度预留；不足返回 `42902` 且**不创建** USER/ASSISTANT 占位消息、不建立 SSE | `[已实现]` ADR-051 |
| FR-U-27 | 单条当前 USER 超输入预算时，在建立 SSE 前返回 `40010 CONTEXT_WINDOW_EXCEEDED` | `[已实现]` `api.md` §5.3 |

#### 3.1.4 附件与云盘

| 编号 | 需求 | 状态与证据 |
| --- | --- | --- |
| FR-U-28 | 统一 `user_file` 同时承载聊天附件与云盘，不建两套存储 | `[已实现]` ADR-006 |
| FR-U-29 | 附件以 `user_file.id` 引用，不重复上传对象 | `[已实现]` ADR-043 |
| FR-U-30 | 文件上传 / 列表（分页 + 搜索）/ 详情 / 重命名 / 下载 / 预览 / 删除 | `[已实现]` `file/controller/UserFileController.java`、`FilesView.vue` |
| FR-U-31 | 重命名不得改变扩展名 | `[已实现]` `api.md` §3.5 |
| FR-U-32 | 用户存储配额：`usedBytes` / `reservedBytes` / `quotaBytes` / `fileCount` | `[已实现]` `file/entity/UserStorageUsage.java` |
| FR-U-33 | 并发上传时容量统计不得明显失真（悲观锁 + reservation） | `[已实现]` ADR-042 |
| FR-U-34 | 文件被聊天消息或知识库文档引用时禁止删除，返回 `40901` | `[已实现]` ADR-043 |
| FR-U-35 | 下载/预览响应固定带 `X-Content-Type-Options: nosniff`、受限 CSP、`Cache-Control: private, no-store` | `[已实现]` `api.md` §3.5 |
| FR-U-36 | 图片附件可预览；为未来多模态保留 metadata | `[部分]` `metadata_json` 仅存 `category/previewable`；**图片理解推理未实现** |
| FR-U-37 | 上传校验：大小、扩展名、声明 MIME、实际 MIME、magic/text 有效性、文件名规范化 | `[已实现]` `file/service/FileTypePolicy.java` |
| FR-U-38 | PDF 附件文本抽取 | `[已实现]` `ai/attachment/PdfTextExtractor.java`（PDFBox 3.0.4）；**已超出原始需求范围** |

#### 3.1.5 记忆

| 编号 | 需求 | 状态与证据 |
| --- | --- | --- |
| FR-U-39 | 短期记忆：System Prompt + Conversation Summary + Recent Messages 组装 | `[已实现]` ADR-044；`conversation/memory/ConversationContextBuilder.java` |
| FR-U-40 | **禁止**每次请求把全部历史消息塞进 Prompt；采用 Token 预算而非固定条数 | `[已实现]` `app.ai.context.*` 配置项 |
| FR-U-41 | 当前 User Message 不得在 Recent Messages 中重复出现 | `[已实现]` `api.md` §5.3 |
| FR-U-42 | 历史超阈值时滚动摘要，摘要带版本与锚点消息，**按分支**生效 | `[已实现]` `conversation_summary` 表；`database.md` §5.4 |
| FR-U-43 | 摘要失败不得导致聊天失败（失败开放） | `[已实现]` `architecture.md` §7.7 |
| FR-U-44 | 长期记忆 CRUD / 启停 / 搜索 / 分页 | `[已实现]` `memory/controller/MemoryController.java`、`MemoriesView.vue` |
| FR-U-45 | 长期记忆类型：`PREFERENCE` / `PROJECT` / `GOAL` / `EXPLICIT` | `[已实现]` `memory/entity/MemoryType.java` |
| FR-U-46 | 自动提取必须保守：只识别稳定偏好、长期项目/目标、显式「记住」请求；敏感或不确定内容不自动保存 | `[已实现]` ADR-046 |
| FR-U-47 | 自动提取失败不得影响主 Chat 成功终态 | `[已实现]` |
| FR-U-48 | 检索**禁止** `SELECT` 全部记忆；须有候选上限、Top-K、重要度与 Token 预算 | `[已实现]` `app.ai.memory.retrieval-limit=5`、`candidate-limit=100`、`token-budget=512` |
| FR-U-49 | 禁用与软删除的记忆不参与检索 | `[已实现]` `database.md` §7.1 |
| FR-U-50 | 长期记忆对模型呈现为「不可信」system block | `[已实现]` `api.md` §5.3 |

#### 3.1.6 知识库与 RAG

| 编号 | 需求 | 状态与证据 |
| --- | --- | --- |
| FR-U-51 | 知识库创建 / 修改 / 删除 / 列表 / 详情 | `[已实现]` `knowledge/controller/KnowledgeBaseController.java` |
| FR-U-52 | 文档引用 `user_file`，不重复上传 | `[已实现]` ADR-007 |
| FR-U-53 | 文档状态机：`UPLOADED → PARSING → PARSED → EMBEDDING → READY`，异常收敛 `FAILED` | `[已实现]` `knowledge/entity/KnowledgeDocumentStatus.java` |
| FR-U-54 | 仅 `FAILED` 文档可重试 | `[已实现]` 其余状态返回 `40901` |
| FR-U-55 | RAG 与文件系统解耦；聊天附件**不等于**知识库文档 | `[已实现]` ADR-007 |
| FR-U-56 | RAG ownership 双重收口：VectorStore metadata 带 `userId` 过滤 + Spring 侧 MySQL `READY` 与 owner 复核 | `[已实现]` ADR-047 |
| FR-U-57 | 用户 A 不允许检索用户 B 的知识库 | `[已实现]`（有集成测试 `KnowledgeRagIntegrationTests.java`） |
| FR-U-58 | 检索结果包装为「不可信」reference block，Python 返回的 source **不直接**成为授权事实 | `[已实现]` `api.md` §6 |
| FR-U-59 | 精排 Reranker | `[未实现]` 仅预留接口位置 |

#### 3.1.7 用量与模型

| 编号 | 需求 | 状态与证据 |
| --- | --- | --- |
| FR-U-60 | 用户可查看当前周期 token / cost / 配额 | `[已实现]` `/api/v1/usage`、`components/usage/UsagePanel.vue` |
| FR-U-61 | 用量按日 / 模型 / Provider 聚合 | `[已实现]` `/api/v1/usage/breakdown` |
| FR-U-62 | 只返回「Provider 已启用 + 模型已启用」的 CHAT 模型 | `[已实现]` `/api/v1/ai/models` |
| FR-U-63 | 用户侧模型目录下发 `defaultModel` 供前端预选 | `[已实现]` ADR-061 相关 |
| FR-U-64 | 用户在界面上自行调整模型采样参数 | `[未实现]` `api.md` §4.1 明确「请求不接受采样参数」；模型默认参数由管理端 `parameter_policy_json` 决定 |
| FR-U-65 | 用户选择模型 | `[已实现]` `components/chat/ModelSelector.vue` |

#### 3.1.8 界面与体验

| 编号 | 需求 | 状态与证据 |
| --- | --- | --- |
| FR-U-66 | 桌面端左侧 Sidebar（新建/搜索/最近会话/用户菜单）+ 中央消息区 + 输入区 | `[已实现]` `components/chat/ChatLayout.vue` |
| FR-U-67 | 移动端 Sidebar 可收起，聊天区自适应 | `[已实现]` `[待验证]`（未在真机/窄屏执行验证） |
| FR-U-68 | Markdown：标题/粗体/斜体/列表/表格/引用/行内代码/代码块 | `[已实现]` ADR-014 |
| FR-U-69 | 代码块语法高亮 + 语言标识 + 复制按钮 | `[已实现]` `ChatCodeBlock.vue`、highlight.js 11.11.1 |
| FR-U-70 | LaTeX 公式渲染 | `[已实现]` KaTeX 0.16.22 |
| FR-U-71 | Markdown 必须做 XSS 防护：默认禁用原始 HTML，渲染后二次消毒，链接协议白名单 | `[已实现]` ADR-039；`utils/markdown.ts`、DOMPurify 3.2.6 |
| FR-U-72 | 流式增量实时渲染 | `[已实现]` `StreamingCursor.vue` |
| FR-U-73 | 自动滚动到底；用户主动上滚时不得强制拉回 | `[已实现]` `ChatMessageList.vue` |
| FR-U-74 | 消息复制 | `[已实现]` |
| FR-U-75 | 深色模式 / 主题切换 | `[已实现]` `components/ThemeToggle.vue`、`composables/useTheme.ts`；**已超出原始需求范围** |
| FR-U-76 | 全量中文化 | `[已实现]` `progress.md`「前端 UI 重构（仿 ChatGPT 风格 + 全量中文化）」 |
| FR-U-77 | 不复制 ChatGPT 商标与品牌资源，仅借鉴信息架构 | **目标决策**（README 阶段 6） |

### 3.2 管理端（FR-A）

统一前缀 `/api/v1/admin/**`，由 `SecurityConfiguration` 的 `hasAnyRole` 强制 `ADMIN`/`SUPER_ADMIN`。

#### 3.2.1 总览

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| FR-A-01 | Dashboard 实时只读聚合：用户数、今日新增、活跃、会话、消息、AI 请求、token、文件、存储、RAG 文档、系统错误、启用 Provider/Model | `[已实现]` ADR-062 |
| FR-A-02 | 24 小时逐时趋势：固定 24 个桶、UTC 分桶、由旧到新，空平台返回零值桶 | `[已实现]` 分桶在 Java 内完成，不依赖数据库 `hour()` 方言 |
| FR-A-03 | 最近 5 条审计与最近 5 条失败请求 | `[已实现]` |
| FR-A-04 | **不建物化表**，全部实时统计 | `[已实现]` |

#### 3.2.2 用户管理

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| FR-A-05 | 用户列表：分页 + 模糊搜索（邮箱/显示名）+ 状态筛选 + 角色筛选 + 创建时间排序 | `[已实现]` |
| FR-A-06 | 用户详情（已删除用户仍可读取以便审计核对） | `[已实现]` |
| FR-A-07 | 新建用户（管理员），密码经 `PasswordEncoder` 加密 | `[已实现]` |
| FR-A-08 | 修改邮箱 / 显示名 | `[已实现]` |
| FR-A-09 | 状态变更：封禁 / 解封 / 禁用 / 恢复 | `[已实现]` |
| FR-A-10 | 恢复已删除用户（重置为 `NORMAL`） | `[已实现]` |
| FR-A-11 | 重置密码（≥12 位），并吊销其全部会话 | `[已实现]` |
| FR-A-12 | 角色变更 | `[已实现]` |
| FR-A-13 | 软删除用户 | `[已实现]` |
| FR-A-14 | 额度调整 `POST /admin/users/{id}/usage:adjust`，理由必填、负增量不得使已用量变负 | `[已实现]` ADR-054 |
| FR-A-15 | 禁止 `ADMIN` 修改/封禁/停用/删除/改角色/重置 `SUPER_ADMIN` | `[已实现]` ADR-058；`admin/service/AdminAuthorizationService.java` |
| FR-A-16 | 禁止 `ADMIN` 把自己提升为 `ADMIN`/`SUPER_ADMIN` | `[已实现]` `validateRoleAssignment` |
| FR-A-17 | 禁止 `ADMIN` 对自己执行删除/禁用等破坏性操作 | `[已实现]` 返回 `40301` |
| FR-A-18 | `BANNED`/`DISABLED`/`DELETED` 用户必须由**服务端**拒绝受保护业务，不能只靠前端限制 | `[已实现]` JWT Filter 每次请求重读状态 |

#### 3.2.3 聊天记录管理

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| FR-A-19 | 会话目录：分页 + 搜索（标题/用户名/邮箱/消息正文）+ 用户过滤 + 时间区间 | `[已实现]` |
| FR-A-20 | 会话详情（内嵌首屏 100 条消息） | `[已实现]` |
| FR-A-21 | 会话消息分页（按 `sequenceNo`/`variantNo` 升序） | `[已实现]` |
| FR-A-22 | 软删除违规会话 / 软删除单条消息（同步递减会话计数） | `[已实现]` |
| FR-A-23 | 列表与详情适用**同一**授权规则，不得出现「列表可见、点击被拒」 | `[已实现]` ADR-063 |
| FR-A-24 | 复用既有 `Conversation` / `ChatMessage` Entity 与 Repository，不新建 `AdminConversation` Entity | `[已实现]` ADR-058 |
| FR-A-25 | 列表页只展示元数据，完整聊天内容只在详情页呈现 | `[已实现]` |
| FR-A-26 | 非 `SUPER_ADMIN` 管理员**看不到** `SUPER_ADMIN` 的私人会话 | `[已实现]` |

#### 3.2.4 文件管理

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| FR-A-27 | 文件目录：分页 + 文件名搜索 + 用户名/邮箱搜索 + MIME 前缀 + 状态 + 大小区间 + 时间区间 | `[已实现]` |
| FR-A-28 | 文件详情：元数据 + 所属用户 + 引用统计 | `[已实现]` |
| FR-A-29 | 下载（经 `StorageService` 流式返回） | `[已实现]` |
| FR-A-30 | 删除违规文件（经 `StorageService` 删除对象 + 软删除 + 释放配额） | `[已实现]` |
| FR-A-31 | **绝不返回** `object_key` 或服务器内部绝对路径 | `[已实现]` ADR-059 |
| FR-A-32 | 路径穿越防御：打开/删除前拒绝含 `..`、以 `/` 开头、含反斜杠的 object key（纵深防御） | `[已实现]` |
| FR-A-33 | 仅 `AVAILABLE` 文件可下载/删除 | `[已实现]` `FilePersistenceService` 状态机 |
| FR-A-34 | 非 `SUPER_ADMIN` 管理员禁止查看/下载/删除 `SUPER_ADMIN` 的私人文件 | `[已实现]` |

#### 3.2.5 知识库与 RAG 管理

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| FR-A-35 | 知识库列表（名称/用户搜索、状态筛选）+ 详情（所属用户 + 处理概况 + 首屏 50 条文档） | `[已实现]` |
| FR-A-36 | 文档列表：KB/用户过滤 + 文件名/用户搜索 + 状态筛选 | `[已实现]` |
| FR-A-37 | 重新处理 `FAILED` 文档（重新解析 + 向量化） | `[已实现]` |
| FR-A-38 | 删除文档（分块 + 元数据 + 向量索引） | `[已实现]` |
| FR-A-39 | 仅删除向量数据，保留文档记录为 `FAILED`/`VECTOR_REMOVED` 以便审计追溯 | `[已实现]` |
| FR-A-40 | `chunkCount` 必须始终与真实 `document_chunk` 行数一致 | `[已实现]` |
| FR-A-41 | AI 处理动作由 Spring 经 `RagGateway` 中转 Python，**Vue 永不直连 Python** | `[已实现]` ADR-060 |
| FR-A-42 | 非 `SUPER_ADMIN` 管理员禁止操作 `SUPER_ADMIN` 的知识库 | `[已实现]` |

#### 3.2.6 Provider 与 Model 管理

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| FR-A-43 | Provider CRUD + 启停 | `[已实现]` |
| FR-A-44 | Model CRUD + 启停 + 默认模型 + 排序 | `[已实现]` |
| FR-A-45 | **密钥只写不读**：响应只返回 `hasApiKey` 与运行时解析出的 `maskedApiKey` | `[已实现]` ADR-061 |
| FR-A-46 | 写入只接受引用形式（`env:NAME` / `vault:…`）；裸 Key 返回 `40001` | `[已实现]` ADR-016 |
| FR-A-47 | 编辑时 `credentialRef` 留空 = 保持原值 | `[已实现]` |
| FR-A-48 | 审计元数据只含布尔与非敏感字段，**绝不记录**引用名或密钥 | `[已实现]` |
| FR-A-49 | `defaultModel` 在同一 `type` 内唯一；仅 `ENABLED` 模型可设默认；停用立即清除默认标记 | `[已实现]` |
| FR-A-50 | 新建 Provider/Model 默认 `DISABLED`，必须显式启用 | `[已实现]` |
| FR-A-51 | 排序先用同 Provider 模型规范化为 `10/20/30…` 再交换，保证移动确定性；首位继续上移为幂等空操作 | `[已实现]` |
| FR-A-52 | `code` 与 `providerId` 不可变（改动返回 `40901`） | `[已实现]` |
| FR-A-53 | 删除 Provider/Model，若已产生请求日志（`ON DELETE RESTRICT`）返回 `40901` | `[已实现]` |
| FR-A-54 | JSON 形状向后兼容：`capabilities` 兼容历史数组式，参数兼容历史嵌套式；损坏值退化为「未配置」不 500 | `[已实现]` `ai/catalog/AiCatalogJsonCodec.java` |
| FR-A-55 | 控制台只负责目录与开关，**不**把连接配置下发给 Python | `[已实现]` ADR-032 |

#### 3.2.7 审计日志与 AI 请求日志

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| FR-A-56 | 审计日志分页 + 按管理员/动作/目标类型/时间区间筛选 | `[已实现]` |
| FR-A-57 | AI 请求日志分页 + 按用户/状态/Provider/Model/时间区间筛选 | `[已实现]` |
| FR-A-58 | 单条调用详情（耗时、token、错误码） | `[已实现]` |
| FR-A-59 | 三个接口**全部只读**，无写入/修改/删除入口；前端无法伪造或抹除审计 | `[已实现]` ADR-062 |
| FR-A-60 | 审计由业务 Service 在动作发生时写入，**Vue 不自行提交审计事件** | `[已实现]` ADR-058 |
| FR-A-61 | 审计必须覆盖：封禁/解封/禁用/恢复/删除/重置密码/改角色/创建/修改用户、查看会话与消息、删除会话与消息、查看/下载/删除文件、知识库查看/重试/删除/删向量、Provider/Model 全部写操作、额度调整 | `[已实现]` 见 `admin/audit/AdminAuditAction.java` |
| FR-A-62 | `admin_audit_log` 代码层不提供 update/delete Repository | `[已实现]`（`AdminAuditLogRepository` 仅保存与查询） |
| FR-A-63 | 危险操作前端必须二次确认 | `[已实现]` `components/admin/AdminConfirmModal.vue` |
| FR-A-64 | 极端情况：写入审计的事务不得声明 `readOnly`；审计枚举读取必须容错 | `[已实现]` ADR-064（阶段 11H 排错结论） |

### 3.3 Python AI 服务（FR-P）

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| FR-P-01 | `GET /health` 返回进程与配置就绪状态；**不调用**收费远端模型；无需服务令牌以供容器探针使用 | `[已实现]` |
| FR-P-02 | `POST /internal/chat` 返回完整非流式响应 | `[已实现]` |
| FR-P-03 | `POST /internal/chat/stream` 返回标准化 internal SSE | `[已实现]` |
| FR-P-04 | `LLMProvider` 抽象：`chat()` / `stream_chat()` / `embedding()`，async 接口 | `[已实现]` ADR-030 |
| FR-P-05 | Provider 选择集中在 Registry/Factory，禁止 `if model == "openai"` 散落业务层 | `[已实现]` `providers/registry.py` |
| FR-P-06 | 请求不得携带 `baseUrl` / API Key / `credentialRef` | `[已实现]` |
| FR-P-07 | Pydantic `extra=forbid` 拒绝未知字段 | `[已实现]` |
| FR-P-08 | 内部认证：`X-Service-Authorization` 共享令牌 + 常量时间比较；缺失或短于 32 字符 fail-closed | `[已实现]` ADR-031 |
| FR-P-09 | Provider 超时 / 网络不可用 / 限流 / 请求拒绝 / 非法响应分别映射为稳定内部错误码；响应与日志**不透传** Provider body | `[已实现]` |
| FR-P-10 | 内部 SSE 根级字段：`type/schemaVersion/aiRequestId/requestId/seq/timestamp` + `payload` | `[已实现]` ADR-036 |
| FR-P-11 | RAG：`/internal/rag/documents/process`（二进制）、`/internal/rag/retrieve`、`DELETE /internal/rag/documents/{id}` | `[已实现]` |
| FR-P-12 | 分块参数默认 `1800` 字符 + `180` overlap，可配置并带上界 | `[已实现]` `rag_chunk_chars` / `rag_chunk_overlap_chars` |
| FR-P-13 | 附件渲染预算由 Python 侧独立限制（`attachment_max_text_chars` 默认 60000） | `[已实现]` |
| FR-P-14 | 日志不得记录完整 Prompt、Authorization 或 Provider 密钥 | `[已实现]` `core/logging.py` |
| FR-P-15 | 演示用节流 `stream_throttle_ms` 默认必须为 `0` | `[已实现]` `settings.py` 默认值 0，生产不得开启 |

---

## 4. 数据需求

### 4.1 迁移清单（Flyway，唯一 schema 变更入口）

| 版本 | 内容 | 状态 |
| --- | --- | --- |
| V1 | 核心七表：`app_user`、`app_role`、`app_user_role`、`conversation`、`chat_message`、`ai_provider`、`ai_model` | `[已实现]` ADR-020 |
| V2 | `refresh_token`、`app_user.auth_version`、内置三角色 | `[已实现]` |
| V3 | `chat_message.total_tokens` / `error_message` | `[已实现]` |
| V4 | `ai_request_log` | `[已实现]` |
| V5 | `user_file`、`user_storage_usage`、`chat_message_attachment` | `[已实现]` |
| V6 | `conversation_summary` | `[已实现]` |
| V7 | `user_memory` | `[已实现]` |
| V8 | `knowledge_base`、`knowledge_document`、`document_chunk`、`conversation_knowledge_base` | `[已实现]` |
| V9 | `admin_audit_log` | `[已实现]` |
| V10 | `ai_usage_account`、`ai_usage_ledger` | `[已实现]` |
| V11 | `ai_model.is_default` + `idx_ai_model_default` | `[已实现]` |
| V12 | 仅加索引：`ai_request_log(created_at, id)`、`admin_audit_log(created_at, id)` | `[已实现]` |
| V13 | 规范化管理员审计动作 | `[已实现]` |

### 4.2 硬性数据约束

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| DR-01 | 内部主键 `BIGINT`；对外资源暴露 `CHAR(26)` ULID `public_id`；API 永不暴露内部 ID | `[已实现]` ADR-012 |
| DR-02 | 核心表统一 `created_at` / `updated_at` / 乐观锁 `version`；时间用 UTC `DATETIME(3)` | `[已实现]` |
| DR-03 | 软删除表以 CHECK 保证「`DELETED` ⇔ `deleted_at` 非空」一致 | `[已实现]` ADR-021 |
| DR-04 | ownership 必须进入查询条件；不可因使用 ULID 而省略 `user_id` | `[已实现]` ADR-027 |
| DR-05 | 子表冗余 `user_id` 时，父表须建 `(id, user_id)` 唯一键并使用复合外键防跨用户脏关联 | `[已实现]` |
| DR-06 | FK 默认 `RESTRICT`；不级联删除审计 / usage / message 事实 | `[已实现]` |
| DR-07 | **只改 Entity 不加 migration 属违规** | **目标决策** |
| DR-08 | `ai_usage_ledger` 不可变：代码层只提供 `save` 与查询，不提供 update/delete | `[已实现]` |
| DR-09 | `sum(ledger.delta) == used + reserved` 必须恒成立 | `[已实现]` ADR-051 |
| DR-10 | 索引有效性必须在真实查询与 MySQL `EXPLAIN` 中持续验证 | `[部分]` `[待验证]` 本次未执行 `EXPLAIN` |

### 4.3 枚举字典（实际实现值）

| 对象 | 枚举值 | 状态 |
| --- | --- | --- |
| User | `NORMAL` / `BANNED` / `DISABLED` / `DELETED` | `[已实现]` |
| Conversation | `ACTIVE` / `ARCHIVED` / `DELETED` | `[已实现]` |
| Chat message | `PENDING` / `STREAMING` / `COMPLETED` / `STOPPED` / `FAILED` / `DELETED` | `[已实现]` |
| Message role | `USER` / `ASSISTANT` / `SYSTEM` / `TOOL` | `[已实现]` |
| User file（实际） | `UPLOADING` / `AVAILABLE` / `DELETING` / `FAILED` / `DELETED` | `[已实现]` |
| Knowledge doc（实际） | `UPLOADED` / `PARSING` / `PARSED` / `EMBEDDING` / `READY` / `FAILED` | `[已实现]` |
| Memory type | `PREFERENCE` / `PROJECT` / `GOAL` / `EXPLICIT` | `[已实现]` |
| Memory origin | `MANUAL` / `AUTO` | `[已实现]` |
| AI request（实际） | `PENDING` / `STREAMING` / `COMPLETED` / `STOPPED` / `FAILED` | `[已实现]` |
| Refresh token | `ACTIVE` / `ROTATED` / `REVOKED` / `EXPIRED` / `REUSED` | `[已实现]` |
| Provider / Model | `ENABLED` / `DISABLED`（另有独立 health status） | `[已实现]` |
| Usage ledger entry | `RESERVE` / `SETTLE` / `RELEASE` / `ADJUST` | `[已实现]` |

### 4.4 文档一致性问题（待 Owner 确认）

以下三处是 `database.md` §3「设计建议枚举」与 §实际实现章节 / `api.md` 之间的**文字性不一致**。实现以实际章节与 `api.md` 为准，但文档内部应统一：

| 编号 | 不一致点 | 建议 |
| --- | --- | --- |
| DI-01 | `database.md` §3 用户文件状态写作 `QUARANTINED`，§6.1 与 `api.md` §3.10 实际为 `DELETING` | 统一为实际值 `UPLOADING/AVAILABLE/DELETING/FAILED/DELETED` |
| DI-02 | `database.md` §3 知识文档状态写作 `PENDING/CHUNKING/DELETED`，§8.4 与 `api.md` §3.11 实际为 `UPLOADED/PARSING/PARSED/EMBEDDING/READY/FAILED` | 统一为实际值 |
| DI-03 | `database.md` §3 AI 请求状态写作 `STARTED/SUCCEEDED/TIMED_OUT`，§9.3 与 `api.md` §3.13 实际为 `PENDING/COMPLETED/FAILED` | 统一为实际值 |

> 判定口径：**代码与 `api.md` 契约是事实源**，§3 属早期设计草案，应在文档修订时对齐，不得反过来改代码去迁就草案。

---

## 5. 接口需求

### 5.1 通用约定（目标决策，已落地）

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| IR-01 | 公共 API 前缀 `/api/v1`；Python 内部前缀 `/internal` | `[已实现]` |
| IR-02 | JSON 字段统一 `camelCase`；时间为带时区 ISO 8601 UTC 字符串 | `[已实现]` |
| IR-03 | 统一响应 envelope：`{code, message, data, requestId, timestamp}` | `[已实现]` `common/api/ApiResult.java` |
| IR-04 | 错误响应可带 `errors[]`（仅校验错误），**不得**回显密钥 / SQL / 堆栈 / Provider 原始响应 | `[已实现]` |
| IR-05 | 错误码分段：`0` 成功；`400xx` 请求；`401xx` 认证；`403xx` 权限/状态/归属；`404xx` 不存在；`409xx` 冲突；`413xx`/`415xx` 上传；`429xx` 限流/额度；`500xx` 本服务；`502xx` 上游；`504xx` 超时 | `[已实现]` `common/exception/ErrorCode.java` |
| IR-06 | HTTP 状态码表达协议结果，业务 `code` 表达机器语义；错误不得一律返回 200 | `[已实现]` |
| IR-07 | v1 请求未知字段默认拒绝；响应消费者须忽略未知字段以允许向后兼容 | `[已实现]` `fail-on-unknown-properties=true` |
| IR-08 | 分页先采用从 0 开始的 `page/size`；游标契约待压测后另行决定，不在同一路径静默改语义 | `[已实现]` ADR-028 |
| IR-09 | 请求头：`Authorization`（不得放 query）、`X-Request-Id`、`traceparent`、`Idempotency-Key`（流式与上传必填） | `[已实现]` |
| IR-10 | 重试产生新 `requestId`，但沿用同一 `Idempotency-Key` | `[已实现]` |
| IR-11 | 同一语义的错误码不得在不同模块重复编号 | **目标决策** |
| IR-12 | DTO 变更必须搜索并验证 Vue / Spring / Python 三端全部调用方 | **目标决策** |

### 5.2 公共 REST 端点索引

> 字段级细节以 `api.md` 为准，此处仅建立需求索引。

| 分组 | 端点 | 状态 |
| --- | --- | --- |
| 认证 | `POST /auth/register`、`/auth/login`、`/auth/refresh`、`/auth/logout`、`/auth/logout-all` | `[已实现]` |
| 用户 | `GET/PATCH /users/me`、`PUT /users/me/password`、`GET /users/me/permissions` | `[已实现]` |
| 会话 | `POST/GET /conversations`、`GET/PATCH/DELETE /conversations/{id}`、`GET /conversations/{id}/messages` | `[已实现]` |
| 生成 | `POST /conversations/{id}/messages:stream`、`POST /messages/{id}:regenerate`、`POST /messages/{id}:edit-and-resend` | `[已实现]` |
| 停止 | `POST /ai/requests/{requestId}:stop` | `[已实现]` |
| 模型 | `GET /ai/models` | `[已实现]` |
| 文件 | `/files` 全套（上传/列表/用量/详情/重命名/下载/预览/删除） | `[已实现]` |
| 记忆 | `/memories` CRUD + `/enabled` | `[已实现]` |
| 知识库 | `/knowledge-bases`、`/knowledge-bases/{id}/documents`、`/knowledge-documents/{id}`、`/retry`、`/conversations/{id}/knowledge-bases` | `[已实现]` |
| 用量 | `GET /usage`、`GET /usage/breakdown` | `[已实现]` |
| 管理端 | `/admin/**`（见 §3.2） | `[已实现]` |
| 搜索 | 消息搜索 | `[已实现]` 超出原始范围 |
| 导出 | 会话导出 | `[已实现]` 超出原始范围 |

### 5.3 SSE 事件契约

| 事件 | 语义 | 状态 |
| --- | --- | --- |
| `start` | USER 与 Assistant placeholder 已持久化；含 `userMessageId`、`modelId`，分支操作额外含 `operation` | `[已实现]` |
| `delta` | 可见回答增量 | `[已实现]` |
| `reasoning` | 仅转发 Provider 明确提供的 reasoning，**不得人为生成私有思维链** | `[已实现]` |
| `usage` | Provider 上报 token；最终值以服务端持久化为准 | `[已实现]` |
| `done` | 终止事件，`status` ∈ `COMPLETED`/`STOPPED` | `[已实现]` |
| `error` | 终止事件，含 `code`/`message`/`retryable`/`partial`；不含上游 secret/stack | `[已实现]` |
| `citation` | RAG 引用元数据 | `[部分]` 契约已贯穿，本阶段不宣称模型已产生精确 citation 事件 |
| `tool_start` / `tool_delta` / `tool_done` | 未来工具调用 | `[未实现]` 仅保留类型 |

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| IR-13 | `error` 与 `done` 是一次流中唯一且互斥的终止事件；**连接关闭本身不等于成功** | `[已实现]` |
| IR-14 | 不得为每个 delta 写数据库；内存累计 + 终态一次持久化 | `[已实现]` ADR-033 |
| IR-15 | Spring 必须先提交终态事务（消息 / request log / conversation），**再**发送 `done` | `[已实现]` |
| IR-16 | 响应头已提交后的失败只能使用 SSE `error` 终止事件，不能再声称返回 HTTP 4xx/5xx | `[已实现]` |
| IR-17 | Spring 检测到浏览器断开后必须传播取消；即使取消 Provider 失败，也不得继续向已断开客户端写数据 | `[已实现]` |
| IR-18 | 建立流之前的认证 / 校验 / 额度 / 上游连接失败使用普通 HTTP 错误 | `[已实现]` |
| IR-19 | MVP 不承诺 POST SSE 透明续传；客户端通过消息历史读取最终事实 | `[已实现]` 有意边界 |
| IR-20 | 重复 `Idempotency-Key` 返回 `40009 IDEMPOTENCY_CONFLICT`，不重复创建消息；**尚不支持流重放** | `[部分]` 有意边界 |

### 5.4 Spring → Python 内部契约

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| IR-21 | Python 仅内网可达，要求服务身份认证 | `[已实现]` ADR-031；**生产建议升级 mTLS/workload identity** `[未实现]` |
| IR-22 | 不转发用户 Access/Refresh Token | `[已实现]` |
| IR-23 | 需要文件时使用短时、单对象、只读能力；Python 不直连业务 MySQL | `[已实现]`（RAG 走 Spring 中转） |
| IR-24 | 请求携带 `schemaVersion` / `aiRequestId` / `providerKey` / `model` / `messages` | `[已实现]` |
| IR-25 | Spring 严格校验事件名、根级 `type`、`aiRequestId` 与从 0 开始的连续 `seq` | `[已实现]` |
| IR-26 | `messages` 构造有硬边界：候选数、Top-K、Memory Token、Recent Token、祖先条数 | `[已实现]` |
| IR-27 | Spring ↔ Python 使用 consumer/provider contract tests，覆盖成功、终止、超时、乱序、未知事件、断线、错误脱敏 | `[部分]` 存在 `JdkHttpAiGatewayTests.java` 与 Python 侧 `test_chat_service.py`；**完整契约矩阵本次未核验** `[待验证]` |

---

## 6. 非功能需求

### 6.1 安全（NFR-S）

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| NFR-S-01 | 必须防御：IDOR、Broken Access Control、XSS、Markdown XSS、SQL 注入、Path Traversal、非法上传、暴力登录、Token 泄漏、API Key 泄漏、管理员越权、封禁绕过、SSE 未鉴权、RAG/Memory/文件越权 | `[已实现]` 逐项有对应机制与测试（见 §8.3） |
| NFR-S-02 | 所有资源访问必须校验 ownership；禁止只写 `WHERE id = ?` | `[已实现]` ADR-027 |
| NFR-S-03 | Refresh Cookie：`Secure; HttpOnly; SameSite=Lax; Path=/api/v1/auth`；本地可关 `Secure`，生产必须开启 | `[已实现]` |
| NFR-S-04 | `/auth/refresh` 与 `/auth/logout` 必须携带 `X-CSRF-Guard: 1` | `[已实现]` `auth/security/RefreshCsrfGuardFilter.java` |
| NFR-S-05 | 默认同源部署，不开放 CORS | `[已实现]` |
| NFR-S-06 | 客户端 IP 默认只取直连地址；仅当直连 peer 命中 `trusted-proxies`（精确地址或 IPv4 CIDR）才取 `X-Forwarded-For` 最左跳 | `[已实现]` ADR-052 |
| NFR-S-07 | 限流覆盖：登录、注册、刷新、上传 | `[已实现]` |
| NFR-S-08 | Markdown 双重安全边界：禁用原始 HTML + 渲染后消毒 | `[已实现]` ADR-039 |
| NFR-S-09 | Provider 密钥只存引用；数据库与审计均不保存明文 | `[已实现]` ADR-016/061 |
| NFR-S-10 | 密码只存强哈希（Argon2id 或兼容自适应算法），绝不存可逆密码 | `[部分]` `PasswordEncoder` 已用；**具体算法本次未核验** `[待验证]` |
| NFR-S-11 | 日志禁令：密码、JWT、Refresh Token、API Key、Cookie、Authorization、默认完整 Prompt | `[已实现]` |
| NFR-S-12 | 内部 Python API 不得成为绕过 Spring 的公开免费 AI 入口 | `[已实现]` fail-closed 令牌 |
| NFR-S-13 | 管理员可配置的 Provider `base_url` 必须 HTTPS allowlist、URL 规范化、DNS/IP 重绑定防护、禁止 loopback/link-local/metadata/内网管理地址 | `[未实现]` `architecture.md` §8 列为要求；**当前无此校验的证据**。风险：SSRF |
| NFR-S-14 | 用户注销/删除时，业务行、对象、向量、缓存、日志、备份按保留政策分别处理；不得产生孤儿数据 | `[部分]` 有意边界：异步重试/清理任务尚未实现 |

### 6.2 性能与容量（NFR-P）

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| NFR-P-01 | 禁止 N+1 查询、无分页查询、`SELECT *`、不合理 JOIN | `[待验证]` 本次未做查询审计 |
| NFR-P-02 | 子表冗余 `user_id` + 复合外键避免跨用户脏关联扫描 | `[已实现]` |
| NFR-P-03 | 关键路径索引：`user_id`、`conversation_id`、`status`、`created_at`、`updated_at` | `[已实现]` |
| NFR-P-04 | 不得盲目添加无用索引 | **目标决策** |
| NFR-P-05 | 单文件默认上限 25 MiB；用户默认额度 1 GiB；均由服务端配置决定，**不能以前端值作为安全边界** | `[已实现]` |
| NFR-P-06 | multipart/request 限制必须与业务读取限制一致或更严格 | `[已实现]` |
| NFR-P-07 | 流式期间不得持有数据库长事务；外部 LLM/Object/Vector 调用绝不持有长事务 | `[已实现]` |
| NFR-P-08 | 对账任务全表扫描 `ai_usage_account` 属已知 P1 技术债；数据量验证后改分片/游标扫描 | `[部分]` 已知债 |
| NFR-P-09 | 首字响应（TTFT）延迟已被专项诊断与修复 | `[已实现]` `progress.md` 专项记录 |

### 6.3 可靠性（NFR-R）

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| NFR-R-01 | 带 `version` 的聚合根用乐观锁；状态更新用 `WHERE id=? AND status=?` 防重复终止 | `[已实现]` |
| NFR-R-02 | 终态竞争使用 compare-and-set：停止不得覆盖已成功完成，迟到的 `done` 不得覆盖已停止 | `[已实现]` |
| NFR-R-03 | 定时恢复任务扫描超时 `STREAMING` 消息、`STARTED/STREAMING` 请求、未决 reservation，收敛到唯一终态 | `[已实现]` |
| NFR-R-04 | 额度三段式：先 reserve，结束 settle，停止/失败 release；悬挂 reservation 由启动恢复扫描释放 | `[已实现]` ADR-051 |
| NFR-R-05 | 周期对账只报告漂移，**不自动改写**余额 | `[已实现]` ADR-053 |
| NFR-R-06 | 摘要失败、Memory 提取失败、RAG 检索失败均不得导致主 Chat 失败（失败开放） | `[已实现]` |
| NFR-R-07 | 必须能明确分析：MySQL 失败、Redis 失败、Python 失败、Provider 429/超时/500、用户中断 SSE、浏览器刷新、RAG 解析失败、Embedding 失败、Vector DB 失败、磁盘不足 | `[部分]` 代码有对应状态机；**故障演练本次未执行** `[待验证]` |
| NFR-R-08 | Redis 不可用不得导致主业务失败（Redis 仅承载限流计数） | `[待验证]` 降级策略本次未核验 |

### 6.4 可观测性（NFR-O）

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| NFR-O-01 | Actuator 暴露 `/actuator/health`、`/info` 公开，`/actuator/prometheus` 仅 `ADMIN`/`SUPER_ADMIN` | `[已实现]` ADR-057 |
| NFR-O-02 | 关键计数指标：`auth_login_rate_limited_total`、`auth_register_rate_limited_total`、`auth_refresh_rate_limited_total`、`file_upload_rate_limited_total` | `[已实现]` |
| NFR-O-03 | 对账指标：`ai_usage_reconcile_duration_seconds`、`ai_usage_reconcile_released_total`、`ai_usage_reconcile_drift_total` | `[已实现]` |
| NFR-O-04 | 日志允许字段：`request_id`/`user_id`/`conversation_id`/`provider`/`model`/`latency`/`token`/`status`/`error_code` | `[已实现]` |
| NFR-O-05 | 使用 W3C Trace Context（`traceparent`） | `[部分]` 请求头契约已定义；**tracing SDK 实际接入本次未核验** `[待验证]` |
| NFR-O-06 | 后端只产指标，不内置告警；告警由 Grafana/Prometheus 侧配置 | `[已实现]` 有意边界 |
| NFR-O-07 | 日志格式统一携带 `requestId` / `traceId` | `[已实现]` `logging.pattern.console` |

### 6.5 可维护性与工程质量（NFR-M）

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| NFR-M-01 | 禁止临时代码长期保留、核心逻辑 TODO、Mock 冒充正式实现、魔法数字、重复 DTO/工具类/API、复制粘贴 Service、超大函数/组件/Service | `[部分]` 见 §9.4 残留清单 |
| NFR-M-02 | 推荐 Enum / Constants / Configuration Properties / Factory / Strategy / Adapter，但不为设计模式而设计模式 | `[已实现]` `config/` 下 12 个 `*Properties` 类 |
| NFR-M-03 | 采用「阶段式开发 + 小任务提交」，禁止一次性实现整个系统 | **目标决策** |
| NFR-M-04 | 每任务遵循：阅读 → 分析 → 修改 → 编译 → 测试 → 修复 → 文档记录 | **目标决策** |
| NFR-M-05 | 编码前须输出：当前代码情况 / 本次目标 / 准备修改文件 / 数据库影响 / API 影响 / 数据流 | **目标决策** |
| NFR-M-06 | 完成后须输出：本次完成 / 修改文件 / 新增文件 / 删除文件 / API 变化 / 数据库变化 / 配置变化 / 测试结果 / 已知问题 / 下一步建议 | **目标决策** |
| NFR-M-07 | 测试失败先判定归属（本次引入 / 历史问题 / 环境问题）；属本次引入必须修复 | **目标决策** |
| NFR-M-08 | **禁止**删除测试、注释测试、降低断言、`catch Exception` 吞错来制造「测试通过」 | **目标决策** |
| NFR-M-09 | 阶段完成后更新 `progress.md`；不得删除历史重要决策，只能补充或注明废弃原因 | **目标决策** |
| NFR-M-10 | 每阶段完成后需独立验收（Review），不得只信 `progress.md` | **目标决策** |
| NFR-M-11 | 根目录含有调试产物，跨三端统一变更审计受仓库策略限制（仅 `stardust_vue` 是独立 Git 仓库） | `[待确认]` P2 技术债 |

### 6.6 配置与密钥（NFR-C）

| 编号 | 需求 | 状态 |
| --- | --- | --- |
| NFR-C-01 | 禁止把密码 / JWT Secret / Refresh Token / LLM API Key / 数据库密码 / Redis 密码写入 Git 管理代码 | `[已实现]` |
| NFR-C-02 | 三端均须提供 `.env.example` 且**不得**填写真实 Secret | `[已实现]` `stardust_springboot/.env.example`、`stardust_ai/.env.example` |
| NFR-C-03 | Spring 侧配置全部走环境变量占位符 | `[已实现]` `application.properties` 全量 `${ENV:default}` |
| NFR-C-04 | Python 侧配置统一 `STARDUST_AI_` 前缀 + pydantic-settings | `[已实现]` |
| NFR-C-05 | 密钥类字段使用 `SecretStr` 防止意外打印 | `[已实现]` Python `settings.py` |
| NFR-C-06 | `.env.example` 必须与 `application.properties` 的键名保持同步 | `[待确认]` 存在少量键在 `application.properties` 有默认值但未列入 `.env.example`（如 `AI_RAG_PROVIDER_KEY`、`AI_ATTACHMENT_*`、`AI_CONTEXT_*` 部分项），建议逐项对齐 |

---

## 7. 约束、依赖与假设

### 7.1 技术栈约束（当前事实）

| 层 | 事实 | 证据 |
| --- | --- | --- |
| Vue | Vue 3（声明 `^3.2.13`）、**Vue CLI 5 + Webpack（无 Vite）**、TypeScript 4.5.5 `strict`、Vue Router 4.1.6、**Vuex 4.1.0（无 Pinia）**、**无 UI 组件库**、统一 fetch API 层、markdown-it + DOMPurify + highlight.js + KaTeX、SCSS、ESLint 7 + Prettier、Vitest 2.1.9 | `stardust_vue/package.json`、`architecture.md` §2.1 |
| Spring | Java 21、Spring Boot 4.1.1、Maven、Spring MVC、Spring Data JPA、MySQL、Flyway、Spring Security + Nimbus JOSE、Spring Data Redis + Lettuce、Actuator + Micrometer/Prometheus、PDFBox 3.0.4；测试 H2（MySQL mode）+ Testcontainers 1.21.1 | `stardust_springboot/pom.xml` |
| Python | `>=3.12,<3.15`、FastAPI 0.141.1、Uvicorn 0.52.4、Pydantic 2.13.5、pydantic-settings 2.15.0、httpx 0.28.1；测试 pytest 9.1.1 + pytest-asyncio 1.4.0、Ruff 0.16.6；Poetry Core 构建 | `stardust_ai/pyproject.toml` |

### 7.2 环境与启动依赖

| 依赖 | 用途 | 状态 |
| --- | --- | --- |
| MySQL 8.x | 业务事实源（`DB_URL` / `DB_USERNAME` / `DB_PASSWORD`） | 必需 |
| Redis | 仅 `RATE_LIMIT_STORE=redis` 时需要；默认 `memory` | 可选 |
| Python AI 服务 | `AI_SERVICE_BASE_URL` 默认 `http://127.0.0.1:8000`，`AI_SERVICE_TOKEN` 两侧必须一致 | 必需 |
| LLM Provider | Python 侧 `STARDUST_AI_OPENAI_COMPATIBLE_*` | 必需 |
| 本地存储目录 | `STORAGE_LOCAL_ROOT` 默认 `./data/files` | 必需 |
| 向量存储 | Python 侧 `rag_vector_store_path` 默认 `./data/rag-vectors.sqlite3` | 必需 |

端口约定：Spring `8081`；Python 默认 `8000`。

### 7.3 假设与待确认项

| 编号 | 内容 | 影响 | 处置建议 |
| --- | --- | --- | --- |
| AS-01 | `spring.flyway.baseline-version=9` 与 `baseline-on-migrate=true` 同时存在。若在**非空且无 `flyway_schema_history`** 的旧库上触发 baseline，V1–V9 会被标记为基线而**不执行** | 高：可能导致 schema 不完整 | **需 Owner 确认该配置的既定用途**；本次未执行 Flyway 验证，标记 `[待验证]` |
| AS-02 | `stardust_vue/package.json` 的 `devDependencies` 含 `@vitejs/plugin-vue`，但构建脚本为 `vue-cli-service build`，`architecture.md` 记录「没有 Vite」 | 低：依赖噪音 | 确认后移除未使用依赖（ADR-013 的 Vite 迁移尚未执行） |
| AS-03 | 三处枚举文档不一致 | 中：阅读者可能误用 | 按 §4.4 对齐文档 |
| AS-04 | `.env.example` 与 `application.properties` 键名未完全对齐 | 低 | 逐项补齐 |
| AS-05 | Provider `base_url` 的 SSRF 防护（NFR-S-13）仅见于文档要求，无实现证据 | 高：安全 | 列入下一阶段安全加固 |
| AS-06 | 根目录存在调试产物（`build.log`、`msg_stream.txt`、`python_run*.log`、`spring_run*.log`、`spring_stderr.log`、`spring_stdout.log`、`stream_out.txt`、`verify_stream.ps1`、`export_test.ps1`） | 低：仓库整洁 | 建议归档或加入忽略清单（**未经确认不删除**） |
| AS-07 | README 中的「阶段 11A–11F」任务书在仓库中**已全部完成**，README 措辞仍为「接下来再做」 | 低：误导后续 Agent | 建议 README 增加「当前进度指针」指向 `progress.md` |

---

## 8. 验收标准

### 8.1 通用「完成判定」（Definition of Done）

> 该判定直接来自需求基线（README 中的完成判定条款），适用于任何一个功能任务。

只有**同时**满足以下条件，任务才算完成：

1. 前端页面已经可以真实操作；
2. Vue 已调用真实 Spring Boot API；
3. Spring Boot API 已有真实业务实现；
4. 数据库真实发生查询或变更；
5. 权限由 Spring Security 后端校验；
6. 涉及敏感管理员操作已写审计；
7. 不存在 Mock 数据；
8. 不存在占位页面；
9. 不存在「后续再实现」的核心 TODO；
10. Vue `build` / `typecheck` 通过；
11. Spring Boot `test` / `package` 通过。

**任意一项未完成，不得宣称任务完成。**

### 8.2 阶段验收流程

1. 不开发新功能，只做 Review 与验收。
2. **扫描实际代码，不只相信 `progress.md`**。
3. 检查 15 项：是否真实现 / 是否有 TODO 冒充 / API 三端一致 / migration 完整 / DTO 字段一致 / 权限 / ownership / 是否重复 Service·Utils·API / 是否巨型类 / 异常处理遗漏 / Secret 硬编码 / 敏感日志 / 测试有效性 / build 通过 / 是否越阶段实现。
4. 发现问题**直接修复**，不只给建议。
5. 修复后重跑该阶段可执行命令。
6. 输出验收结论：`PASS` 或 `FAIL`，并说明已修复项、仍存问题、是否允许进入下一阶段。
7. 若存在影响下一阶段的严重问题，结论必须为 `FAIL`。

### 8.3 测试矩阵（当前事实）

**Spring Boot**（`src/test/java/**`，共 31 个测试类）

| 领域 | 测试类 |
| --- | --- |
| 应用/持久化/公共 | `StardustSpringbootApplicationTests`、`PersistenceInfrastructureTests`、`CommonInfrastructureTests`、`PublicIdGeneratorTests`、`RequestIdFilterTests` |
| 认证 | `AuthenticationIntegrationTests`、`JwtTokenServiceTests`、`LoginRateLimitTests` |
| 会话与消息 | `ConversationIntegrationTests`、`ConversationShortMemoryIntegrationTests` |
| 流式与 AI | `AiStreamingIntegrationTests`、`JdkHttpAiGatewayTests`、`ChatAttachmentIntegrationTests`、`AiCatalogJsonCodecTests` |
| 文件 | `UserFileIntegrationTests` |
| 记忆 | `MemoryIntegrationTests` |
| 知识库 | `KnowledgeRagIntegrationTests` |
| 用量 | `AiUsageIntegrationTests`、`AiUsageReconciliationTests`、`AdminUsageAdjustTests` |
| 管理端 | `AdminIntegrationTests`、`AdminUserManagementTests`、`AdminConversationManagementTests`、`AdminFileManagementTests`、`AdminKnowledgeManagementTests`、`AdminAiCatalogTests`、`AdminDashboardAndLogsTests`、`AdminCrudConsistencyTests` |
| 限流 | `EndpointRateGuardTests`、`RedisRateLimiterTests` |

**Vue**（Vitest）

`sseParser.spec.ts`、`ChatComposer.spec.ts`、`ChatAttachment.spec.ts`、`conversations.spec.ts`

**Python**（pytest，`testpaths = ["tests"]`）

`test_openai_compatible_provider.py`、`test_chat_service.py`、`test_endpoints.py`、`test_rag_service.py`、`test_sqlite_vector_store.py`、`test_attachments.py`（另有 `fakes.py`）

**各端点可执行命令**

| 端 | 命令 |
| --- | --- |
| Vue | `npm run typecheck`、`npm run lint`、`npm run build`、`npm run test` |
| Spring Boot | `mvn test`、`mvn package` |
| Python | `pytest`、`ruff check` |

> 本次梳理**未执行**上述任何命令，因此所有构建/测试状态均为 `[待验证]`。`progress.md` 中记录的历史绿灯结论不构成本次验证结论。

---

## 9. 需求实现状态总表与遗留项

### 9.1 已实现（有代码证据）

阶段 0–13 全部落地，含 11A–11F 六个管理端子阶段、11G/11H 排错、前端 ChatGPT 风格重构与中文化、TTFT 专项修复。

### 9.2 部分实现 / 有意边界

| 项 | 边界说明 | 依据 |
| --- | --- | --- |
| 图片 AI 理解 | 仅引用与预览，推理未实现 | FR-U-36 |
| citations | 契约贯穿，未宣称模型产生精确 citation 事件 | §5.3 |
| Reranker | 未实现，仅预留 | AB-07 |
| 向量数据库 | SQLite adapter，生产选型未定 | AB-03、ADR-048 |
| 跨节点 Stop | 单实例内存注册表 | ADR-035、P1 债 |
| SSE 流重放 | 重复 Idempotency-Key 返回冲突，不重放 | IR-20 |
| 异步清理/重试 | 文件与向量删除的异步重试未实现 | NFR-S-14 |
| 对账扫描 | 全表扫描，未分片 | NFR-P-08 |
| 内部认证 | 共享令牌，未上 mTLS/workload identity | IR-21 |
| 浏览器 E2E | 仅一次性冒烟，无 CI 套件 | `architecture.md` §3 P1 |
| 密码哈希算法 | 用了 `PasswordEncoder`，具体算法未核验 | NFR-S-10 `[待验证]` |

### 9.3 未实现（候选下一阶段）

| 项 | 说明 |
| --- | --- |
| 工具调用 / Function Calling / MCP 实际执行 | 仅预留 SSE 事件类型 |
| 图片生成、语音、联网搜索 | 无代码证据 |
| 组织 / 租户多层级 | 明确排除 |
| Provider `base_url` SSRF 防护（NFR-S-13） | **建议优先**（安全） |
| 病毒扫描 / CDR | 明确属生产加固 |
| 细粒度 `permission` / `role_permission` 表 | ADR-025 延后 |

### 9.4 残留清理建议（**建议清理，未经确认不执行删除**）

| 文件 | 观察 | 建议 |
| --- | --- | --- |
| `stardust_springboot/src/main/java/ChatAttachment.java` | 位于包外，内容为空 | 确认为误建后删除 |
| `stardust_vue/src/views/AdminView.vue` | 内容为空；正式路由使用 `views/admin/AdminLayoutView.vue` | 确认为遗留后删除 |
| `stardust_vue/src/debug.spec.ts` | 内容为空 | 确认为遗留后删除 |
| `stardust_vue/src/components/HelloWorld.vue` | 脚手架组件（文案已改为星语 AI），无路由引用 | 确认无引用后删除 |
| 根目录调试产物 | 见 AS-06 | 归档或加入忽略清单 |

> 上述文件**本次仅做只读识别**，未做任何移动、重命名或删除操作。

---

## 10. 需求追溯与变更管理

### 10.1 追溯链路

本项目坚持如下逆向闭环，任一结论都必须可沿链路回溯：

```text
README 需求/任务书
        ↓
本文件需求编号（FR-*/NFR-*/DR-*/IR-*/TR-*/AB-*）
        ↓
architecture.md / api.md / database.md / decisions.md
        ↓
三端代码（Vue / Spring / Python）
        ↓
测试用例
        ↓
progress.md 阶段记录
```

### 10.2 变更管理规则

| 编号 | 规则 |
| --- | --- |
| CM-01 | 新增需求须先补充到本文件并编号，再进入开发；不得只改代码不更新需求。 |
| CM-02 | 破坏性接口变更进入 `/v2` 或明确 `schemaVersion`，不得静默改语义。 |
| CM-03 | 数据库变更必须走新 Flyway migration，禁止只改 Entity。 |
| CM-04 | DTO 变更必须搜索并验证三端调用方。 |
| CM-05 | 架构决策只能追加 ADR；历史 ADR 不得删除，废弃须注明原因。 |
| CM-06 | 阶段完成必须更新 `progress.md`，并同步本文件的实现状态列。 |
| CM-07 | 本文件中的 `[待验证]` 项在获得执行证据前，不得升级为 `[已实现]`。 |

### 10.3 本文档的已知不足

1. 未执行任何构建、测试或服务运行，因此所有运行时结论均为 `[待验证]`。
2. 未做类规模度量、「超大函数/组件」判定未核验。
3. 未做查询级性能审计（N+1、索引命中、`EXPLAIN`）。
4. 未逐字段核对三端 DTO 一致性。
5. 未核验 `traceparent` tracing SDK 的实际接入程度。

以上五项建议作为下一轮「阶段验收 / 架构巡检」的输入。

---

_本文件为需求基线首版，随阶段推进持续维护。任何与本文件冲突的实现，应先在 `decisions.md` 中留下决策记录，再回归更新本文件。_
