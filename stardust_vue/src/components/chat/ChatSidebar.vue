<template>
  <aside class="chat-sidebar">
    <div class="sidebar-top">
      <router-link class="wordmark" to="/chat"
        ><span>✦</span>星语 AI</router-link
      ><button
        class="close-sidebar"
        type="button"
        aria-label="收起侧栏"
        @click="$emit('close')"
      >
        ×
      </button>
    </div>
    <button
      class="new-chat"
      type="button"
      :disabled="creating"
      @click="$emit('new')"
    >
      <span aria-hidden="true">✎</span>开启新对话
    </button>
    <label class="search-box"
      ><span aria-hidden="true">⌕</span
      ><input
        :value="search"
        type="search"
        :placeholder="searchPlaceholder"
        :aria-label="searchPlaceholder"
        @input="
          $emit('update:search', ($event.target as HTMLInputElement).value)
        "
        @keyup.enter="$emit('search')"
    /></label>
    <div class="search-mode" role="group" aria-label="搜索范围">
      <button
        type="button"
        :class="{ active: searchMode === 'title' }"
        @click="$emit('update:searchMode', 'title')"
      >
        标题
      </button>
      <button
        type="button"
        :class="{ active: searchMode === 'content' }"
        @click="$emit('update:searchMode', 'content')"
      >
        内容
      </button>
    </div>
    <div v-if="searchMode === 'content'" class="hit-list">
      <p v-if="searching" class="hit-hint">正在搜索…</p>
      <p v-else-if="!search.trim()" class="hit-hint">
        输入关键词后按回车，搜索全部对话的消息内容。
      </p>
      <p v-else-if="!searchHits.length" class="hit-hint">没有匹配的消息。</p>
      <template v-else>
        <button
          v-for="hit in searchHits"
          :key="hit.messageId"
          type="button"
          class="hit"
          @click="$emit('open-hit', hit)"
        >
          <strong class="hit-title">{{ hit.conversationTitle }}</strong>
          <span class="hit-snippet">{{ hit.snippet }}</span>
          <span class="hit-meta"
            >{{ hit.role === "USER" ? "你" : "星语" }} ·
            {{ formatTime(hit.createdAt) }}</span
          >
        </button>
      </template>
    </div>
    <ConversationList
      v-else
      :conversations="conversations"
      :active-id="activeId"
      :loading="loading"
      @select="$emit('close')"
    />
    <UsagePanel compact />
    <details class="user-menu">
      <summary>
        <span class="avatar">{{ initials }}</span
        ><span
          ><strong>{{ user?.displayName || "我的账户" }}</strong
          ><small>{{ user?.email }}</small></span
        ><span aria-hidden="true">•••</span>
      </summary>
      <div>
        <router-link to="/files">云盘</router-link
        ><router-link to="/knowledge">知识库</router-link
        ><router-link to="/memories">记忆管理</router-link
        ><router-link to="/profile">个人设置</router-link
        ><router-link v-if="canAdmin" to="/admin">管理后台</router-link
        ><button type="button" @click="toggleTheme">
          {{ isDark ? "浅色模式" : "深色模式" }}
        </button>
        <button type="button" @click="logout">退出登录</button>
      </div>
    </details>
  </aside>
