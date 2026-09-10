import { ApiError } from "@/api/client";

/**
 * True only when the server explicitly answered 404. Every other failure (403 RBAC rejection,
 * 500 backend fault, network error) must be reported as an error and never as "not found" —
 * hiding them behind one message is what made list/detail inconsistencies look like missing data.
 */
export function isNotFoundError(e: unknown): boolean {
  return e instanceof ApiError && e.status === 404;
}

/** Keeps HTTP status and platform error code visible so an operator can tell RBAC from a missing row. */
export function describeError(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    return `${fallback}（HTTP ${e.status} / 错误码 ${e.code}）：${e.message}`;
  }
  return e instanceof Error && e.message ? e.message : fallback;
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat().format(value);
}

export function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let size = value / 1024;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size.toFixed(size >= 10 ? 1 : 2)} ${units[unit]}`;
}

export function confirmDanger(action: string): boolean {
  const first = window.confirm(
    `${action}\n\nThis is an audited administrative action.`
  );
  if (!first) return false;
  return window.confirm(`Final confirmation: ${action}`);
}

export function formatDateTime(value?: string): string {
  if (!value) return "—";
  const d = new Date(value);
  if (isNaN(d.getTime())) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(d);
}

export const USER_STATUS_LABELS: Record<string, string> = {
  NORMAL: "正常",
  BANNED: "已封禁",
  DISABLED: "已停用",
  DELETED: "已删除",
};

export const USER_ROLE_LABELS: Record<string, string> = {
  USER: "用户",
  ADMIN: "管理员",
  SUPER_ADMIN: "超级管理员",
};

/* ---------- AI 运营（Provider / Model） ---------- */

export const PROVIDER_TYPE_LABELS: Record<string, string> = {
  OPENAI: "OpenAI",
  AZURE_OPENAI: "Azure OpenAI",
  ANTHROPIC: "Anthropic",
  GEMINI: "Google Gemini",
  DEEPSEEK: "DeepSeek",
  OLLAMA: "Ollama（本地）",
  OPENROUTER: "OpenRouter",
  OPENAI_COMPATIBLE: "OpenAI 兼容",
};

export const MODEL_TYPE_LABELS: Record<string, string> = {
  CHAT: "对话",
  EMBEDDING: "向量",
  RERANK: "重排",
  MULTIMODAL: "多模态",
};

export const CAPABILITY_LABELS: Record<string, string> = {
  streaming: "流式",
  vision: "视觉",
  reasoning: "推理",
  embedding: "向量",
};

export const HEALTH_LABELS: Record<string, string> = {
  UNKNOWN: "未探测",
  HEALTHY: "正常",
  DEGRADED: "降级",
  UNAVAILABLE: "不可用",
};

/**
 * Mirrors the server-side rule for `credentialRef`: a secret *reference* such as `env:NAME`,
 * never a raw key. Kept in sync so the console can reject a pasted key before it is submitted.
 */
export const CREDENTIAL_REF_PATTERN = new RegExp(
  "^$|^[a-z][a-z0-9-]{1,15}:[A-Za-z0-9][A-Za-z0-9_.:/@-]{0,200}$"
);

/* ---------- 审计日志与 AI 请求日志 ---------- */

/** Chinese labels for every {@code AdminAuditAction}; unknown values fall back to the raw enum. */
export const AUDIT_ACTION_LABELS: Record<string, string> = {
  USER_CREATE: "创建用户",
  USER_UPDATE: "更新用户",
  USER_BAN: "封禁用户",
  USER_UNBAN: "解除封禁",
  USER_DISABLE: "停用用户",
  USER_ENABLE: "启用用户",
  USER_RESTORE: "恢复用户",
  USER_DELETE: "删除用户",
  USER_RESET_PASSWORD: "重置密码",
  USER_ROLES_UPDATE: "变更角色",
  USER_USAGE_ADJUST: "调整额度",
  VIEW_CONVERSATION: "查看会话",
  VIEW_CHAT_MESSAGES: "查看消息",
  VIEW_USER_FILE: "查看文件",
  VIEW_KNOWLEDGE_BASE: "查看知识库",
  DELETE_CONVERSATION: "删除会话",
  DELETE_CHAT_MESSAGE: "删除消息",
  FILE_DELETE: "删除文件",
  FILE_DOWNLOAD: "下载文件",
  RAG_DOCUMENT_RETRY: "重新处理文档",
  RAG_DOCUMENT_DELETE: "删除文档",
  RAG_VECTOR_REMOVE: "删除向量",
  PROVIDER_CREATE: "创建服务商",
  PROVIDER_UPDATE: "更新服务商",
  PROVIDER_STATUS_UPDATE: "服务商启停",
  PROVIDER_DELETE: "删除服务商",
  MODEL_CREATE: "创建模型",
  MODEL_UPDATE: "更新模型",
  MODEL_STATUS_UPDATE: "模型启停",
  MODEL_DELETE: "删除模型",
  MODEL_DEFAULT_UPDATE: "变更默认模型",
  MODEL_REORDER: "调整模型排序",
};

export const AUDIT_TARGET_LABELS: Record<string, string> = {
  USER: "用户",
  USER_ROLE: "用户角色",
  USER_USAGE: "用户额度",
  CONVERSATION: "会话",
  CHAT_MESSAGE: "消息",
  USER_FILE: "文件",
  KNOWLEDGE_BASE: "知识库",
  KNOWLEDGE_DOCUMENT: "知识库文档",
  AI_PROVIDER: "AI 服务商",
  AI_MODEL: "AI 模型",
};

export const AI_REQUEST_STATUS_LABELS: Record<string, string> = {
  PENDING: "等待中",
  STREAMING: "生成中",
  COMPLETED: "已完成",
  STOPPED: "已停止",
  FAILED: "失败",
};
