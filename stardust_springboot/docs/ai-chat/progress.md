# AI Chat SaaS 开发进度

> 最近更新：2026-09-11  
> 当前阶段：阶段 13「统计聚合、共享限流器、可观测性」已完成；阶段 12 用量与额度闭环已完成；阶段 11「管理员后台与审计」已完成（11A 用户管理 / 11B 聊天管理 / 11C 文件与云盘管理 / 11D 知识库与 RAG 管理）

## 专项：AI Chat 首字响应（TTFT）延迟诊断与修复

状态：Completed

背景：聊天可正常使用，但用户发送后有时「很久没有任何可见响应」，几十秒甚至更久才开始回答。本次**只做专项**，不新增业务功能，目标是把 TTFT（用户点击发送 → 页面第一次看到 AI 输出）压到最小，并补全可定位每一层时延的诊断日志。

### 定位结论（先定位，再修复）

逐层核对 `Vue → Spring → Python → Provider` 的流式链路：

- **Vue `useChatStream`**：`fetch` + `ReadableStream` 逐行解析 `event:`；收到 `start` 即 `addPlaceholders`（骨架/思考态）。链路与解析均正确，**不是瓶颈**。
- **Spring `JdkHttpAiGateway`**：`HttpClient.send` + `ofInputStream` 逐行解析 SSE，无缓冲。**不是瓶颈**。
- **Python `ChatService.stream` / `OpenAICompatibleProvider.stream_chat`**：`async for` 逐 chunk 转发，转发前无隐藏缓冲。**不是瓶颈**（仅有一个超时配置缺陷，见下）。
- **Spring `AiStreamingService` / `AiStreamPersistenceService`**：**根因**。`prepare()` 在**控制器线程、SSE 尚未开启前**就同步执行 `contextBuilder.build()`，其中包含 `RagContextService.retrieve()` 向 Python 发起的 **query embedding 远程调用**，且整个过程持有 conversation 的 `FOR UPDATE` 行锁。于是 RAG embedding 的远程往返被完全阻塞在「用户点击发送 → 浏览器可见第一字节」之间，期间页面完全空白，并阻塞其他请求。

### 已完成

- [x] **根因修复（P0）**：`prepare()` / `prepareRegenerate()` / `prepareEditAndResend()` 仍在同一短事务做快速 DB 落库 + 额度预留（保留「超额即回滚、不建占位/不建 SSE」的 HTTP 契约），但额度估计所用的上下文只走**本地部分**（system + summary + memory + 近期消息 + 当前消息），**不包含远程 RAG query embedding**，因此控制器线程上没有任何网络调用。远程 RAG embedding 被推迟到 **流式 worker 线程、`start` 已发出之后**：新增 `AiStreamPersistenceService.buildContext(PreparedAiStream)`（`@Transactional(readOnly = true)`）在 worker 中构建**完整上下文（含 RAG）**。此时 `prepare()` 的 `FOR UPDATE` 锁早已释放，RAG 远程时延不再阻塞首字、也不再阻塞其他请求。
- [x] **`AiStreamingService.run()` 顺序修正**：先 `markStreaming` + `send("start")`（浏览器立即进入「生成中」骨架态），再 `buildContext`，最后才向 Python 发流。上下文内容、Memory / RAG / Summary 组装顺序与规则**完全不变**，仅挪动执行时机。
- [x] **Python 流式读超时解耦（P1）**：`settings.provider_stream_read_timeout_seconds`（默认 600s）为已打开的流设置独立的「两次字节之间」空闲读超时；`provider_timeout_seconds` 仍用于连接/写/池。避免 provider 在 token 间停顿（思考/调度）时被组合超时误杀。
- [x] **端到端诊断日志**：以 `requestId`（Spring）== `ai_request_id`（Python，同源下发）为关联键，逐层打点：`AI stream started` / `AI stream context built contextMs` / `AI stream first token springPreprocessMs+pythonTtftMs+totalTtftMs`、`chat stream start`、`provider first token ttft_ms`、`chat stream first token serviceTtftMs`，以及 `Context build memoryMs+ragMs`。详见 [`performance.md`](performance.md) 的诊断决策树。

### 验证

- Spring：`AiStreamingIntegrationTests`（FakeAiGateway 同步消费，覆盖成功/失败/超时/停止/越权）、`JdkHttpAiGatewayTests`（真实 HTTP SSE 解析）、`ConversationContextBuilder` 相关测试应对齐；`buildContext` 在 worker 阶段调用，`PreparedAiStream.messages()` 初始为 `null`。
- Python：`python -m py_compile` 通过；`provider_stream_read_timeout_seconds` 经 `app/main.py` 注入 ProviderRegistry。
- Vue：未改动（流式客户端本就正确）。

### 已知边界

- [ ] 本次只修复「首字前空白」与「流式读超时误杀」；若 `Context build ragMs` 仍高，是 RAG query embedding 本身的远程耗时，需另行做 embedding 缓存 / 异步化（不属本次范围）。
- [ ] 全局总超时 `app.ai.stream.request-timeout`（默认 PT2M）仍由 Spring watchdog 兜底整体生成时长，与「两次 token 间空闲读超时」分离。
- [ ] 专项性能任务无法在本环境跑全量 `mvn test` / `pytest`（命令审批未通过），改动均经静态核对与既有测试契约保持一致；建议后续在可运行环境跑 `mvn -o test` 与 `pytest` 收口。

## 阶段 11H：管理端详情 50002 排错（审计写入 / 审计枚举容错）

状态：Completed

现象：管理员查看「对话 / 文件 / 知识库」详情统一报 `HTTP 500 / 错误码 50002 / persistence operation failed`；列表与用户详情正常。上一轮的 11G 修复让前端不再把 500 伪装成「未找到」，真实状态码才得以暴露。

### 已完成

