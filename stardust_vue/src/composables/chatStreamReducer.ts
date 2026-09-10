import type { AiStreamEvent, ChatMessage } from "../types/conversation";
import type { FileReference } from "@/types/file";

export type ChatAction =
  | {
      type: "SEND";
      content: string;
      attachments: FileReference[];
      parentMessageId?: string;
      requestId?: string;
    }
  | { type: "REGENERATE"; target: ChatMessage }
  | {
      type: "EDIT_AND_RESEND";
      target: ChatMessage;
      content: string;
      attachments: FileReference[];
    };

export function userPlaceholder(
  id: string,
  content: string,
  sequenceNo: number,
  variantNo: number,
  createdAt: string,
  parentMessageId?: string,
  supersedesMessageId?: string,
  conversationId = ""
): ChatMessage {
  return {
    id,
    conversationId,
    role: "USER",
    status: "COMPLETED",
    content,
    contentType: "PLAIN_TEXT",
    reasoningContent: "",
    attachments: [],
    parentMessageId: parentMessageId || undefined,
    supersedesMessageId: supersedesMessageId || undefined,
    sequenceNo,
    variantNo,
    createdAt,
  };
}

export function assistantPlaceholder(
  event: AiStreamEvent,
  sequenceNo: number,
  variantNo: number,
  createdAt: string,
  parentMessageId?: string,
  conversationId = ""
): ChatMessage {
  return {
    id: event.messageId as string,
    conversationId,
    role: "ASSISTANT",
    status: "STREAMING",
    content: event.content || "",
    contentType: "MARKDOWN",
    modelId: event.modelId,
    reasoningContent: "",
    attachments: [],
    parentMessageId: parentMessageId || undefined,
    supersedesMessageId: undefined,
    sequenceNo,
    variantNo,
    createdAt,
  };
}

/**
 * Pure reducer: apply one decoded SSE event to the message array in place.
 * Kept free of Vue/vuex/router so it can be unit tested directly.
 *
 * Lifecycle:
 *  - start: the user message was already appended instantly on send(); here we
 *    reconcile its temporary id (`user-<requestId>`) with the real server id, and
 *    make sure the assistant placeholder exists exactly once (keyed by the real
 *    assistantMessageId).
 *  - delta/reasoning: append content to the assistant message.
 *  - usage: record token usage.
 *  - done: mark the assistant COMPLETED (or STOPPED when the user cancelled).
 *  - error: mark the assistant FAILED.
 */
export function applyStreamEvent(
  allMessages: ChatMessage[],
  current: ChatAction,
  event: AiStreamEvent
): void {
  if (event.type === "start") {
    if (current.type === "SEND") {
      const tempUser = allMessages.find(
        (item) => item.id === `user-${current.requestId}`
      );
      const userMessageId = event.userMessageId || tempUser?.id || "";
      if (tempUser && event.userMessageId) tempUser.id = event.userMessageId;
      const userSequence =
        tempUser?.sequenceNo ??
        Math.max(0, ...allMessages.map((item) => item.sequenceNo));
      if (!allMessages.find((item) => item.id === event.messageId)) {
        allMessages.push(
          assistantPlaceholder(
            event,
            userSequence + 1,
            0,
            new Date().toISOString(),
            userMessageId,
            event.conversationId
          )
        );
      }
    } else if (
      current.type === "EDIT_AND_RESEND" ||
      current.type === "REGENERATE"
    ) {
      // A regenerated / edited-and-resent reply replaces an existing turn, so it
      // keeps that turn's sequence number and bumps the variant index:
      // `selectActiveBranch` renders the highest variant per sequence.
      const target = current.target;
      const sequence =
        current.type === "REGENERATE"
          ? target.sequenceNo
          : target.sequenceNo + 1;
      const variant =
        Math.max(
          0,
          ...allMessages
            .filter((item) => item.sequenceNo === sequence)
            .map((item) => item.variantNo)
        ) + 1;
      allMessages.push(
        assistantPlaceholder(
          event,
          sequence,
          variant,
          new Date().toISOString(),
          event.userMessageId || target.parentMessageId,
          event.conversationId
        )
      );
    }
    return;
  }
  const assistant = allMessages.find((item) => item.id === event.messageId);
  if (!assistant) return;
  switch (event.type) {
    case "delta":
      if (event.content)
        assistant.content = (assistant.content || "") + event.content;
      break;
    case "reasoning":
      if (event.content)
        assistant.reasoningContent =
          (assistant.reasoningContent || "") + event.content;
      break;
    case "usage":
      assistant.promptTokens = event.promptTokens ?? 0;
      assistant.completionTokens = event.completionTokens ?? 0;
      assistant.totalTokens = event.totalTokens ?? 0;
      break;
    case "done":
      assistant.content = event.content || assistant.content;
      assistant.status = event.status === "STOPPED" ? "STOPPED" : "COMPLETED";
      break;
    case "error":
      assistant.status = "FAILED";
      assistant.errorCode = event.code || "AI_ERROR";
      assistant.errorMessage = event.message || "";
      break;
    default:
      break;
  }
}
