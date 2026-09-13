<template>
  <ChatLayout
    :sidebar-open="chat.sidebarOpen.value"
    @close-sidebar="chat.sidebarOpen.value = false"
  >
    <template #sidebar
      ><ChatSidebar
        :conversations="chat.conversations.value"
        :active-id="chat.conversationId.value"
        :search="chat.search.value"
        :search-mode="chat.searchMode.value"
        :search-hits="chat.searchHits.value"
        :searching="chat.searching.value"
        :loading="chat.loadingConversations.value"
        :creating="chat.creating.value"
        @new="chat.newConversation"
        @search="chat.runSearch"
        @update:search="chat.search.value = $event"
        @update:search-mode="onSearchModeChange"
        @open-hit="chat.openSearchHit"
        @close="chat.sidebarOpen.value = false"
    /></template>
    <ChatHeader
      :title="chat.conversation.value?.title || '新对话'"
      :status="chat.sending.value ? '正在生成回复…' : undefined"
      :models="chat.models.value"
      :model-value="chat.selectedModelId.value"
      :disabled="chat.sending.value"
      :conversation-id="chat.conversationId.value"
      @menu="chat.sidebarOpen.value = true"
      @update:model-value="chat.selectedModelId.value = $event"
      @export="chat.exportCurrent"
    />
    <ChatMessageList
      :messages="chat.messages.value"
      :loading="chat.loadingMessages.value"
      :busy="chat.sending.value"
      :initials="initials"
      :highlight-id="chat.highlightMessageId.value"
      @edit="beginEdit"
      @regenerate="chat.regenerate"
    />
    <p v-if="chat.error.value" class="chat-error" role="alert">
      {{ chat.error.value }}
    </p>
    <ChatComposer
      v-if="chat.conversationId.value"
      ref="composer"
      :sending="chat.sending.value"
      :model-available="Boolean(chat.selectedModelId.value)"
      :editing-message="editingMessage"
      @send="submit"
      @stop="chat.stop"
      @cancel-edit="editingMessage = null"
    />
    <button
      v-else
      class="empty-new"
      type="button"
      @click="chat.newConversation"
    >
      开启新对话
    </button>
  </ChatLayout>
</template>
<script lang="ts">
import { computed, defineComponent, ref } from "vue";
import type { ComponentPublicInstance } from "vue";
import { useStore } from "vuex";
import { ChatMessage } from "@/types/conversation";
import { FileReference } from "@/types/file";
import { useChatWorkspace } from "@/composables/useChatWorkspace";
import ChatLayout from "@/components/chat/ChatLayout.vue";
import ChatSidebar from "@/components/chat/ChatSidebar.vue";
import ChatHeader from "@/components/chat/ChatHeader.vue";
import ChatMessageList from "@/components/chat/ChatMessageList.vue";
import ChatComposer from "@/components/chat/ChatComposer.vue";
/** Public surface of `ChatComposer` used through a template ref. */
type ComposerInstance = ComponentPublicInstance & {
  restore: (content: string, attachments: FileReference[]) => void;
};

export default defineComponent({
  name: "ChatView",
  components: {
    ChatLayout,
    ChatSidebar,
    ChatHeader,
    ChatMessageList,
    ChatComposer,
  },
  setup() {
    const chat = useChatWorkspace();
    const store = useStore();
    const editingMessage = ref<ChatMessage | null>(null);
    const initials = computed(
      () =>
        store.state.auth.user?.displayName?.trim().slice(0, 2).toUpperCase() ||
        "我"
    );
    const beginEdit = (message: ChatMessage) => {
      editingMessage.value = message;
    };
    // The composer clears its draft and picked attachments as soon as a message is submitted. When
    // the request never reached the backend the user must get both back instead of losing them.
    const composer = ref<ComposerInstance | null>(null);
    const submit = async (content: string, attachments: FileReference[]) => {
      const target = editingMessage.value;
      editingMessage.value = null;
      const delivered = target
        ? await chat.editAndResend(target, content, attachments)
        : await chat.send(content, attachments);
      if (!delivered) composer.value?.restore(content, attachments);
    };
    // Switching the search scope must immediately re-run it: leaving stale "content" hits under a
    // "title" search (or the other way round) would silently show the wrong list.
    const onSearchModeChange = (mode: "title" | "content") => {
      chat.searchMode.value = mode;
      void chat.runSearch();
    };
    return {
      chat,
      composer,
      editingMessage,
      initials,
      beginEdit,
      submit,
      onSearchModeChange,
    };
  },
});
</script>
<style scoped>
.chat-error {
  position: absolute;
  right: 18px;
  bottom: 132px;
  z-index: 8;
  max-width: min(520px, calc(100% - 36px));
  margin: 0;
  padding: 9px 12px;
  border: 1px solid #efcfcd;
  border-radius: 9px;
  color: #8d413e;
  background: #fff6f5;
  box-shadow: 0 8px 24px rgba(80, 37, 35, 0.1);
  font-size: 0.73rem;
}
.empty-new {
  position: absolute;
  bottom: 42px;
  left: 50%;
  transform: translateX(-50%);
  padding: 11px 18px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: var(--ink);
  font-weight: 650;
  cursor: pointer;
}
</style>
