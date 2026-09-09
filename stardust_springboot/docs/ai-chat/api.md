# AI Chat SaaS API 契约

> 状态：阶段 5 SSE、阶段 6 消息变体、阶段 7 文件、阶段 9 Long-Term Memory、阶段 10 RAG 契约已实现  
> 对外链路：Vue → Spring Boot  
> 内部链路：Spring Boot → Python AI

## 1. 通用规则

- 公共 API 前缀：`/api/v1`；阶段 4 Python 内部 API 前缀：`/internal`。后续破坏性演进使用新路径或 `schemaVersion`，不得静默改语义。
- JSON 字段统一使用 `camelCase`，时间使用带时区的 ISO 8601 UTC 字符串，例如 `2026-09-07T06:00:00Z`。
- 用户拥有的外部资源暴露不透明 `publicId`（建议 ULID）；Role/Permission 等平台字典资源可暴露稳定 `code`。数据库内部自增主键不出现在外部契约。
- HTTP 状态码表达协议结果，业务 `code` 提供稳定机器语义；错误不得一律返回 HTTP 200。
- 创建和更新请求使用 Bean Validation/Pydantic 校验；v1 请求中的未知字段默认拒绝，响应消费者必须忽略未知字段以允许向后兼容扩展。
- 当前 Conversation/Message 列表复用公共 `PageResult`，使用从 0 开始的 `page/size`；引入流式增量和大数据量压测后再决定是否新增不透明游标契约，不在同一路径静默改变分页语义。

## 2. Vue → Spring Boot JSON

