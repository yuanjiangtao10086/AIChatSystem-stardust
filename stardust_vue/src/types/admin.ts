import { PageResult } from "@/types/conversation";
import { UserRole, UserStatus } from "@/types/auth";
import { KnowledgeDocumentStatus } from "@/types/knowledge";
import { UsageView } from "@/types/usage";

/** One hourly bucket of the dashboard trend; always 24 of them, oldest first (UTC). */
export interface AdminHourlyPoint {
  bucketStart: string;
  requests: number;
  failures: number;
}

export interface AdminDashboard {
  totalUsers: number;
  todayNewUsers: number;
  activeUsers: number;
  conversations: number;
  messages: number;
  aiRequests: number;
  totalTokens: number;
  files: number;
  storageBytes: number;
  ragDocuments: number;
  systemErrors: number;
  enabledProviders: number;
  enabledModels: number;
  hourly: AdminHourlyPoint[];
  recentAudits: AdminAudit[];
  recentFailures: AdminAiRequest[];
}

/** Filters for the append-only audit trail; every value is optional and server-side enforced. */
export interface AdminAuditQuery {
  page?: number;
  size?: number;
  adminId?: string;
  action?: string;
  targetType?: string;
  from?: string;
  to?: string;
}

/** Filters for the AI request log; {@code from}/{@code to} are ISO 8601 instants. */
export interface AdminAiRequestQuery {
  page?: number;
  size?: number;
  userId?: string;
  status?: string;
  provider?: string;
  model?: string;
  from?: string;
  to?: string;
}
export interface AdminUser {
  id: string;
  email: string;
  displayName: string;
  status: UserStatus;
  banReason?: string;
  bannedUntil?: string;
  lastLoginAt?: string;
  roles: UserRole[];
  usedBytes: number;
  quotaBytes: number;
  aiRequests: number;
  aiTokens: number;
  usage?: UsageView;
  createdAt: string;
  updatedAt: string;
}
export interface AdminConversation {
  id: string;
  userId: string;
  userEmail: string;
  userName: string;
  title: string;
  status: string;
  messageCount: number;
  lastMessageAt?: string;
  createdAt: string;
}
export interface AdminMessage {
  id: string;
  conversationId: string;
  role: "USER" | "ASSISTANT" | "SYSTEM" | "TOOL";
  content: string;
  contentType: "PLAIN_TEXT" | "MARKDOWN" | "JSON";
  status: string;
  totalTokens?: number;
  sequenceNo: number;
  createdAt: string;
}
export interface AdminConversationDetail {
  id: string;
  userId: string;
  userEmail: string;
  userName: string;
  title: string;
  status: string;
  messageCount: number;
  lastMessageAt?: string;
  createdAt: string;
  updatedAt: string;
  messages: AdminMessagePage;
}
export interface AdminFile {
  id: string;
  userId: string;
  userEmail: string;
  userName: string;
  name: string;
  mime: string;
  sizeBytes: number;
  status: string;
  referenced: boolean;
  createdAt: string;
}
/**
 * File metadata exposed to an administrator. The response deliberately carries no
 * object key and no server-side absolute path: downloads always go through the
 * audited admin download endpoint.
 */