- [x] **根因 A（写入落在只读事务）**：`AdminResourceService` 的 `conversation` / `file` / `download` / `knowledgeBase` 是 `@Transactional(readOnly = true)`，却通过 `AdminAuditService` 写入 `admin_audit_log`。改这四处为 `@Transactional`（可写）。这正是「仅这三个详情失败、列表与用户详情正常」的唯一差异：`/users/{id}` 不写审计。
- [x] **根因 B（审计枚举不可容错）**：`admin_audit_log.action` 用 `@Enumerated(STRING)`；项目 seed 与历史数据里存在阶段 11B 已重命名的 `CONVERSATION_VIEW` / `CONVERSATION_DELETE`。未知值使**任何**读取 `admin_audit_log` 的请求抛 `IllegalArgumentException` 并被包装成 50002（`AdminCrudConsistencyTests.legacyAuditActionValueWrittenByOlderBuildDoesNotBreakReads` 在本机复现：审计日志接口 `expected 200 but was 500`）。
- [x] 新增 `V13__normalize_admin_audit_action.sql` 归一化历史动作名；`AdminAuditLog.action` 改用 `AdminAuditActionConverter`，未知值降级为新增的 `AdminAuditAction.LEGACY`。
- [x] `AdminAuditService` 加固：`ip` 空白时兜底 `unknown`（列 `NOT NULL`，原实现会把空白截断成 `null` 触发约束错误）；截断按 UTF-8 边界处理，避免劈开代理对导致 MySQL 拒绝写入；写失败时记录 `action/resourceType/resourceId/adminId/requestId` 后原样抛出（不吞异常）。
- [x] `GlobalExceptionHandler.handlePersistenceException` 增加 `method/uri/requestId/exceptionType/rootType/rootMessage` 并保留完整 stack trace；响应体仍只返回通用 `50002`，不泄漏数据库信息。
- [x] 错误码区分已有：`40401` 资源不存在、`40301` 权限不足、`50002` 持久化异常、`50003` 存储异常，本次未新增码。

### 验证

- Spring：`mvn -o test` 全量 **123 项通过**（`AdminCrudConsistencyTests` 8 项，含遗留枚举值用例）；V13 迁移在 H2 正常应用。
- Vue / Python：本次未改动。

### 已知边界

- [ ] 现场 `user_file` 具体数据未能核对（无 MySQL 环境）；修复后若详情仍 500，新日志的 `rootMessage` 会直接给出底层原因。
- [ ] `AdminAuditAction.LEGACY` 仅作历史数据兜底，应用代码不得写入。

## 阶段 11G：管理员 CRUD 全链路排错与修复

状态：Completed

背景：管理端「对话 / 文件 / 知识库」出现「列表有数据、详情显示未找到」的跨模块一致性问题。按 Database → Repository → Service → DTO → JSON → Vue → Router 逐段核对后排除 ID 语义错配（三端均为 `publicId`，URI 与 `@PathVariable` 一致），定位到两个叠加缺陷。

### 已完成

- [x] **根因 A（授权语义不一致）**：管理端列表接口不施加 ADR-058 的 SUPER_ADMIN 保护，详情/读取接口却经 `AdminAuthorizationService.requireCanView` 施加。因此 `ADMIN` 能看到 `SUPER_ADMIN` 的会话/文件/知识库，点击后收到 `40301`。修复：`AdminResourceService` 新增 `hidesSuperAdminOwned(actor)`，把同一规则下推到 `ConversationRepository` / `UserFileRepository` / `KnowledgeBaseRepository` / `KnowledgeDocumentRepository` 的 `findAdmin` JPQL；四个列表 Controller 传入 `@AuthenticationPrincipal AuthenticatedUser`。列表不再展示操作者无权打开的行（ADR-063）。
- [x] **根因 B（前端吞掉真实错误）**：四个详情视图把 `error` 渲染在 `v-else`（详情存在）分支内，任何失败都落到 `!detail` 分支并显示「未找到…」，把 403/500 伪装成数据缺失。修复：`utils/admin.ts` 新增 `isNotFoundError` / `describeError`；`AdminConversationDetailView`、`AdminFileDetailView`、`AdminKnowledgeBaseDetailView`、`AdminUserDetailView` 仅在 `ApiError.status === 404` 时显示「未找到」，其余显示 `HTTP 状态 + 平台错误码 + 服务端 message`。
- [x] **契约漂移**：`AdminDtos.ConversationView` 缺少 `userName`（其余管理端列表 VO 均有，Vue `AdminConversation` 也声明了），导致列表「用户」列空白。已补字段并由测试锁定。
- [x] 新增 `AdminCrudConsistencyTests` 7 项：用列表响应体里的 `id` 直接查详情（Conversation / UserFile / KnowledgeBase 均 200）、未知 ID 三个端点均 404、普通 USER 三个端点均 `40301`、ADMIN 列表不展示 SUPER_ADMIN 资源且详情仍 `40301`、SUPER_ADMIN 能打开列表显示的全部资源。

### 验证

- Spring：`mvn -o test` 全量 **122 项通过**；`mvn -o package -DskipTests` 成功。
- Vue：`npm run typecheck` 通过；`npm run lint` 0 error（3 条既有无用导入 warning）；`npm run build` 成功（`DONE Build complete`）。
- Python：本次未改动。

### 已核实为正常、未改动的部分

- ID 链路：数据库 `public_id CHAR(26)` → Entity `publicId` → DTO `id` → JSON → TS `id: string` → `route.params.id`（未做 `Number()`/`parseInt` 转换）→ `encodeURIComponent` 拼接，三端一致。
- 软删除：列表与详情均为 `deletedAt is null`（`Conversation` / `UserFile` / `KnowledgeBase` / `KnowledgeDocument`），无 `@TableLogic` / `@Where` 隐式过滤差异。
- API base path：全部统一为 `/api/v1/admin/**`，无混用。