### 2.1 成功响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "01K4...",
  "timestamp": "2026-09-07T06:00:00Z"
}
```

`data` 可以是对象、数组或 `null`。删除接口优先返回 `204 No Content`；若需要业务确认则返回上述 envelope。

### 2.2 错误响应

```json
{
  "code": 40101,
  "message": "access token is invalid or expired",
  "data": null,
  "requestId": "01K4...",
  "timestamp": "2026-09-07T06:00:00Z",
  "errors": [
    { "field": "email", "reason": "INVALID_FORMAT" }
  ]
}
```

- `message` 面向人类但不可作为客户端分支依据。
- `errors` 仅用于校验错误，不能回显密钥、SQL、堆栈或内部 Provider 原始响应。
- 生产环境异常详情只进入脱敏服务端日志。

### 2.3 错误码分段

| 范围 | 类别 | 示例 |
| --- | --- | --- |
| `0` | 成功 | `0` |
| `40000-40099` | 请求/校验/幂等 | `40001 VALIDATION_FAILED`、`40004 CURRENT_PASSWORD_INVALID`、`40009 IDEMPOTENCY_CONFLICT`、`40010 CONTEXT_WINDOW_EXCEEDED` |
| `40100-40199` | 认证 | `40101 TOKEN_INVALID`、`40102 TOKEN_EXPIRED`、`40103 REFRESH_REUSED`、`40104 INVALID_CREDENTIALS`、`40105 REFRESH_TOKEN_INVALID` |
| `40300-40399` | 权限/状态/归属 | `40301 FORBIDDEN`、`40302 ACCOUNT_BANNED`、`40303 RESOURCE_NOT_OWNED`、`40304 ACCOUNT_DISABLED`、`40305 CSRF_GUARD_REQUIRED` |
| `40400-40499` | 资源不存在 | `40401 RESOURCE_NOT_FOUND` |
| `40900-40999` | 状态冲突 | `40901 RESOURCE_STATE_CONFLICT`、`40903 EMAIL_ALREADY_EXISTS` |
| `41300-41399` | 上传限制 | `41301 FILE_TOO_LARGE` |
| `41500-41599` | 文件/MIME | `41501 FILE_TYPE_NOT_ALLOWED` |
| `42900-42999` | 限流/额度 | `42901 RATE_LIMITED`、`42902 AI_QUOTA_EXCEEDED`、`42903 STORAGE_QUOTA_EXCEEDED` |
| `50000-50099` | 本服务内部错误 | `50001 INTERNAL_ERROR`、`50002 PERSISTENCE_ERROR`、`50003 STORAGE_ERROR` |
| `50200-50299` | Python/Provider 上游错误 | `50201 AI_SERVICE_UNAVAILABLE`、`50202 PROVIDER_ERROR` |
| `50400-50499` | 超时 | `50401 AI_TIMEOUT` |

错误码常量只能集中维护；同一语义不能在不同模块重复编号。

### 2.4 分页

```json
{
  "items": [],
  "nextCursor": "opaque-or-null",
  "hasMore": false
}
```

游标必须包含稳定排序键，经 base64url 编码并由服务端 HMAC 签名，不能信任客户端传入的用户 ID。聊天默认按 `(lastMessageAt DESC, id DESC)`，消息默认按 `(sequenceNo ASC, id ASC)`。

### 2.5 请求头

| Header | 方向 | 规则 |
| --- | --- | --- |
| `Authorization: Bearer ...` | Vue → Spring | Access Token；不得放 query string |
| `X-Request-Id` | 双向 | 客户端可传合法 ULID/UUID；非法或缺失时服务端生成并回传 |
| `traceparent` | 全链路 | W3C Trace Context；由 tracing SDK 生成/传播 |
| `Idempotency-Key` | 写请求 | 流式生成、上传初始化等重试敏感请求必填 |
| `Content-Type` | 请求 | 请求体为 JSON 时用 `application/json`，上传用 multipart；SSE 由 `Accept` 和响应 `Content-Type` 表达 |

`requestId` 标识一次入口 HTTP 请求，便于用户报错与日志检索；`traceId` 标识跨 Spring/Python/Provider 的完整调用链。两者不可混用。重试会产生新 `requestId`，但沿用同一 `Idempotency-Key`；是否沿用 trace 由 tracing 系统决定。

## 3. 公共 API 资源草案

Auth/User/RBAC 已在阶段 2 实现；Conversation/Message 基础 API 已在阶段 3 实现，其余资源仍只确定边界。

### 3.1 Auth/User/RBAC

| Method | Path | 用途 |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | 注册 |
| `POST` | `/api/v1/auth/login` | 登录 |
| `POST` | `/api/v1/auth/refresh` | Refresh Token rotation |
| `POST` | `/api/v1/auth/logout` | 撤销当前 refresh session |
| `POST` | `/api/v1/auth/logout-all` | 撤销用户全部 refresh session |
| `GET/PATCH` | `/api/v1/users/me` | 当前资料查询/修改 |
| `PUT` | `/api/v1/users/me/password` | 修改密码并按策略撤销会话 |
| `GET` | `/api/v1/users/me/permissions` | 当前用户权限快照 |

阶段 2 已确定并实现以下认证契约：

- Access Token 是 15 分钟短时 HS256 JWT，只通过响应体返回；Vue 仅保存在内存中的 Vuex state/API module，不写 `localStorage`、`sessionStorage` 或普通 Cookie。
- Refresh Token 是 256-bit 随机不透明值，只存于 `HttpOnly` Cookie；数据库只保存带独立 pepper 的 HMAC-SHA256 哈希。
- Refresh Cookie 默认 `Secure; HttpOnly; SameSite=Lax; Path=/api/v1/auth`。本地 HTTP 开发通过配置关闭 `Secure`，生产必须开启。
- `/auth/refresh` 与 `/auth/logout` 除 SameSite Cookie 外必须携带 `X-CSRF-Guard: 1`；部署保持 Vue 与 Spring 同源，跨源请求默认不开放 CORS。
- 每次 refresh 必须 rotation。旧 token 再使用返回 `40103 REFRESH_REUSED` 并撤销同一 family 仍为 ACTIVE 的 token。
- 修改密码递增 `auth_version`、撤销用户全部 Refresh Token，并签发新的 Access/Refresh session；旧 Access Token 在下次请求时立即被服务端拒绝。
- 普通 logout 撤销当前 refresh family，浏览器同时清空内存 Access Token；已签发 Access Token 最长仍有 15 分钟密码学有效期，但退出后的客户端不再持有它。`logout-all` 撤销全部 Refresh Token。
- JWT Filter 每次受保护请求重新读取用户状态和角色，因此 `BANNED`、`DISABLED`、`DELETED` 与角色变更由服务端立即生效。

注册请求：

```json
{"email":"user@example.com","password":"at-least-12-characters","displayName":"Stardust User"}
```

登录请求：

```json
{"email":"user@example.com","password":"current-password"}
```

注册、登录、refresh 和修改密码成功时的 `data`：

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "01K...",
    "email": "user@example.com",
    "displayName": "Stardust User",
    "status": "NORMAL",
    "roles": ["USER"]
  }
}
```

