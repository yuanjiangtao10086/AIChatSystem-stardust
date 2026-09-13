import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  createConversation,
  deleteEmptyConversations,
  editAndResendMessage,
  generateTitle,
  getConversation,
  listAiModels,
  listConversations,
  listMessages,
  exportConversation,
  regenerateMessage,
  searchMessages,
  stopAiRequest,
  streamConversationMessage,
} from "@/api/conversations";
import { ApiError } from "@/api/client";
import {
  AiModel,
  AiStreamEvent,
  ChatMessage,
  Conversation,
  ConversationExportFormat,
  MessageSearchHit,
} from "@/types/conversation";
import { FileReference } from "@/types/file";
import { selectActiveBranch } from "@/utils/conversationBranch";
import { applyStreamEvent, userPlaceholder } from "./chatStreamReducer";
import type { ChatAction } from "./chatStreamReducer";

// `crypto.randomUUID` is absent from the DOM lib this project compiles against
// (and is unavailable on insecure origins), so fall back to a non-cryptographic
// id — it only labels an in-flight request locally.
const newRequestId = (): string => {
  const webCrypto = crypto as unknown as { randomUUID?: () => string };
  return (
    webCrypto.randomUUID?.() ??
    `req-${Date.now()}-${Math.random().toString(16).slice(2)}`
  );
};

/**
 * Reads a download file name from `Content-Disposition`, preferring the RFC 5987 `filename*` form so
 * Chinese conversation titles survive. Falls back to `null` when the header is absent or unparsable.
 */
const fileNameFromDisposition = (header: string | null): string | null => {
  if (!header) return null;
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      return encoded[1];
    }
  }
  const plain = /filename="([^"]+)"/i.exec(header);
  return plain?.[1] ?? null;
};