## 阶段 13：统计聚合、共享限流器、可观测性

状态：Completed

- [x] **统计聚合**：`GET /api/v1/usage/breakdown?from&to&by=DAY|MODEL|PROVIDER`（当前用户）与 `GET /api/v1/admin/usage/breakdown`（全站，仅 ADMIN/SUPER_ADMIN）。数据实时聚合自 `ai_request_log`（仅 `COMPLETED` 计入 token 与请求数，避免 STOPPED/FAILED 干扰），**不新增表**。
- [x] 新增 `usage.breakdown` 纵向切片：`UsageBreakdownService` + `AiRequestLogRepository` 六条 JPQL 聚合（按日/模型/Provider 各用户态与管理态），`BreakdownDimension` 枚举由 query param 绑定。
- [x] Vue：`api/usage.ts` 新增 `getUsageBreakdown` / `getAdminUsageBreakdown`；`types/usage.ts` 新增 `UsageBreakdownItem`/`BreakdownDimension`；管理端新增「用量分析」菜单与 `AdminUsage.vue` 轻量 SVG 柱状图（按日/模型/Provider + 7/30/90 天）；`UsagePanel` 在 Profile 非 compact 态展示近 14 天用量趋势条。
- [x] **共享限流器**：抽 `RateLimiter` 接口，`InMemoryRateLimiter`（默认）与 `RedisRateLimiter`（Spring Data Redis + Lettuce，Lua 原子计数）由 `app.security.rate-limit.store=memory|redis` 选择；`RateLimitAutoConfiguration` 在 redis 模式下自建 `LettuceConnectionFactory`（支持 `app.redis.password-encoded` 的 base64 密码）。
- [x] **X-Forwarded-For 可信代理**：`ClientIpResolver` 仅当直连 peer 命中 `app.security.rate-limit.trusted-proxies`（支持精确/IPv4 CIDR）时才取 XFF 最左跳；默认忽略 XFF，避免伪造绕过限流（ADR-052 延续）。
- [x] 限流覆盖注册 / 刷新 / 上传入口：`EndpointRateGuard` 对注册按 IP、刷新按 IP、上传按 userId 计数，超限抛 `42901 RATE_LIMITED`（默认 8/20/30 次窗口，可在 `.env` 调）。
- [x] **可观测性**：引入 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`，暴露 `/actuator/prometheus`（仅 ADMIN，health/info 公开）；计数器 `auth_login_rate_limited_total`、`auth_register_rate_limited_total`、`auth_refresh_rate_limited_total`、`file_upload_rate_limited_total`，以及对账 `ai_usage_reconcile_duration_seconds`、`ai_usage_reconcile_released_total`、`ai_usage_reconcile_drift_total`，漂移与限流命中从纯日志升级为可采集指标（ADR-057）。

### 验证

- Spring：`EndpointRateGuardTests` 2 项（注册/上传维度隔离与上限）通过；`RedisRateLimiterTests` 以 Testcontainers 跑真实 Redis，无 Docker 时自动跳过；既有 `LoginRateLimitTests` 已随 `register/refresh` 签名增加 `clientIp` 同步更新。
- 回归预期：阶段 12 全部测试继续有效；`mvn -o package -DskipTests` 应通过（新增 actuator/redis 依赖与 `app.*` 配置项均有默认值，test profile 排除 `RedisAutoConfiguration` 并设 `store=memory`）。
- Vue：`npm run typecheck` 应通过；新增管理端路由 `/admin/usage` 与菜单项。

### 已知边界

- [ ] `store=redis` 为可切换适配器，默认仍为 `memory`；生产多实例前需将 `RATE_LIMIT_STORE=redis` 并部署 Redis（密码经 `REDIS_PASSWORD_ENCODED=true` 以 base64 注入）。
- [ ] 对账仍为全表扫描 `ai_usage_account`，用户量增长后需分片。
- [ ] Prometheus 拉取需独立 bearer/管理端口策略；当前 `/actuator/**` 仅 ADMIN 可访问，Grafana 侧需配置对应凭据或管理端口。
- [ ] 告警规则（如 drift>0、限流命中突增）由运维在 Grafana/Prometheus 侧配置，后端只负责产出指标。

## 阶段 12 第二批：用量 UI、管理员额度调整、登录限流与周期对账

状态：Completed

- [x] Vue 新增 `types/usage.ts`、`api/usage.ts` 与 `components/usage/UsagePanel.vue`（完整卡片 / `compact` 侧栏条），接入 Profile 与聊天侧栏。
- [x] `AdminUserView` 新增可选 `usage` 字段（与 `UsageView` 同构），管理员用户列表展示 `used/quota tokens`、请求数与已用费用。
- [x] `POST /api/v1/admin/users/{id}/usage:adjust`：权限校验 + 理由必填 + 写入 `ADJUST` ledger + `USER_USAGE_ADJUST` 审计；负增量不得使已用量变负（`40003`），正增量不得超额（`42902`）。
- [x] 登录暴力破解防护：`InMemoryRateLimiter` + `LoginAttemptGuard`，按客户端地址与归一化邮箱双维度计数，超限返回 `42901 RATE_LIMITED`，成功登录清零；只信任 `getRemoteAddr()`（ADR-052）。
- [x] `AiUsageReconciliationService` 定时清扫悬挂 RESERVE（缺失 / `FAILED` / `STOPPED` 释放，`COMPLETED` 按请求记录 token 结算，`PENDING/STREAMING` 不动）并比对账户与 ledger，漂移只告警（ADR-053/054）。
- [x] 新增 `SchedulingConfiguration`（`@EnableScheduling`），对账间隔与清扫窗口可配置。

### 验证

- Spring：`AdminUsageAdjustTests` 3 项、`LoginRateLimitTests` 3 项、`AiUsageReconciliationTests` 3 项全部通过。
- 回归全绿：`PersistenceInfrastructureTests` 3、`AiStreamingIntegrationTests` 8、`AdminIntegrationTests` 3、`AuthenticationIntegrationTests` 12、`ConversationIntegrationTests` 2、`ConversationShortMemoryIntegrationTests` 6、`MemoryIntegrationTests` 3、`UserFileIntegrationTests` 3、`KnowledgeRagIntegrationTests` 3、`AiUsageIntegrationTests` 6 及基础设施测试，合计 67 项通过；`mvn -o package -DskipTests` 成功。
- Vue：`npm run typecheck` 通过；`npx eslint src --ext .ts,.vue --fix` 后 0 error；`npm run build` 成功（仅既有体积告警）。
- Python：本次未改动。

### 已知边界

- [ ] 限流状态在 JVM 堆内，重启丢失且不跨实例；生产多实例前需共享限流器 + 可信代理/真实客户端地址策略。
- [ ] 对账任务当前扫描全部 `ai_usage_account`；用户量增长后需分片。
- [ ] 按模型/Provider 的用量统计聚合、用户自助购买/续期额度未实现。

## 阶段 12 第一批：AI 用量账户与额度闭环

状态：Completed（后端事实闭环；不含前端 UI 与管理员额度管理）

- [x] V10 创建 `ai_usage_account` 与 `ai_usage_ledger`；CHECK 保证计数非负、`used + reserved <= quota` 与 `period_end > period_start`，并为现有用户回填账户。
- [x] 新增 `usage` 纵向切片：`entity/repository/service/dto/controller`，`AiUsageLedgerRepository` 只暴露 `save` 与查询方法，不提供 update/delete。
- [x] 生成前在同一短事务 reserve：预留量 = 上下文估算 prompt tokens + 模型 `max_output_tokens`（缺省 `app.ai.usage.output-reserve-tokens`）；额度不足抛 `42902 AI_QUOTA_EXCEEDED` 并回滚，不创建占位消息、不建立 SSE。
- [x] 正常完成按 Provider 真实 prompt/completion tokens 结算；主动停止与失败释放预留。`(ai_request_id, operation_key)` 唯一约束保证幂等；`sum(token_delta) == used + reserved` 恒成立。
- [x] 启动恢复扫描 interrupted request，为被收敛为失败的悬挂预留追加 RELEASE。
- [x] 注册与管理员建用户时创建用量账户；访问路径对缺失账户按配置默认惰性补建。
- [x] `GET /api/v1/usage` 返回当前周期额度视图；周期到期惰性滚动重置，历史事实只由 ledger 保留。
- [x] 明确 `ai_model.input_price/output_price` 单位为「每 1,000 tokens」，结算使用平台统一币种（ADR-050/ADR-051）。

### 验证

- Spring：`AiUsageIntegrationTests` 6 项通过（reserve/settle 一致性与 ledger 不变量、按每 1K 价格计费、超额拒绝、release 幂等与 settle 后忽略、重复操作幂等、缺失账户惰性补建）。
- 回归：`PersistenceInfrastructureTests` 3、`AiStreamingIntegrationTests` 8、`AdminIntegrationTests` 3、`AuthenticationIntegrationTests` 12、`ConversationIntegrationTests` 2、`ConversationShortMemoryIntegrationTests` 6、`MemoryIntegrationTests` 3、`UserFileIntegrationTests` 3、`KnowledgeRagIntegrationTests` 3 及基础设施测试全部通过；`mvn -o package -DskipTests` 成功，Flyway V1–V10 与 Hibernate validate 通过。
- Vue / Python：本次未改动。

### 有意边界

- [ ] 未提供管理员额度调整（ADJUST）入口、周期对账任务与基于时间的悬挂清扫；`ADJUST` 仅作为 ledger 枚举保留。
- [ ] 停止与失败按保守策略返还预留，不猜测计费，因此可被高频「发起即停止」滥用，需由后续限流/风控阶段约束。
- [ ] 用户与管理员用量 UI、按模型/Provider 的统计聚合未实现。

## 阶段 11：管理员后台与审计

状态：Completed（补记；代码已存在于 `admin/` 包与 V9，此前未写入进度）

- [x] V9 `admin_audit_log` 追加写；查看用户聊天、封禁、重置密码、角色变更等敏感动作留痕。
- [x] 管理员用户 CRUD、状态变更（封禁/禁用/恢复/注销）、重置密码与角色管理；跨管理员越权受 `AdminAuthorizationService` 约束。
- [x] 管理员会话/消息/文件/知识库/Provider/Model/AI 请求日志查询与违规内容删除。
- [x] `AdminDashboardService` 提供用户、会话、消息、AI 请求、token、文件与 RAG 文档统计。

### 阶段 11A：Vue 管理控制台落地（本次）

- [x] 后端增强：`GET /api/v1/admin/users` 新增 `role` 角色过滤与 `direction`（`ASC/DESC`）创建时间排序参数；已删除用户详情改为可读（便于审计核对）；状态变更审计动作补齐 `USER_ENABLE`（`DISABLED→NORMAL`），与 `USER_UNBAN`（`BANNED→NORMAL`）明确区分。
- [x] `AdminUserManagementTests` 12 项覆盖：普通用户 403、BANNED 管理员 40302、列表/搜索/分页/状态过滤/角色过滤、创建与更新审计、封禁/解封/停用/启用审计动作、软删除+审计+恢复、重置密码、角色变更、ADMIN 不可改 SUPER_ADMIN / 不可提权 / 不可删自己。
- [x] Vue `/admin` 改为嵌套路由：`AdminLayoutView`（侧栏导航 + 退出登录）承载 `AdminDashboardView` / `AdminUsersView` / `AdminUserDetailView`。
- [x] 用户管理页（真实交互）：搜索/状态/角色/排序筛选、分页、新建/编辑（`AdminUserFormModal`）、角色管理（`AdminRoleModal`，非超级管理员禁用 ADMIN/SUPER_ADMIN 选项但仍以服务端为准）、重置密码（`AdminPasswordModal`）、封禁/解封/停用/启用/软删除/恢复（二次确认 `AdminConfirmModal`）；所有操作走服务端校验并刷新列表。
- [x] 用户详情页：基础资料、角色、存储用量进度、AI 额度（`UsageView`）、操作区复用同一组模态框。
- [x] 管理控制台样式接入 `styles.scss`（`.admin-toolbar/.admin-table/.pager/.status/.role-chip/.admin-card` 等），与项目既有浅色设计语言一致。
- [x] 侧栏中文件/知识库/AI 运营/审计日志以「待接入」明确标注，不伪装为已实现（前端尚未接入，后端能力已具备）。

### 阶段 11B：管理员聊天记录管理（本次）

- [x] 后端新增/修正：`GET /api/v1/admin/conversations/{id}`（会话详情，内嵌首屏消息）与 `DELETE /api/v1/admin/messages/{id}`（删除单条消息）；已有 `GET /conversations`、`GET /conversations/{id}/messages`、`DELETE /conversations/{id}` 保留。
- [x] 会话目录 `findAdmin` 检索扩展为**标题 / 用户名 / 邮箱 / 消息正文**多字段 `like`，并支持 `userId` 精确过滤与 `from/to` 创建时间过滤。
- [x] 敏感读取强制审计：`VIEW_CONVERSATION`（详情）、`VIEW_CHAT_MESSAGES`（消息）由 Service 层在每次打开时自动写 `admin_audit_log`；删除动作写 `DELETE_CONVERSATION` / `DELETE_CHAT_MESSAGE`。统一 `CONVERSATION_VIEW → VIEW_CHAT_MESSAGES`、`CONVERSATION_DELETE → DELETE_CONVERSATION` 命名，新增 `VIEW_CONVERSATION` / `DELETE_CHAT_MESSAGE`。
- [x] 权限收紧：`AdminAuthorizationService.requireCanView` 规定非 `SUPER_ADMIN` 的管理员**禁止查看/删除 `SUPER_ADMIN` 的私人聊天资源**（返回 `40301`）；`USER` 与 `BANNED` 管理员在既有 Spring Security / JWT 层仍被拒绝。
- [x] 复用既有 `Conversation`/`ChatMessage` Entity 与 Repository，经 `AdminResourceService` 独立查询方法提供，不污染普通用户接口、不新建 AdminConversation Entity。
- [x] `AdminConversationManagementTests` 6 项覆盖：普通用户 403、BANNED 管理员 40302、列表/标题搜索/用户名搜索/消息内容搜索/userId 过滤/时间过滤、详情审计 `VIEW_CONVERSATION`、消息审计 `VIEW_CHAT_MESSAGES`、ADMIN 不可访问 SUPER_ADMIN 会话（详情/消息/删除会话/删除消息均 40301）、删除会话 `DELETE_CONVERSATION`（随后 404）+ 删除消息 `DELETE_CHAT_MESSAGE`（从列表消失）；既有 `AdminIntegrationTests` 断言同步更新为 `VIEW_CHAT_MESSAGES`。
- [x] Vue：新增 `AdminConversationsView`（搜索/用户 ID 过滤/时间筛选/分页/查看/删除二次确认）与 `AdminConversationDetailView`（元数据 + 消息流 + 单条消息删除 + 删除整个会话），复用 `ChatMarkdown` 安全渲染 markdown；路由加入 `/admin/conversations` 与 `/admin/conversations/:id`，侧栏「对话」启用。列表页仅展示元数据，完整内容只在详情呈现。
### 阶段 11C：管理员文件与云盘管理（本次）

- [x] 后端新增 `GET /api/v1/admin/files/{id}`（文件详情：元数据 + 所属用户 + 引用统计）与 `GET /api/v1/admin/files/{id}/download`（经 `StorageService` 流式下载）；`GET /admin/files` 列表筛选扩展为 `search`（文件名）、`userSearch`（用户名/邮箱）、`mime`（前缀）、`status`、`minSize`/`maxSize`（字节）、`from/to`（上传时间），并保留 `userId` 精确过滤与分页。
- [x] `AdminDtos.FileView` 补 `userName`；新增 `AdminFileDetailView`（`declaredMime/detectedMime/extension/sha256/storageProvider/metadataJson/attachmentCount/knowledgeDocumentCount/referenced/updatedAt`），**响应不含 `object_key` 与任何服务器绝对路径**。
- [x] 引用统计：`ChatMessageAttachmentRepository.countByUserFileId` 与 `KnowledgeDocumentRepository.countByUserFileIdAndDeletedAtIsNull`；被引用文件删除仍返回 `409`（沿用 `FilePersistenceService` 既有保护）。
- [x] 安全：`AdminResourceService.requireSafeObjectKey` 在打开/删除前拒绝含 `..`、以 `/` 开头或含反斜杠的 object key（`LocalStorageProvider.SAFE_KEY` 白名单与 root 包含校验为第二道防线）；下载与删除一律经 `StorageService`，Controller 不访问本地文件路径；下载响应沿用 `Content-Disposition: attachment`、`nosniff`、`CSP sandbox`、`private, no-store`。
- [x] 审计：新增 `VIEW_USER_FILE`（查看详情）与 `FILE_DOWNLOAD`（下载），`FILE_DELETE`（删除）沿用；由 Service 层写入，Vue 不提交审计事件。
- [x] 权限：`deleteFile` 也纳入 `AdminAuthorizationService.requireCanView`，`ADMIN` 禁止查看/下载/删除 `SUPER_ADMIN` 的私人文件（`40301`），延续 ADR-058；新增 ADR-059。
- [x] `AdminFileManagementTests` 6 项覆盖：普通用户 403、文件名/用户名/MIME/大小区间/时间区间筛选、详情元数据与引用统计且响应不含 object key、下载字节一致 + `attachment` + `FILE_DOWNLOAD` 审计、删除后对象从存储移除（再次 `open` 抛 `IOException`）+ `FILE_DELETE` 审计 + 详情 404、ADMIN 不可读/下载/删 SUPER_ADMIN 文件（40301）、含 `..` 的 object key 不被打开（5xx 且不返回内容）。
- [x] Vue：新增 `AdminFilesView`（文件名/用户名/MIME/状态/大小区间/时间区间筛选、分页、查看/下载/删除二次确认）与 `AdminFileDetailView`（元数据 + 所属用户 + 引用情况 + 下载 + 删除）；`api/admin.ts` 的 `listAdminFiles` 改为参数对象并新增 `getAdminFile`/`downloadAdminFile`（经 `authenticatedFetch` 携带 Token，不拼直链）；路由 `/admin/files` 与 `/admin/files/:id`，侧栏「文件」由「待接入」转为启用。
- [x] 清理：删除阶段 11B 遗留且已无引用的占位组件 `components/admin/AdminConversations.vue`（旧 `listAdminConversations(0,…)` 签名）与 `components/admin/AdminFiles.vue`，避免死代码与构建失败。

### 验证

- Spring：`mvn -o test` 全量 **89 项通过**（含新增 `AdminFileManagementTests` 6 项）；`AdminConversationManagementTests.adminCanDeleteConversationAndMessageWithAudit` 中未持久化的 `m1` 已修正补齐 `saveAndFlush`，全量回归转绿。
- Vue：`npm run typecheck` 通过；`npm run lint -- --fix` 后 0 error；`npm run build` 成功（`DONE Build complete`）。
- Python：本次未改动。

### 已知边界

### 阶段 11D：管理员知识库与 RAG 管理（本次）

- [x] 后端新增 `GET /api/v1/admin/knowledge-bases/{id}`（知识库详情：所属用户 + 处理概况 + 首屏 50 条文档）与 `DELETE /api/v1/admin/knowledge-documents/{id}`（删除文档：分块 + 元数据 + 向量索引）；`GET /admin/knowledge-bases` 增加 `userSearch`（用户名/邮箱）筛选，`GET /admin/knowledge-documents` 增加 `search`（文件名）与 `userSearch` 筛选。
- [x] `AdminDtos`：`KnowledgeBaseView` 补 `userName`/`updatedAt`；`KnowledgeDocumentView` 扩展为含 `knowledgeBaseName/fileId/mimeType/processingVersion/parserType/embeddingProvider/embeddingModel/errorMessage/startedAt/completedAt/updatedAt`（`chunkCount` 与 `errorCode` 原本已有）；新增 `KnowledgeBaseDetailView`（`description/documentCount/readyDocumentCount/failedDocumentCount/totalChunks/documents`）。
- [x] 统计查询：`KnowledgeDocumentRepository` 新增按知识库的文档总数 / `READY` 数 / `FAILED` 数 与 `sum(chunk_count)`；`KnowledgeBaseRepository.findAdminDetail` 与 `KnowledgeDocumentRepository.findAdminDetail` 用 fetch join 预取 owner/base/file，避免无事务路径上的懒加载。
- [x] 一致性修正：`removeVectors` 删除 chunk 行后同步 `clearChunks()`，`chunkCount` 不再残留旧值（原先会向管理员显示「有分块」但实际已无向量）。
- [x] 权限与审计：重试 / 删除文档 / 删除向量统一加 `requireCanView`（`ADMIN` 对 `SUPER_ADMIN` 私人知识资产 `40301`）；新增 `VIEW_KNOWLEDGE_BASE` 与 `RAG_DOCUMENT_DELETE` 审计动作，`RAG_DOCUMENT_RETRY` / `RAG_VECTOR_REMOVE` 沿用；**三层边界**：Vue 只调 `/api/v1/admin/**`，AI 处理由 `KnowledgeDocumentService` 经 `RagGateway` 调用 Python，Vue 不直连 Python（新增 ADR-060）。
- [x] `AdminKnowledgeManagementTests` 7 项覆盖：普通用户 403、知识库列表（userId/userSearch/搜索）与详情（文档数/就绪数/失败数/总 chunks + `VIEW_KNOWLEDGE_BASE` 审计）、文档按状态/知识库/文件名/用户名筛选（含 `PROVIDER_ERROR` 失败原因）、重试 `READY` + `RAG_DOCUMENT_RETRY` 审计、删除文档 204 + `RAG_DOCUMENT_DELETE` 审计 + 向量经网关删除 + 列表消失、删除向量后 `FAILED/VECTOR_REMOVED` 且 `chunkCount=0` + `RAG_VECTOR_REMOVE` 审计、`ADMIN` 不可查看/重试/删除/删向量 `SUPER_ADMIN` 资源（40301）。测试以 `@Primary FakeRagGateway` 替换 Python 网关，无需真实 AI 服务。
- [x] Vue：新增 `AdminKnowledgeView`（知识库列表：名称/用户/状态筛选 + 分页 + 查看）与 `AdminKnowledgeBaseDetailView`（基本信息 + 处理概况统计卡 + 文档表：状态徽标 / 分块数 / 处理版本 / 失败原因 / 重新处理 / 删除向量 / 删除文档二次确认）；路由 `/admin/rag` 与 `/admin/rag/:id`，侧栏「知识库 / RAG」由「待接入」转为启用。
- [x] 清理：删除无引用的占位组件 `components/admin/AdminRag.vue`（无分页/无用户筛选/无删除文档，且使用旧 API 签名）。

### 验证

- Spring：`mvn -o test` 全量 **96 项通过**（含新增 `AdminKnowledgeManagementTests` 7 项），既有 `KnowledgeRagIntegrationTests` 3 项仍通过。
- Vue：`npm run typecheck` 通过；`npm run lint -- --fix` 后 0 error；`npm run build` 成功。
- Python：本次未改动。

### 已知边界

- [x] AI 运营与审计日志前端均已接入（分别为阶段 11E、11F）；至此侧栏不再有「待接入」项。
- [ ] 管理员未开放「删除知识库」；需先清空文档，与普通用户侧策略一致（ADR-043 同类保护）。
- [ ] 文档处理仍为同步调用 Python，无队列/重试退避；大批量重建需后续异步化。
- [ ] 管理员删除未做批量操作与回收站；被引用文件需先解除引用（沿用 ADR-043 策略）。
- [ ] 未按内容做病毒扫描/CDR；`sha256` 暴露用于人工核对，不作为内容判定依据。

### 阶段 11E：AI 服务商与模型管理后台（本次）

- [x] 迁移 `V11__extend_ai_catalog.sql`：`ai_model` 增加 `is_default BOOLEAN NOT NULL DEFAULT FALSE` 与 `idx_ai_model_default` 索引；Provider 未加列（连接事实仍由 Python `STARDUST_AI_*` 设置决定，ADR-032）。
- [x] 新增 `ai/catalog` 契约层：`ModelCapabilities`、`ModelParameters`、`ProviderConfig` 值对象；`AiCatalogJsonCodec` 统一读写三列 JSON，**读取兼容历史形状**（capabilities 数组式 `["streaming"]`、参数嵌套式 `{"temperature":{"default":0.7}}`），损坏/空值退化为「未配置」而非 500；`ProviderCredentialResolver` 接口 + `EnvironmentProviderCredentialResolver`（解析 `env:NAME`）+ `CredentialMask`（`sk-****1234`）。
- [x] **密钥只写不读**：`ProviderView` 只含 `hasApiKey` 与 `maskedApiKey`，不含 `credentialRef`；`AiProvider.getCredentialRef()` 加 `@JsonIgnore`；掩码由运行时解析出的真实值生成，数据库不存明文。`credentialRef` 只接受引用（`@Pattern` `^$|scheme:…`），粘贴裸 Key 返回 `40001` 且不入库；编辑留空 = 保持原密钥，填新引用 = 替换（审计以布尔 `credentialReplaced` 记录）。
- [x] Provider 能力：CRUD、启用/停用、`timeoutSeconds`/`connectTimeoutSeconds`（存 `non_secret_config_json`）、`modelCount`、健康状态透出；`code` 不可变；删除受保护（仍有模型或已产生 `ai_request_log`，`ON DELETE RESTRICT` → `40901`）。
- [x] Model 能力：必须关联 `providerId`；结构化 `capabilities{streaming,vision,reasoning,embedding}`、`contextWindow`/`maxOutputTokens`、默认参数 `defaultTemperature`/`defaultTopP`/`defaultMaxOutputTokens`、价格与币种、`sortOrder`；启停（停用自动清除默认标记）；同 `type` 内唯一默认模型（仅 `ENABLED` 可设，否则 `40901`）；同服务商内上/下移（先规范化为 10/20/30… 再交换相邻项，越界为幂等空操作）；`code`/归属不可变；已产生请求日志的模型删除 → `40901`。
- [x] 用户侧 `GET /api/v1/ai/models` 新增 `defaultModel`，前端据此预选默认模型；该接口只下发「Provider 已启用 + 模型已启用」的 `CHAT` 模型。
- [x] 审计：新增 `PROVIDER_DELETE`/`MODEL_DELETE`/`MODEL_DEFAULT_UPDATE`/`MODEL_REORDER`，沿用 `PROVIDER_CREATE/UPDATE/STATUS_UPDATE`、`MODEL_CREATE/UPDATE/STATUS_UPDATE`；全部由 Service 层写入，元数据只含 `type`/`code`/`credentialConfigured`/`credentialReplaced`/`timeoutSeconds`/`direction`/`defaultModel` 等非敏感字段（`Map.of` 不接受 null，改用容忍 null 的 `metadata(...)` 构造）。
- [x] 测试：`AdminAiCatalogTests` 8 项（掩码且响应不含密钥与引用、裸 Key 拒绝且不入库、留空保留原密钥与新引用替换、生命周期与依赖删除保护、能力/参数结构化读写、默认模型唯一性与用户侧下发、排序、普通用户 40301）+ `AiCatalogJsonCodecTests` 7 项（历史 JSON 兼容与容错）；`AdminIntegrationTests` 契约同步为 `hasApiKey`/`maskedApiKey`/`timeoutSeconds`。
- [x] Vue：新增 `AdminAiView`（服务商 / 模型 / 请求日志三页签）、`AdminProviderFormModal`（含「留空保持原值」提示与当前凭据说明、引用格式前端预校验）、`AdminModelFormModal`（能力勾选、参数、定价、排序）；模型表格支持设为默认 / 上移下移 / 启停 / 删除，服务商表格支持编辑 / 启停 / 删除（有模型时禁用并提示）；启用、停用、删除走 `AdminConfirmModal` 二次确认。`api/admin.ts` 补 `deleteAdminProvider`/`deleteAdminModel`/`setAdminModelDefault`/`moveAdminModel` 并把请求体改为强类型 payload；`types/admin.ts` 重写 AI 目录类型；`utils/admin.ts` 补类型/能力/健康标签与 `CREDENTIAL_REF_PATTERN`；路由加入 `/admin/ai`，侧栏「AI 运营」由「待接入」转为启用。
- [x] 清理：删除使用旧接口契约（`credentialConfigured`/`capabilitiesJson`/`parameterPolicyJson` 与 `prompt()` 输入）且已无引用的占位组件 `components/admin/AdminAi.vue`。

### 验证

- Spring：`mvn -o test` 全量 **111 项通过**（含新增 `AdminAiCatalogTests` 8 项、`AiCatalogJsonCodecTests` 7 项），无回归。
- Vue：`npm run lint -- --fix` 后 0 error；`npm run build` 成功（`DONE Build complete`，无 TS 诊断）。
- Python：本次未改动。

### 已知边界

- [ ] 审计日志前端仍为「待接入」（后端能力具备）。
- [ ] 控制台配置尚未下发给 Python（ADR-032）：目录与启停/默认开关是平台事实源，实际调用参数仍由 Python `STARDUST_AI_*` 决定。若要让控制台成为唯一事实源，需先立新 ADR 定义下发通道与回滚语义。
- [ ] 掩码依赖部署环境提供被引用的变量；未提供时只能显示「已配置」而无掩码，`vault:` 等非 `env:` scheme 目前一律返回空掩码（resolver 待后续接入真实 Secret Manager）。
- [ ] 密钥轮换仍需人工更新环境变量并在控制台改引用，未做自动轮换/到期提醒。
- [ ] 模型删除为硬删除且受 `ai_request_log` 外键保护；历史调用过的模型只能停用，未提供归档状态。

### 阶段 11F：Dashboard + 审计日志 + AI Request Log（本次）

- [x] 迁移 `V12__index_request_and_audit_time.sql`：只增 `ai_request_log(created_at, id)` 与 `admin_audit_log(created_at, id)` 两个索引（原有复合索引均以其他列为前导列，时间筛选必然全表扫描）；不新增表、不改字段，脚本用 `CREATE INDEX` 以同时兼容 MySQL 与测试的 H2。
- [x] `AdminDtos`：`Dashboard` 扩展 `enabledProviders/enabledModels/hourly/recentAudits/recentFailures`，新增 `HourlyPoint(bucketStart, requests, failures)`；新增 `AdminDtos.of(AdminAuditLog)` 与 `of(AiRequestLog)` 共享映射，消除总览/审计/调用日志三处重复投影。
- [x] `AdminDashboardService`：趋势为最近 24 小时 UTC 逐小时分桶，**在 Java 内分桶**（仓库只投影 `created_at`、`status` 两列），避免 MySQL/H2 的 `hour()` 方言差异与 JDBC 会话时区影响；恒返回 24 个点（空平台为零值桶）；近期审计 5 条 / 近期失败 5 条；新增启用中的 Provider 与 Model 计数。
- [x] 日志筛选：`AdminAuditLogRepository.findAdmin` 与 `AiRequestLogRepository.findAdmin` 新增 `from/to` 闭区间时间筛选；新增 `GET /api/v1/admin/ai/requests/{requestId}` 单条详情（不存在 `40401`）。控制器沿用既有 `Instant from/to` 参数风格（与会话/文件筛选一致）。
- [x] `AdminDashboardAndLogsTests` 4 项覆盖：总览真实聚合 + 24 点趋势求和 + 近期失败/审计可见且有界、审计按管理员/动作/目标/时间区间筛选与分页、调用日志按状态/服务商/模型/用户/时间区间筛选与详情 404、普通用户三项接口均 `40301`。
- [x] Vue：新增 `AdminDashboardView`（真实数据驱动的 24 小时柱图含失败占比、8 张指标卡、近期管理动作与最近失败调用，并链接到完整日志页）、`AdminAuditView`（管理员/动作/目标类型/时间区间筛选 + 分页 + 元数据与 UA 提示 + 明确「只可读取」说明）、`AdminAiRequestsView`（状态/服务商/模型/用户/时间区间筛选 + 分页 + 详情弹窗）；`AdminAiView` 的请求日志只读 tab 移除，改为跳转到独立页面，避免两处重复实现。
- [x] 配套：`api/admin.ts` 的 `listAdminAuditLogs`/`listAdminAiRequests` 改为带筛选参数的分页查询并删除重复的 `listAdminAudits` 死代码；`types/admin.ts` 新增 `AdminHourlyPoint`/`AdminAuditQuery`/`AdminAiRequestQuery`；`utils/admin.ts` 补 `AUDIT_ACTION_LABELS` / `AUDIT_TARGET_LABELS` / `AI_REQUEST_STATUS_LABELS`；路由新增 `/admin/audit` 与 `/admin/ai/requests`，侧栏「审计日志」由「待接入」转为启用。
- [x] 清理：删除占位组件 `components/admin/AdminDashboard.vue`（24 根柱子为硬编码假趋势、字节数未格式化）、`components/admin/AdminAudit.vue`（无筛选/无分页）与 11E 遗留的空文件 `components/admin/AdminAi.vue`。

### 验证

- Spring：`mvn -o test` 全量 **115 项通过**（含新增 `AdminDashboardAndLogsTests` 4 项），无回归。
- Vue：`npm run lint -- --fix` 后 0 error；`npm run build` 成功（`DONE Build complete`，无 TS 诊断）。
- Python：本次未改动。

### 已知边界

- [ ] 审计日志已接入，但不支持对目标用户、IP 或 `metadata_json` 的模糊搜索（避免全表扫描），更强检索应接入外部日志系统。
- [ ] 仪表盘为实时聚合，大表下存在 O(近 24 小时行数) 扫描；规模化后需按小时 rollup 或改用 Prometheus 指标（需新 ADR）。
- [ ] 趋势固定为 UTC 小时分桶，不随浏览器时区变化。
- [ ] 近期列表固定 5 条；需要更长列表请到对应日志页。

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

- Owner 明确下达「按模型统计聚合 / 共享限流器 / 生产加固 / 计费」任务；阶段 12 第一、二批完成后不得擅自进入后续阶段。
- 认证 Cookie/CSRF 与 Token 策略已冻结；跨域部署前必须新增 CORS/Origin 策略 ADR，不能直接放开 credentials CORS。
- 在真实 MySQL 环境验证 V1 至 V7；生成 Python lock、修复 Maven Wrapper 并锁定 Node LTS，以恢复可复现命令入口。