资料修改只接受 `{"displayName":"New name"}`；密码修改接受 `{"currentPassword":"...","newPassword":"..."}`。客户端不能提交用户 ID、状态或角色。

### 3.2 Conversation/Message

| Method | Path | 用途 |
| --- | --- | --- |
| `POST/GET` | `/api/v1/conversations` | 创建/分页查询自己的会话 |
| `GET/PATCH/DELETE` | `/api/v1/conversations/{conversationId}` | 查询、改标题/归档、软删除 |
| `GET` | `/api/v1/conversations/{conversationId}/messages` | 查询当前用户拥有会话的消息 |
| `POST` | `/api/v1/conversations/{conversationId}/messages:stream` | 流式生成 |
| `POST` | `/api/v1/messages/{messageId}:regenerate` | 基于已有 Assistant 创建真实的新变体并流式生成 |
| `POST` | `/api/v1/messages/{messageId}:edit-and-resend` | 基于已有 USER 创建修订变体及对应 Assistant 并流式生成 |
| `POST` | `/api/v1/ai/requests/{requestId}:stop` | 主动停止；owner-scoped、幂等 |
| `GET` | `/api/v1/ai/models` | 返回已启用 Provider 下的 CHAT 模型 |

服务端从认证主体取得 `userId`，请求体不得决定资源归属。返回 404 还是 403 应采用统一的防枚举策略；无论哪种外部表现，内部都要记录真实拒绝原因。

阶段 3 已实现的请求约定：

- `POST /conversations`：`{"title":"optional, max 200"}`；省略或空白时标题为 `New conversation`。
- `GET /conversations`：支持 `page`、`size`、`search`、`status=ACTIVE|ARCHIVED`、`sort=LAST_MESSAGE_AT|CREATED_AT|UPDATED_AT|TITLE`、`direction=ASC|DESC`。默认按 `lastMessageAt DESC, id DESC`。
- `PATCH /conversations/{id}`：接受可选 `title` 和 `status`；至少提供一项。删除只能使用 DELETE，不能提交 `status=DELETED` 绕过删除语义。
- `DELETE /conversations/{id}`：软删除并返回 204；消息事实保留但不再能从普通用户 API 访问。
- `GET /conversations/{id}/messages`：使用 `page/size`，固定按 `sequenceNo ASC, id ASC` 返回。

Conversation 响应至少包含：`id/title/status/lastMessageAt/messageCount/createdAt/updatedAt`。Message 响应至少包含：`id/conversationId/parentMessageId/supersedesMessageId/variantNo/role/content/contentType/modelId/status/promptTokens/completionTokens/totalTokens/errorMessage/sequenceNo/createdAt/attachments`；附件项为 `id/name/mimeType/size/previewable/downloadUrl/previewUrl`。全局 non-null 序列化策略会省略尚无值的可选字段。

阶段 3 的同步 Mock 消息入口与 adapter 已在阶段 5 删除，正式消息生成只通过 SSE orchestrator；测试使用注入的 `AiGateway` fake，不进入生产实现。

所有 Conversation/Message 查询、更新和删除都使用认证主体的内部 `userId` 参与 Repository 条件。访问其他用户或不存在的资源统一返回 `40401 RESOURCE_NOT_FOUND`，避免资源枚举；请求体不接受 `userId`。

### 3.3 File/KB/Memory/Usage/Admin

| 资源 | 示例前缀 | 说明 |
| --- | --- | --- |
| 文件/云盘 | `/api/v1/files` | 上传、列表、下载授权、删除、配额 |
| 知识库 | `/api/v1/knowledge-bases` | KB、文档与 ingestion 状态 |
| 长期记忆 | `/api/v1/memories` | 自己的记忆 CRUD、启停与来源 |
| 模型 | `/api/v1/ai/models` | 只返回用户可用模型及安全参数范围 |
| 使用量 | `/api/v1/usage` | 当前用户 token/cost/quota 统计 |
| 管理端 | `/api/v1/admin/...` | 用户、角色、文件、KB、Provider、Model、日志、审计 |