export function useChatWorkspace() {
  const route = useRoute();
  const router = useRouter();
  const conversations = ref<Conversation[]>([]);
  const conversation = ref<Conversation | null>(null);
  const allMessages = ref<ChatMessage[]>([]);
  const messages = computed(() => {
    // `selectActiveBranch` only reads structural fields (sequence/variant/
    // parent), so a plain computed would NOT re-render while an assistant
    // message streams. Touch every field that mutates during streaming here so
    // the computed re-runs on each delta/status flip and the bubble updates live
    // (otherwise the UI stays frozen on "正在思考" until the array is replaced).
    for (const item of allMessages.value) {
      void item.content;
      void item.status;
      void item.reasoningContent;
      void item.errorMessage;
      void item.totalTokens;
    }
    return selectActiveBranch(allMessages.value);
  });
  const models = ref<AiModel[]>([]);
  const selectedModelId = ref("");
  const search = ref("");
  const loadingConversations = ref(false);
  const loadingMessages = ref(false);
  const creating = ref(false);
  const sending = ref(false);
  const error = ref("");
  const activeRequestId = ref<string | null>(null);
  const sidebarOpen = ref(false);
  // "title" filters the sidebar conversation list; "content" runs the owner-scoped
  // message search and swaps the list for hit results.
  const searchMode = ref<"title" | "content">("title");
  const searchHits = ref<MessageSearchHit[]>([]);
  const searching = ref(false);
  const highlightMessageId = ref<string | null>(null);
  const highlightConversationId = ref<string | null>(null);
  let controller: AbortController | null = null;
  let action: ChatAction | null = null;
  const conversationId = computed(
    () => route.params.conversationId as string | undefined
  );

  const describeError = (value: unknown): string => {
    if (value instanceof ApiError) {
      return `${value.message}${
        value.requestId ? ` · ${value.requestId}` : ""
      }`;
    }
    return value instanceof Error
      ? value.message
      : "请求未能完成，请稍后重试。";
  };

  const loadConversations = async (): Promise<void> => {
    loadingConversations.value = true;
    try {
      conversations.value = (
        await listConversations({ search: search.value })
      ).items;
    } catch (value) {
      error.value = describeError(value);
    } finally {
      loadingConversations.value = false;
    }
  };

  /**
   * Runs whatever the current search mode means: title mode re-queries the conversation list, content
   * mode asks the backend for message hits. An empty keyword in content mode clears results instead of
   * asking the server for "everything" (the backend rejects that too).
   */
  const runSearch = async (): Promise<void> => {
    if (searchMode.value === "title") {
      searchHits.value = [];
      await loadConversations();
      return;
    }
    const keyword = search.value.trim();
    if (!keyword) {
      searchHits.value = [];
      await loadConversations();
      return;
    }
    searching.value = true;
    try {
      searchHits.value = (await searchMessages(keyword)).items;
    } catch (value) {
      error.value = describeError(value);
    } finally {
      searching.value = false;
    }
  };

  /**
   * Jumps to the conversation a search hit belongs to and marks the message so the list can highlight it.
   * Messages are re-read from the server — the UI never invents a message from a snippet.
   */
  const openSearchHit = async (hit: MessageSearchHit): Promise<void> => {
    highlightMessageId.value = hit.messageId;
    highlightConversationId.value = hit.conversationId;
    sidebarOpen.value = false;
    if (conversationId.value === hit.conversationId) {
      await openConversation(hit.conversationId);
      return;
    }
    await router.push({
      name: "conversation",
      params: { conversationId: hit.conversationId },
    });
  };

  /** Downloads the current conversation as Markdown or JSON through the authenticated fetch layer. */
  const exportCurrent = async (
    format: ConversationExportFormat
  ): Promise<void> => {
    const id = conversationId.value;
    if (!id) {
      error.value = "当前没有可导出的对话，请先创建或打开一个对话。";
      return;
    }
    try {
      const response = await exportConversation(id, format);
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download =
        fileNameFromDisposition(response.headers.get("Content-Disposition")) ||
        `conversation.${format.toLowerCase()}`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      // Defer revoking the blob URL: some browsers cancel the download if the
      // URL is released synchronously right after the click.
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (value) {
      error.value = describeError(value);
    }
  };

  const openConversation = async (id?: string): Promise<void> => {
    // Never wipe an in-flight stream for the same conversation: the running
    // request owns `allMessages` until its `finally` refresh reconciles state.
    // Clearing here would make the just-sent message vanish until a manual refresh.
    if (sending.value && id && conversationId.value === id) return;
    // A stream started for the previous conversation must not keep appending
    // deltas into the one being opened now.
    controller?.abort();
    controller = null;
    conversation.value = null;
    allMessages.value = [];
    error.value = "";
    sidebarOpen.value = false;
    if (!id) return;
    // A highlight belongs to the hit the user clicked: opening any other conversation clears it.
    if (highlightConversationId.value !== id) highlightMessageId.value = null;
    loadingMessages.value = true;
    try {
      const [detail, history] = await Promise.all([
        getConversation(id),
        listMessages(id),
      ]);
      conversation.value = detail;
      allMessages.value = history.items;
    } catch (value) {
      error.value = describeError(value);
    } finally {
      loadingMessages.value = false;
    }
    // Leaving (or loading) a conversation: prune any other empty conversation we left behind, but never
    // the one we just opened. Skipped while a stream is in flight so we don't delete the chat being sent to.
    if (id && !sending.value) {
      await cleanupEmptyConversations(id);
    }
  };

  const newConversation = async (): Promise<void> => {
    if (creating.value) return;
    creating.value = true;
    error.value = "";
    try {
      const created = await createConversation();
      // Drop the previously empty conversation (if any) before switching to the new one.
      await cleanupEmptyConversations(created.id);
      await router.push({
        name: "conversation",
        params: { conversationId: created.id },
      });
    } catch (value) {
      error.value = describeError(value);
    } finally {
      creating.value = false;
    }
  };

  /**
   * @returns `true` when the request reached the backend and produced a stream outcome (including a
   * server-side error), `false` when it was never delivered or was aborted. Callers use this to
   * decide whether the composer must restore the draft and attachments.
   */
  const run = async (nextAction: ChatAction): Promise<boolean> => {
    if (!conversationId.value || !selectedModelId.value || sending.value)
      return false;
    const targetConversationId = conversationId.value;
    // A conversation with no messages loaded yet means this SEND is the very first message.
    const isFirstUserMessage =
      nextAction.type === "SEND" && allMessages.value.length === 0;
    action = nextAction;
    controller = new AbortController();
    const activeController = controller;
    sending.value = true;
    error.value = "";
    // Show the user's own message instantly, before the SSE `start` event
    // round-trips to the server. Without this the chat looks frozen and the
    // sent message only appears after the stream finishes (or after refresh).
    if (nextAction.type === "SEND") {
      const requestId = nextAction.requestId || newRequestId();
      nextAction.requestId = requestId;
      const sequence =
        Math.max(0, ...allMessages.value.map((item) => item.sequenceNo)) + 1;
      const tempUser = userPlaceholder(
        `user-${requestId}`,
        nextAction.content,
        sequence,
        0,
        new Date().toISOString(),
        nextAction.parentMessageId,
        undefined,
        conversationId.value || ""
      );
      tempUser.attachments = nextAction.attachments || [];
      allMessages.value.push(tempUser);
    }
    let aborted = false;
    let delivered = true;
    try {
      const onEvent = (event: AiStreamEvent) => {
        if (conversationId.value !== targetConversationId) return;
        applyEvent(event);
      };
      if (nextAction.type === "SEND") {
        await streamConversationMessage(
          conversationId.value,
          nextAction.content,
          selectedModelId.value,
          nextAction.parentMessageId,
          nextAction.attachments,
          activeController.signal,
          onEvent
        );
      } else if (nextAction.type === "REGENERATE") {
        await regenerateMessage(
          nextAction.target.id,
          selectedModelId.value,
          activeController.signal,
          onEvent
        );
      } else {
        await editAndResendMessage(
          nextAction.target.id,
          nextAction.content,
          selectedModelId.value,
          nextAction.attachments,
          activeController.signal,
          onEvent
        );
      }
    } catch (value) {
      if (value instanceof DOMException && value.name === "AbortError") {
        aborted = true;
      } else {
        error.value = describeError(value);
        delivered = false;
      }
    } finally {
      sending.value = false;
      if (controller === activeController) controller = null;
      activeRequestId.value = null;
      action = null;
      // stop() already refreshes after an explicit stop, and an abort during
      // teardown must not fire another load for a conversation being left.
      if (!aborted && conversationId.value === targetConversationId) {
        await refresh(targetConversationId);
      }
      // First message sent: ask the backend to derive a title from it. Decoupled from the chat
      // stream — a failure here must never break the conversation or the reply that already landed.
      if (
        isFirstUserMessage &&
        !aborted &&
        conversationId.value === targetConversationId
      ) {
        await applyAutoTitle(targetConversationId);
      }
    }
    return delivered;
  };

  const send = (
    content: string,
    attachments: FileReference[] = []
  ): Promise<boolean> => {
    const parent = messages.value[messages.value.length - 1];
    return run({
      type: "SEND",
      content,
      attachments,
      parentMessageId: parent?.id,
    });
  };
  const regenerate = (target: ChatMessage): Promise<boolean> =>
    run({ type: "REGENERATE", target });
  const editAndResend = (
    target: ChatMessage,
    content: string,
    attachments: FileReference[] = []
  ): Promise<boolean> =>
    run({ type: "EDIT_AND_RESEND", target, content, attachments });

  const stop = async (): Promise<void> => {
    if (!controller) return;
    const stoppedConversationId = conversationId.value;
    const requestId = activeRequestId.value;
    controller.abort();
    // Before `start`, disconnecting the fetch is the only request identity the
    // browser has; Spring's disconnect handler still propagates cancellation.
    if (!requestId) return;
    try {
      await stopAiRequest(requestId);
      if (stoppedConversationId) await refresh(stoppedConversationId);
    } catch (value) {
      error.value = describeError(value);
    }
  };

  const applyEvent = (event: AiStreamEvent): void => {
    if (!action) return;
    if (event.type === "start") activeRequestId.value = event.requestId;
    // All message-array mutations live in the pure reducer so they can be
    // unit tested without vuex/router. The reducer handles placeholder
    // creation/reconciliation, delta/reasoning appends, usage, done and error.
    applyStreamEvent(allMessages.value, action, event);
    if (event.type === "error") {
      error.value = `${event.message || "AI 生成失败"} · ${event.requestId}`;
    }
  };

  const refresh = async (id: string): Promise<void> => {
    try {
      const [detail, history] = await Promise.all([
        getConversation(id),
        listMessages(id),
      ]);
      conversation.value = detail;
      allMessages.value = history.items;
      await loadConversations();
    } catch (value) {
      error.value = describeError(value);
    }
  };

  /**
   * Persists the auto-generated title returned by the backend into both the current conversation and the
   * sidebar list so the rename shows instantly — no full reload, no manual refresh.
   */
  const applyAutoTitle = async (id: string): Promise<void> => {
    try {
      const updated = await generateTitle(id);
      if (conversation.value && conversation.value.id === updated.id) {
        conversation.value = { ...conversation.value, title: updated.title };
      }
      const index = conversations.value.findIndex(
        (item) => item.id === updated.id
      );
      if (index !== -1) {
        conversations.value[index] = {
          ...conversations.value[index],
          title: updated.title,
        };
      }
    } catch (value) {
      // Title generation must never break the chat. Swallow and keep the default placeholder.
      if (process.env.NODE_ENV !== "production") {
        console.warn("[TITLE_GEN_FAILED]", value);
      }
    }
  };

  /**
   * Prunes the user's empty conversations (no message ever sent), keeping `keepId` so a freshly created
   * chat is never removed before the user types. Reloads the sidebar list afterwards.
   */
  const cleanupEmptyConversations = async (keepId?: string): Promise<void> => {
    try {
      await deleteEmptyConversations(keepId);
    } catch (value) {
      if (process.env.NODE_ENV !== "production") {
        console.warn("[CLEANUP_EMPTY_FAILED]", value);
      }
    }
    await loadConversations();
  };

  onMounted(async () => {
    try {
      const [, loadedModels] = await Promise.all([
        loadConversations(),
        listAiModels(),
      ]);
      models.value = loadedModels;
      selectedModelId.value =
        loadedModels.find((model) => model.defaultModel)?.id ||
        loadedModels[0]?.id ||
        "";
      await openConversation(conversationId.value);
      // No active conversation on load: prune any historical empty rows left behind.
      if (!conversationId.value) {
        await cleanupEmptyConversations();
      }
    } catch (value) {
      error.value = describeError(value);
    }
  });
  watch(conversationId, (id) => openConversation(id));
  onUnmounted(() => {
    // Leaving the chat view must not leave the SSE request running.
    controller?.abort();
    controller = null;
  });

  return {
    conversations,
    conversation,
    conversationId,
    messages,
    models,
    selectedModelId,
    search,
    loadingConversations,
    loadingMessages,
    creating,
    sending,
    error,
    sidebarOpen,
    searchMode,
    searchHits,
    searching,
    highlightMessageId,
    loadConversations,
    runSearch,
    openSearchHit,
    exportCurrent,
    newConversation,
    cleanupEmptyConversations,
    send,
    regenerate,
    editAndResend,
    stop,
  };
}
