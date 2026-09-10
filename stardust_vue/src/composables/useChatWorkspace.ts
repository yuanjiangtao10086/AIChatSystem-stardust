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
  regenerateMessage,
  stopAiRequest,
  streamConversationMessage,
} from "@/api/conversations";
import { ApiError } from "@/api/client";
import {
  AiModel,
  AiStreamEvent,
  ChatMessage,
  Conversation,
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

  const run = async (nextAction: ChatAction): Promise<void> => {
    if (!conversationId.value || !selectedModelId.value || sending.value)
      return;
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
  };

  const send = (
    content: string,
    attachments: FileReference[] = []
  ): Promise<void> => {
    const parent = messages.value[messages.value.length - 1];
    return run({
      type: "SEND",
      content,
      attachments,
      parentMessageId: parent?.id,
    });
  };
  const regenerate = (target: ChatMessage): Promise<void> =>
    run({ type: "REGENERATE", target });
  const editAndResend = (
    target: ChatMessage,
    content: string,
    attachments: FileReference[] = []
  ): Promise<void> =>
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
    loadConversations,
    newConversation,
    cleanupEmptyConversations,
    send,
    regenerate,
    editAndResend,
    stop,
  };
}