export interface AdminFileDetail {
  id: string;
  userId: string;
  userEmail: string;
  userName: string;
  name: string;
  declaredMime: string;
  detectedMime: string;
  extension: string;
  sizeBytes: number;
  sha256: string;
  storageProvider: string;
  status: string;
  metadataJson?: string;
  attachmentCount: number;
  knowledgeDocumentCount: number;
  referenced: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface AdminKnowledgeBase {
  id: string;
  userId: string;
  userEmail: string;
  userName: string;
  name: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}
export interface AdminKnowledgeBaseDetail {
  id: string;
  userId: string;
  userEmail: string;
  userName: string;
  name: string;
  description?: string;
  status: string;
  documentCount: number;
  readyDocumentCount: number;
  failedDocumentCount: number;
  totalChunks: number;
  createdAt: string;
  updatedAt: string;
  documents: PageResult<AdminKnowledgeDocument>;
}
export interface AdminKnowledgeDocument {
  id: string;
  knowledgeBaseId: string;
  knowledgeBaseName: string;
  userId: string;
  userEmail: string;
  userName: string;
  fileId: string;
  filename: string;
  mimeType: string;
  status: KnowledgeDocumentStatus;
  chunkCount: number;
  processingVersion: number;
  parserType?: string;
  embeddingProvider?: string;
  embeddingModel?: string;
  errorCode?: string;
  errorMessage?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}
export type ProviderType =
  | "OPENAI"
  | "AZURE_OPENAI"
  | "ANTHROPIC"
  | "GEMINI"
  | "DEEPSEEK"
  | "OLLAMA"
  | "OPENROUTER"
  | "OPENAI_COMPATIBLE";
export type ProviderStatus = "ENABLED" | "DISABLED";
export type ProviderHealth = "UNKNOWN" | "HEALTHY" | "DEGRADED" | "UNAVAILABLE";
export type ModelType = "CHAT" | "EMBEDDING" | "RERANK" | "MULTIMODAL";
export type ModelStatus = "ENABLED" | "DISABLED";

/**
 * Provider row for the console. The API returns only {@link AdminProvider.hasApiKey}
 * and {@link AdminProvider.maskedApiKey} — a full API key never reaches the browser.
 */
export interface AdminProvider {
  id: string;
  code: string;
  displayName: string;
  type: ProviderType;
  baseUrl: string;
  hasApiKey: boolean;
  maskedApiKey?: string;
  timeoutSeconds?: number;
  connectTimeoutSeconds?: number;
  status: ProviderStatus;
  health: ProviderHealth;
  healthCheckedAt?: string;
  modelCount: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Write payload. `credentialRef` is a secret *reference* (`env:NAME`, `vault:...`), never a raw
 * key: the platform stores references only, and a plaintext key is rejected by the API. Leaving
 * it blank on update keeps the stored credential.
 */
export interface AdminProviderPayload {
  code: string;
  displayName: string;
  type: ProviderType;
  baseUrl: string;
  credentialRef?: string | null;
  timeoutSeconds?: number | null;
  connectTimeoutSeconds?: number | null;
}

export interface ModelCapabilities {
  streaming: boolean;
  vision: boolean;
  reasoning: boolean;
  embedding: boolean;
}

export interface AdminModel {
  id: string;
  providerId: string;
  providerName: string;
  code: string;
  externalModelId: string;
  displayName: string;
  type: ModelType;
  capabilities: ModelCapabilities;
  contextWindow?: number;
  maxOutputTokens?: number;
  defaultTemperature?: number;
  defaultTopP?: number;
  defaultMaxOutputTokens?: number;
  inputPrice?: number;
  outputPrice?: number;
  currency?: string;
  status: ModelStatus;
  defaultModel: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminModelPayload {
  providerId: string;
  code: string;
  externalModelId: string;
  displayName: string;
  type: ModelType;
  capabilities: ModelCapabilities;
  contextWindow?: number | null;
  maxOutputTokens?: number | null;
  defaultTemperature?: number | null;
  defaultTopP?: number | null;
  defaultMaxOutputTokens?: number | null;
  inputPrice?: number | null;
  outputPrice?: number | null;
  currency?: string | null;
  sortOrder: number;
}
export interface AdminAiRequest {
  requestId: string;
  userId: string;
  conversationId: string;
  provider: string;
  model: string;
  status: string;
  latencyMs?: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  errorCode?: string;
  createdAt: string;
}
export interface AdminAudit {
  id: string;
  adminId: string;
  adminEmail: string;
  action: string;
  targetUserId?: string;
  targetResourceType: string;
  targetResourceId?: string;
  ip: string;
  userAgent?: string;
  requestId: string;
  metadataJson?: string;
  createdAt: string;
}
export type AdminUserPage = PageResult<AdminUser>;
export type AdminConversationPage = PageResult<AdminConversation>;
export type AdminMessagePage = PageResult<AdminMessage>;
export type AdminFilePage = PageResult<AdminFile>;
export type AdminKnowledgeBasePage = PageResult<AdminKnowledgeBase>;
export type AdminKnowledgeDocumentPage = PageResult<AdminKnowledgeDocument>;
export type AdminAiRequestPage = PageResult<AdminAiRequest>;
export type AdminAuditPage = PageResult<AdminAudit>;