管理员查看用户聊天、消息、文件或 Memory 的 API 必须校验专门权限并同步写入审计日志；普通 `ADMIN` 角色名本身不能替代细粒度 permission。

### 3.4 Long-Term Memory（阶段 9 实现）

所有端点要求 Bearer Access Token。Repository 查询始终带当前主体内部 `userId`；其他用户资源与不存在资源统一返回 `40401`。

| Method | Path | 请求/响应 |
| --- | --- | --- |
| `GET` | `/api/v1/memories?page=0&size=20&search=...&enabled=true&type=PROJECT` | size 1..100；分页搜索/筛选，按 `updatedAt DESC, id DESC` |
| `GET` | `/api/v1/memories/{memoryId}` | 当前用户 Memory 详情 |
| `POST` | `/api/v1/memories` | `content/summary/memoryType/importance/enabled?`；HTTP 201 |
| `PATCH` | `/api/v1/memories/{memoryId}` | 可选更新 `content/summary/memoryType/importance` |
| `PATCH` | `/api/v1/memories/{memoryId}/enabled` | `{"enabled":true\|false}` |
| `DELETE` | `/api/v1/memories/{memoryId}` | 软删除并禁用；HTTP 204 |

`MemoryView`：`id/content/summary/memoryType/importance/enabled/origin/sourceConversationId/sourceMessageId/createdAt/updatedAt`。类型为 `PREFERENCE/PROJECT/GOAL/EXPLICIT`，来源为 `MANUAL/AUTO`。自动提取只在成功 Chat 后识别稳定偏好、长期项目/目标和明确“记住”请求；敏感模式或不确定内容不自动保存，失败不改变 Chat 的成功终态。

检索不是公共 API。Spring `MemoryRetriever` 仅查询 owner-scoped、enabled、not-deleted 的固定上限候选，结合 query relevance 与 importance 选择 Top-K，并受独立 Token Budget 约束后交给 `ContextBuilder`。禁用项、无关项与超预算项不会进入 Prompt。

### 3.5 用户文件与云盘（阶段 7 实现）

所有端点要求 Bearer Access Token，且只在 Repository 层按当前主体内部 `userId` 查询。其他用户资源与不存在资源均返回 `40401`。

| Method | Path | 请求/响应 |
| --- | --- | --- |
| `POST` | `/api/v1/files` | `multipart/form-data`，字段 `file`；成功 HTTP 201 + `ApiResult<FileView>` |
| `GET` | `/api/v1/files?page=0&size=20&search=...` | `size` 1..100，按 `createdAt DESC, id DESC` 返回 `PageResult<FileView>` |
| `GET` | `/api/v1/files/usage` | `usedBytes/reservedBytes/quotaBytes/fileCount` |
| `GET` | `/api/v1/files/{fileId}` | 文件详情，不返回 `objectKey/storageName` |
| `PATCH` | `/api/v1/files/{fileId}` | `{"name":"新名称.pdf"}`；不得改变扩展名 |
| `GET` | `/api/v1/files/{fileId}/download` | attachment 二进制响应 |
| `GET` | `/api/v1/files/{fileId}/preview` | 仅检测为图片的文件，inline 二进制响应 |
| `DELETE` | `/api/v1/files/{fileId}` | 成功 HTTP 204；存在聊天引用时返回 `40901` |

`FileView` 为：`id/name/mimeType/detectedMimeType/extension/size/sha256/storageProvider/status/previewable/downloadUrl/previewUrl/createdAt/updatedAt`。下载与预览响应固定包含 `X-Content-Type-Options: nosniff`、受限 CSP 和 `Cache-Control: private, no-store`。

首版单文件上限默认 25 MiB、用户默认额度 1 GiB，均由服务端环境配置决定，不能以前端值作为安全边界。允许类型采用扩展名 + 声明 MIME + magic/text 验证；超限、类型拒绝与配额不足分别使用 `41301/41501/42903`。

配置项：`STORAGE_PROVIDER`（默认 `local`）、`STORAGE_LOCAL_ROOT`、`STORAGE_MAX_FILE_SIZE`、`STORAGE_MAX_REQUEST_SIZE`、`STORAGE_MAX_FILE_BYTES`、`STORAGE_DEFAULT_QUOTA_BYTES`。multipart/request 限制与业务读取限制必须保持一致或更严格。

