import {
  ApiError,
  ApiResult,
  apiRequest,
  authenticatedFetch,
} from "@/api/client";
import {
  AiModel,
  AiStreamEvent,
  ChatMessage,
  Conversation,
  ConversationExportFormat,
  MessageSearchHit,
  PageResult,
} from "@/types/conversation";
import { FileReference } from "@/types/file";
import { BatchDeleteResult } from "@/types/batch";
import { consumeSseChunk, parseSseBlock } from "@/api/sseParser";

export interface ConversationQuery {
  search?: string;
  page?: number;
  size?: number;
}

export function listConversations(
  query: ConversationQuery = {}
): Promise<PageResult<Conversation>> {
  const params = new URLSearchParams({
    page: String(query.page ?? 0),
    size: String(query.size ?? 50),
    sort: "LAST_MESSAGE_AT",
    direction: "DESC",
  });
  if (query.search?.trim()) params.set("search", query.search.trim());
  return apiRequest(`/api/v1/conversations?${params.toString()}`);
}

export function createConversation(title?: string): Promise<Conversation> {
  return apiRequest("/api/v1/conversations", {
    method: "POST",
    body: JSON.stringify({ title: title?.trim() || undefined }),
  });
}

/**
 * Asks the backend to derive a short title from the conversation's first user message. Safe to call after
 * the first message is sent; the backend only renames when the title is still a default placeholder and
 * there is exactly one user message, so repeated calls are idempotent.
 */
export function generateTitle(id: string): Promise<Conversation> {
  return apiRequest(`/api/v1/conversations/${encodeURIComponent(id)}/title`, {
    method: "POST",
  });
}

/**
 * Removes the current user's empty conversations (no message ever sent). The conversation identified by
 * `keepId` is preserved so a freshly created chat is never pruned before the user types anything.
 */
export function deleteEmptyConversations(keepId?: string): Promise<number> {
  const params = new URLSearchParams();
  if (keepId) params.set("keep", keepId);
  return apiRequest(`/api/v1/conversations/empty?${params.toString()}`, {
    method: "DELETE",
  });
}

export function getConversation(id: string): Promise<Conversation> {
  return apiRequest(`/api/v1/conversations/${encodeURIComponent(id)}`);
}

export function deleteConversationsBatch(
  ids: string[]
): Promise<BatchDeleteResult> {
  return apiRequest("/api/v1/conversations/batch", {
    method: "DELETE",
    body: JSON.stringify({ ids }),
  });
}

export function listMessages(id: string): Promise<PageResult<ChatMessage>> {
  return apiRequest(
    `/api/v1/conversations/${encodeURIComponent(id)}/messages?page=0&size=100`
  );
}

export function listAiModels(): Promise<AiModel[]> {
  return apiRequest("/api/v1/ai/models");
}

/**
 * Owner-scoped full-text search over every conversation of the current user.
 * The backend never returns other users' messages, and never returns full bodies — only snippets.
 */
export function searchMessages(
  keyword: string,
  page = 0,
  size = 20
): Promise<PageResult<MessageSearchHit>> {
  const params = new URLSearchParams({
    q: keyword,
    page: String(page),
    size: String(size),
  });
  return apiRequest(`/api/v1/messages/search?${params.toString()}`);
}

/** Same search, scoped to a single owned conversation. */
export function searchConversationMessages(
  id: string,
  keyword: string,
  page = 0,
  size = 20
): Promise<PageResult<MessageSearchHit>> {
  const params = new URLSearchParams({
    q: keyword,
    page: String(page),
    size: String(size),
  });
  return apiRequest(
    `/api/v1/conversations/${encodeURIComponent(
      id
    )}/messages/search?${params.toString()}`
  );
}

/**
 * Downloads an owned conversation as Markdown or JSON. The response is a file attachment, so the raw
 * `Response` is returned instead of an `ApiResult`; failures still need to be surfaced by the caller.
 */
