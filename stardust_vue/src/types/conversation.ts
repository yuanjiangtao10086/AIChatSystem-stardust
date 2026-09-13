import { FileReference } from "@/types/file";

export type ConversationStatus = "ACTIVE" | "ARCHIVED";
export type MessageRole = "USER" | "ASSISTANT" | "SYSTEM" | "TOOL";
export type MessageContentType = "PLAIN_TEXT" | "MARKDOWN" | "JSON";
export type MessageStatus =
  | "PENDING"
  | "STREAMING"
  | "COMPLETED"
  | "STOPPED"
  | "FAILED";

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface Conversation {
  id: string;
  title: string;
  status: ConversationStatus;
  lastMessageAt?: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  parentMessageId?: string;
  supersedesMessageId?: string;
  role: MessageRole;
  content: string;
  contentType: MessageContentType;
  modelId?: string;
  status: MessageStatus;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  errorCode?: string;
  errorMessage?: string;
  sequenceNo: number;
  variantNo: number;
  createdAt: string;
  reasoningContent?: string;
  attachments?: FileReference[];
}

export interface AiModel {
  id: string;
  code: string;
  displayName: string;
  provider: string;
  defaultModel?: boolean;
}

/** One hit of the owner-scoped message search. Only a bounded snippet travels to the browser. */
export interface MessageSearchHit {
  messageId: string;
  conversationId: string;
  conversationTitle: string;
  role: MessageRole;
  status: MessageStatus;
  sequenceNo: number;
  variantNo: number;
  snippet: string;
  createdAt: string;
}

export type ConversationExportFormat = "MARKDOWN" | "JSON";

export type AiStreamEventType =
  | "start"
  | "delta"
  | "reasoning"
  | "usage"
  | "done"
  | "error"
  | "citation"
  | "tool_start"
  | "tool_delta"
  | "tool_done";

export interface AiStreamEvent {
  type: AiStreamEventType;
  requestId: string;
  conversationId: string;
  messageId: string;
  userMessageId?: string;
  modelId?: string;
  operation?: "SEND" | "REGENERATE" | "EDIT_AND_RESEND";
  content?: string;
  status?: MessageStatus;
  finishReason?: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  code?: string;
  message?: string;
  retryable?: boolean;
  partial?: boolean;
}