## 4. Vue → Spring Boot SSE

### 4.1 请求

浏览器使用支持 POST 与 `Authorization` header 的 `fetch` 流解析器，不使用只能 GET 的原生 `EventSource`。

```http
POST /api/v1/conversations/01K.../messages:stream HTTP/1.1
Authorization: Bearer <access-token>
Content-Type: application/json
Accept: text/event-stream, application/json
Idempotency-Key: 01K...
```

```json
{
  "content": "用户输入",
  "contentType": "PLAIN_TEXT",
  "modelId": "01K...",
  "parentMessageId": "optional message public id",
  "attachmentIds": ["optional user_file public id"]
}
```

`content` 最大 32000 字符；`contentType` 省略时为 `PLAIN_TEXT`；`modelId` 必填且必须是已启用 Provider 下的 CHAT 模型。`attachmentIds` 最多 10 个、必须互异，服务端要求每个文件属于当前用户且状态为 `AVAILABLE`。附件只写 `chat_message_attachment`，不重复上传对象，阶段 7 不读取文件内容或转发给 Python/LLM。请求仍不接受采样参数、Provider URL、API Key、系统提示词或任意工具定义。

阶段 6 分支操作同样返回 `text/event-stream` 并要求 `Idempotency-Key`：

- `POST /api/v1/messages/{assistantMessageId}:regenerate`，body 为 `{"modelId":"01K..."}`。目标必须是当前用户拥有、已终止的 Assistant；服务端复用其 USER parent，创建同一 `sequenceNo` 的新 `variantNo`，并设置 `supersedesMessageId`。
- `POST /api/v1/messages/{userMessageId}:edit-and-resend`，body 可额外带 `attachmentIds`。目标必须是当前用户拥有且已完成的 USER；服务端创建 USER 修订变体及其附件关联，再创建下一 sequence 的 Assistant 变体。
- Regenerate 不接受新的附件参数，复用被重新生成 Assistant 的 USER parent 及其既有附件。
- 两个入口与普通发送执行完全相同的认证、用户状态、ownership、模型校验、request log、取消与终态持久化。`start` 额外返回 `operation=SEND|REGENERATE|EDIT_AND_RESEND`；Vue 根据真实 `parentMessageId/supersedesMessageId/variantNo` 选择活动分支，刷新后以数据库为准。

### 4.2 传输格式

```text
event: delta
data: {"type":"delta","requestId":"01K...","conversationId":"01K...","messageId":"01K...","content":"你好"}

```

- 每个 `data` 是单行 JSON；以空行结束事件。
- `messageId` 在 `start` 时已存在，证明 Assistant 占位消息已持久化。
- `error` 和 `done` 是终止事件，一次流最多出现一个；连接关闭本身不等于成功。
- public event 使用扁平字段以降低 Vue 增量处理复杂度；严格 `seq` 校验保留在 Spring → Python 内部边界。

### 4.3 事件目录

| event | data 最小字段（均含 `type/requestId/conversationId/messageId`） | 语义 |
| --- | --- | --- |
| `start` | `userMessageId`, `modelId` | USER 与 Assistant placeholder 已持久化并开始生成 |
| `delta` | `content` | 可见回答增量 |
| `reasoning` | `content` | 仅转发 Provider 明确提供的 reasoning 字段；不得人为生成私有思维链 |
| `usage` | `promptTokens`, `completionTokens`, `totalTokens` | Provider usage；最终值以服务端持久化为准 |
| `citation` | `citationId`, `documentId`, `chunkId`, `label` | RAG 引用元数据，不发送任意可执行 HTML |
| `tool_start` | `toolCallId`, `name` | 未来工具调用开始 |
| `tool_delta` | `toolCallId`, `delta` | 未来工具调用参数增量 |
| `tool_done` | `toolCallId`, `status` | 未来工具调用结束 |
| `done` | `status`, `finishReason` | 正常/主动停止终态；对应终态事务已提交 |
| `error` | `code`, `message`, `retryable`, `partial` | 失败终止；partial 表示已经保存部分回答，不包含上游 secret/stack |

`finishReason` 建议枚举：`STOP`、`LENGTH`、`USER_CANCELLED`、`CONTENT_FILTER`、`TOOL_CALL`。消息持久化状态与 Provider finish reason 分开。