</template>
<script lang="ts">
import { computed, defineComponent, PropType } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import ConversationList from "./ConversationList.vue";
import UsagePanel from "@/components/usage/UsagePanel.vue";
import { Conversation, MessageSearchHit } from "@/types/conversation";
import { UserProfile } from "@/types/auth";
import { useTheme } from "@/composables/useTheme";
export default defineComponent({
  name: "ChatSidebar",
  components: { ConversationList, UsagePanel },
  props: {
    conversations: { type: Array as PropType<Conversation[]>, required: true },
    activeId: String,
    search: { type: String, required: true },
    searchMode: {
      type: String as PropType<"title" | "content">,
      default: "title",
    },
    searchHits: {
      type: Array as PropType<MessageSearchHit[]>,
      default: () => [],
    },
    loading: Boolean,
    creating: Boolean,
    searching: Boolean,
  },
  emits: [
    "new",
    "search",
    "update:search",
    "update:searchMode",
    "open-hit",
    "close",
  ],
  setup(props) {
    const store = useStore();
    const router = useRouter();
    const { isDark, toggle } = useTheme();
    const user = computed(() => store.state.auth.user as UserProfile | null);
    const canAdmin = computed(
      () => store.getters["auth/canAccessAdmin"] as boolean
    );
    const initials = computed(
      () => user.value?.displayName.trim().slice(0, 2).toUpperCase() || "我"
    );
    const formatTime = (value: string): string =>
      new Date(value).toLocaleString("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      });
    const searchPlaceholder = computed(() =>
      props.searchMode === "content" ? "搜索聊天内容" : "搜索对话标题"
    );
    const logout = async () => {
      await store.dispatch("auth/logout");
      await router.replace({ name: "login" });
    };
    return {
      user,
      canAdmin,
      initials,
      formatTime,
      searchPlaceholder,
      isDark,
      toggleTheme: toggle,
      logout,
    };
  },
});
</script>
<style scoped>
.chat-sidebar {
  display: grid;
  grid-template-rows: auto auto auto auto minmax(0, 1fr) auto auto;
  gap: 8px;
  height: 100%;
  padding: 14px 10px 10px;
  color: var(--ink);
  background: var(--sidebar);
}
.sidebar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 10px;
}
.wordmark {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink);
  font-size: 0.98rem;
  font-weight: 700;
  text-decoration: none;
}
.wordmark span {
  font-size: 1.05rem;
}
.close-sidebar {
  display: none;
  border: 0;
  color: var(--ink-soft);
  background: transparent;
  font-size: 1.5rem;
  cursor: pointer;
}
.new-chat {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 42px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 12px;
  color: var(--ink);
  background: var(--surface);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}
.new-chat:hover {
  background: var(--surface-soft);
}
.new-chat:disabled {
  opacity: 0.6;
  cursor: wait;
}
.new-chat span {
  font-size: 0.95rem;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--ink-faint);
  background: var(--surface-soft);
  transition: border-color 0.15s ease;
}
.search-box:focus-within {
  border-color: var(--line);
}
.search-box input {
  min-height: 38px;
  padding: 0;
  border: 0;
  color: var(--ink);
  background: transparent;
  font-size: 0.84rem;
  outline: none;
}
.search-box input::placeholder {
  color: var(--ink-faint);
}
.search-mode {
  display: flex;
  gap: 6px;
  padding: 0 2px;
}
.search-mode button {
  padding: 4px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-soft);
  background: transparent;
  font-size: 0.72rem;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.search-mode button:hover {
  background: var(--surface-soft);
}
.search-mode button.active {
  border-color: var(--ink);
  color: var(--paper);
  background: var(--ink);
}
.hit-list {
  display: grid;
  align-content: start;
  gap: 6px;
  overflow-y: auto;
  min-height: 0;
}
.hit-hint {
  margin: 4px 2px;
  color: var(--ink-faint);
  font-size: 0.74rem;
  line-height: 1.5;
}
.hit {
  display: grid;
  gap: 3px;
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}
.hit:hover {
  background: var(--surface-soft);
}
.hit-title {
  overflow: hidden;
  color: var(--ink);
  font-size: 0.78rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hit-snippet {
  display: -webkit-box;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 0.72rem;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.hit-meta {
  color: var(--ink-faint);
  font-size: 0.66rem;
}
.user-menu {
  position: relative;
  border-top: 1px solid var(--line);
  padding-top: 8px;
}
.user-menu summary {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 9px;
  align-items: center;
  padding: 8px;
  border-radius: 12px;
  cursor: pointer;
  list-style: none;
}
.user-menu summary::-webkit-details-marker {
  display: none;
}
.user-menu summary:hover {
  background: var(--surface-soft);
}
.avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #3d3d4d, #0d0d0d);
  font-size: 0.68rem;
  font-weight: 700;
}
.user-menu strong,
.user-menu small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-menu strong {
  font-size: 0.82rem;
}
.user-menu small {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.66rem;
}
.user-menu summary > span:last-child {
  color: var(--ink-faint);
  font-size: 0.7rem;
}
.user-menu div {
  position: absolute;
  right: 0;
  bottom: 52px;
  left: 0;
  display: grid;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: 0 16px 40px rgba(13, 13, 13, 0.14);
}
.user-menu a,
.user-menu button {
  padding: 9px;
  border: 0;
  border-radius: 9px;
  color: var(--ink);
  background: transparent;
  text-align: left;
  text-decoration: none;
  font-size: 0.82rem;
  cursor: pointer;
}
.user-menu a:hover,
.user-menu button:hover {
  background: var(--surface-soft);
}
@media (max-width: 760px) {
  .close-sidebar {
    display: block;
  }
}
</style>