export async function exportConversation(
  id: string,
  format: ConversationExportFormat
): Promise<Response> {
  const response = await authenticatedFetch(
    `/api/v1/conversations/${encodeURIComponent(id)}/export?format=${format}`
  );
  if (!response.ok) {
    throw new ApiError(
      response.status,
      (await response.json()) as ApiResult<unknown>
    );
  }
  return response;
}

export async function streamMessage(
  path: string,
  body: Record<string, unknown>,
  signal: AbortSignal,
  onEvent: (event: AiStreamEvent) => void
): Promise<void> {
  const response = await authenticatedFetch(path, {
    method: "POST",
    headers: {
      Accept: "text/event-stream, application/json",
      "Idempotency-Key": newClientRequestId(),
    },
    body: JSON.stringify(body),
    signal,
  });
  if (!response.ok) {
    const result = (await response.json()) as ApiResult<unknown>;
    throw new ApiError(response.status, result);
  }
  if (!response.body) throw new Error("Streaming response body is unavailable");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let streamDone = false;
  try {
    while (!streamDone) {
      const result = await reader.read();
      streamDone = result.done;
      const chunk = decoder
        .decode(result.value, { stream: !streamDone })
        .replace(/\r\n/g, "\n");
      // consumeSseChunk keeps an accumulating buffer across reads so a single
      // SSE event split across TCP chunks (or several events in one chunk) is
      // reassembled correctly. Malformed blocks are skipped, never fatal.
      buffer = consumeSseChunk(buffer, chunk, onEvent);
    }
    if (buffer.trim()) {
      try {
        parseSseBlock(buffer, onEvent);
      } catch (parseError) {
        if (process.env.NODE_ENV !== "production") {
          console.warn("[SSE_PARSE_ERROR]", parseError);
        }
      }
    }
  } finally {
    // Release the stream even when the caller aborts or parsing throws;
    // otherwise the reader and response body stay locked for the page lifetime.
    await reader.cancel().catch(() => undefined);
  }
}

/**
 * Append one decoded chunk to the persistent SSE buffer, emit every fully
 * received event (`\n\n` delimited), and return the leftover partial buffer.
 * The implementation lives in `@/api/sseParser` so it can be unit tested
 * without a DOM/`fetch` environment.
 */
export function streamConversationMessage(
  conversationId: string,
  content: string,
  modelId: string,
  parentMessageId: string | undefined,
  attachments: FileReference[],
  signal: AbortSignal,
  onEvent: (event: AiStreamEvent) => void
): Promise<void> {
  return streamMessage(
    `/api/v1/conversations/${encodeURIComponent(
      conversationId
    )}/messages/stream`,
    {
      content,
      contentType: "PLAIN_TEXT",
      modelId,
      parentMessageId,
      attachmentIds: attachments.map((file) => file.id),
    },
    signal,
    onEvent
  );
}

export function regenerateMessage(
  messageId: string,
  modelId: string,
  signal: AbortSignal,
  onEvent: (event: AiStreamEvent) => void
): Promise<void> {
  return streamMessage(
    `/api/v1/messages/${encodeURIComponent(messageId)}/regenerate`,
    { modelId },
    signal,
    onEvent
  );
}

export function editAndResendMessage(
  messageId: string,
  content: string,
  modelId: string,
  attachments: FileReference[],
  signal: AbortSignal,
  onEvent: (event: AiStreamEvent) => void
): Promise<void> {
  return streamMessage(
    `/api/v1/messages/${encodeURIComponent(messageId)}/edit-and-resend`,
    {
      content,
      contentType: "PLAIN_TEXT",
      modelId,
      attachmentIds: attachments.map((file) => file.id),
    },
    signal,
    onEvent
  );
}

export function stopAiRequest(requestId: string): Promise<{ status: string }> {
  return apiRequest(
    `/api/v1/ai/requests/${encodeURIComponent(requestId)}/stop`,
    {
      method: "POST",
    }
  );
}

function newClientRequestId(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return `web-${Array.from(bytes, (value) =>
    value.toString(16).padStart(2, "0")
  ).join("")}`;
}