`done.status` 当前为 `COMPLETED` 或 `STOPPED`，与消息/request log 的终态枚举一致。失败使用 `error` event，数据库状态为 `FAILED`。

### 4.4 断线、取消与重试

- MVP 不承诺在 POST SSE 上透明续传；客户端通过消息历史读取最终事实。
- 客户端断线后先查询 Assistant 消息/AI request 状态，不直接创建新请求。
- `(userId, Idempotency-Key)` 有数据库唯一约束；当前重复提交返回 `IDEMPOTENCY_CONFLICT`，不会重复创建消息，但尚不支持流重放。
- stop API 是幂等的；已终止请求返回当前最终状态。
- Spring 检测浏览器断开后传播取消；即使无法及时取消 Provider，也不得继续向已断开的客户端写数据，最终 usage 仍按实际 Provider 结果结算。
- Spring 必须先提交 Assistant、AI request log 和 conversation 的终态事务，再发送 `done`。usage ledger 尚未进入阶段 5。
- 建立流之前的认证、校验、额度和上游连接失败使用普通 HTTP 错误；响应头已提交后的失败只能使用 SSE `error` 终止事件，不能再声称返回 HTTP 4xx/5xx。

## 5. Spring Boot → Python AI

### 5.1 信任边界

- Python API 仅内网可达，要求服务身份认证（生产 mTLS/workload identity；本地可轮换 service token）。
- 不转发用户 Access/Refresh Token；Python 不建立用户 session，不自行做 RBAC。
- Spring 只传 AI 计算所需的最小上下文。日志中不得记录完整 Prompt、Authorization 或 Provider 密钥。
- Python 不直连业务 MySQL。需要文件时使用短时、单对象、只读签名 URL 或受认证的内部下载接口。

### 5.2 健康与能力

| Method | Path | 返回 |
| --- | --- | --- |
| `GET` | `/health` | 进程、配置就绪状态和已注册 Provider key；不调用收费的远端模型 |
| `POST` | `/internal/chat` | 完整非流式 Provider 响应 |
| `POST` | `/internal/chat/stream` | 标准化 internal SSE |

`/health` 无需服务令牌以供容器探针使用，但 Python 服务仍只应绑定内网接口。`ready=true` 表示内部令牌和默认模型已配置，不代表每次远端 Provider 调用一定成功。

### 5.3 Chat Streaming 请求

```http
POST /internal/chat/stream
Accept: text/event-stream
Content-Type: application/json
X-Service-Authorization: <service-credential>
X-Request-Id: 01K...
traceparent: 00-...-...-01
```

```json
{
  "schemaVersion": "1",
  "aiRequestId": "01K...",
  "providerKey": "openai-compatible",
  "model": "model-name",
  "messages": [
    { "role": "system", "content": "..." },
    { "role": "user", "content": "..." }
  ]
}
```

`providerKey` 只能选择 Python Registry 已注册的服务端配置；请求不得携带 `baseUrl`、API Key 或 `credentialRef`。`model` 可省略并使用环境变量中的默认模型。当前角色只允许 `system/user/assistant/tool`，未知字段由 Pydantic `extra=forbid` 拒绝。

阶段 9 不改变 internal JSON/SSE schema，但扩展 `messages` 的构造保证：Spring 按 `ai_model.context_window`（为空则取配置默认值）扣除 output/safety reserve，发送 System Prompt、可选且有界的“不可信长期记忆”system message、可选的“不可信短期摘要”system message、当前分支 recent messages 和恰好一次 current USER message。不会读取并传送全部历史或全部 Memory；候选数、Top-K、Memory Token、Recent Token 和祖先条数均有硬边界。单条 current USER 已超过输入预算时，在建立 SSE 前返回普通 `40010 CONTEXT_WINDOW_EXCEEDED`。

`POST /internal/chat` 返回 `schemaVersion/aiRequestId/requestId/providerKey/model/message/finishReason/usage`。Provider 超时、网络不可用、限流、请求拒绝和非法响应分别映射为稳定内部错误码，响应和日志不透传 Provider body。

### 5.4 Python SSE

阶段 5 Python 实现 `start/delta/reasoning/usage/done/error`；每个事件包含根级 `type/schemaVersion/aiRequestId/requestId/seq/timestamp` 与 `payload`。Spring 严格校验事件名、根级 `type`、`aiRequestId` 和从 0 开始的连续 `seq`，补全外部 `conversationId/messageId` 并做错误脱敏。`citation/tool_start/tool_delta/tool_done` 已作为保留类型贯穿契约，但本阶段不产生这些事件。

Python 终止错误示例：

```text
event: error
data: {"schemaVersion":"1","type":"error","aiRequestId":"01K...","requestId":"01K...","seq":18,"timestamp":"2026-09-07T06:00:05Z","payload":{"code":"PROVIDER_TIMEOUT","message":"provider request timed out","retryable":true}}

```

内部错误码使用稳定字符串。进入 worker 前的 Spring 校验失败使用普通 `ApiResult` HTTP 错误；流建立后的 Provider/Python/Spring 失败统一映射为脱敏的 public SSE `error.code`（例如 `AI_TIMEOUT`、`PROVIDER_RATE_LIMITED`、`AI_SERVICE_UNAVAILABLE`）并关闭流。

### 5.5 Stop 响应

`POST /api/v1/ai/requests/{requestId}:stop` 返回 HTTP 202 和普通 `ApiResult`，`data={"requestId":"...","status":"STOPPED"}`；若 worker 尚未在 2 秒内完成终态提交，可返回当时状态。重复 Stop 返回数据库中的当前状态，其他用户或不存在的 request 统一 404。

## 6. Knowledge Base / RAG 契约（阶段 10）

公共 API 均要求登录，跨用户资源与不存在资源统一返回 404：

| Method | Path | 语义 |
| --- | --- | --- |
| GET/POST | `/api/v1/knowledge-bases` | 分页搜索 / 创建 |
| GET/PATCH/DELETE | `/api/v1/knowledge-bases/{baseId}` | 详情 / 修改 / 软删除（有文档时 409） |
| GET/POST | `/api/v1/knowledge-bases/{baseId}/documents` | 文档列表 / 引用 `fileId` 并处理 |
| GET/DELETE | `/api/v1/knowledge-documents/{documentId}` | 文档详情 / 从 KB 移除 |
| POST | `/api/v1/knowledge-documents/{documentId}/retry` | 仅 FAILED 可重试 |
| GET/PUT | `/api/v1/conversations/{id}/knowledge-bases` | 读取/替换 0~20 个真实绑定 |

Spring → Python 使用 `X-Service-Authorization`：二进制 `POST /internal/rag/documents/process` 同时携带 `X-User-Id/X-Knowledge-Base-Id/X-Document-Id/X-Document-Name/X-Document-Mime/X-Provider-Key/X-Embedding-Model`；`POST /internal/rag/retrieve` 发送 `userId/knowledgeBaseIds/query/topK/tokenBudget/providerKey/model`；`DELETE /internal/rag/documents/{id}` 幂等清理 owner-scoped 向量。retrieve 返回 `sources[]`：`documentId/knowledgeBaseId/chunkIndex/content/tokenCount/score/page/sourceMetadata`。

Python 返回的 source 不直接成为授权事实。Spring 仅接受当前用户、当前会话已绑定知识库中仍为 `READY` 的文档，随后把有界片段包装为不可信 reference system block。internal sources 与既有 public SSE `citation` 保留类型构成引用契约；本阶段不宣称模型回答已产生精确 citation event。

## 7. 契约演进与测试

- 公共 DTO、Python schema 和 SSE event 都带版本策略；破坏性变更进入 `/v2` 或明确 schema version。
- Spring 生成 OpenAPI 作为 Vue JSON 客户端的类型来源；SSE 事件另外维护 JSON Schema/契约测试。
- Spring ↔ Python 使用 consumer/provider contract tests，至少覆盖成功、终止、超时、乱序事件、未知事件、断线和错误脱敏。
- 所有 DTO 变更必须搜索并验证 Vue、Spring、Python 三端调用方。
- 阶段 5 中 public `requestId`、internal `aiRequestId` 与 `ai_request_log.request_id` 使用同一生成 ID，HTTP 入口诊断 ID 仍由 `X-Request-Id`/MDC 独立维护。重复 Idempotency-Key 当前返回冲突；只有证明需要重放/attempt 历史后才扩展数据模型。
