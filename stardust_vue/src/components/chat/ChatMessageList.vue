<template>
  <section
    ref="scroller"
    class="message-scroller"
    aria-live="polite"
    @scroll="onScroll"
  >
    <div v-if="loading" class="chat-state">正在加载对话…</div>
    <div v-else-if="!messages.length" class="welcome">
      <div class="signal">✦</div>
      <h1>今天有什么计划？</h1>
      <p>提问、探讨想法，或把一个复杂的问题交给星语 AI 一起拆解。</p>
    </div>
    <div v-else class="message-list">
      <ChatMessage
        v-for="message in messages"
        :key="message.id"
        :message="message"
        :initials="initials"
        :busy="busy"
        @edit="$emit('edit', $event)"
        @regenerate="$emit('regenerate', $event)"
      />
    </div>
    <button
      v-if="!pinned"
      class="jump-bottom"
      type="button"
      @click="scrollToBottom(true)"
      aria-label="回到底部"
    >
      ↓
    </button>
  </section>
</template>
<script lang="ts">
import { defineComponent, nextTick, PropType, ref, watch } from "vue";
import { ChatMessage as Message } from "@/types/conversation";
import ChatMessage from "./ChatMessage.vue";
export default defineComponent({
  name: "ChatMessageList",
  components: { ChatMessage },
  props: {
    messages: { type: Array as PropType<Message[]>, required: true },
    loading: Boolean,
    busy: Boolean,
    initials: String,
  },
  emits: ["edit", "regenerate"],
  setup(props) {
    const scroller = ref<HTMLElement | null>(null);
    const pinned = ref(true);
    const onScroll = () => {
      const el = scroller.value;
      if (el)
        pinned.value = el.scrollHeight - el.scrollTop - el.clientHeight < 110;
    };
    const scrollToBottom = (force = false) => {
      if (!force && !pinned.value) return;
      nextTick(() => {
        const el = scroller.value;
        if (el)
          el.scrollTo({
            top: el.scrollHeight,
            behavior: force ? "smooth" : "auto",
          });
      });
    };
    watch(
      () =>
        props.messages
          .map(
            (m) =>
              `${m.id}:${m.content.length}:${m.reasoningContent?.length || 0}`
          )
          .join("|"),
      () => scrollToBottom()
    );
    watch(
      () => props.messages[0]?.conversationId,
      () => {
        pinned.value = true;
        scrollToBottom();
      }
    );
    return { scroller, pinned, onScroll, scrollToBottom };
  },
});
</script>
<style scoped>
.message-scroller {
  position: relative;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}
.message-list {
  padding: 14px 0 165px;
}
.chat-state,
.welcome {
  display: grid;
  place-items: center;
  min-height: 100%;
  padding: 50px 24px;
  text-align: center;
}
.chat-state {
  color: var(--ink-faint);
}
.welcome {
  align-content: center;
}
.signal {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: 22px;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--ink);
  background: var(--canvas);
  font-size: 1.1rem;
}
.welcome h1 {
  margin: 0;
  color: var(--ink);
  font-size: clamp(1.7rem, 4vw, 2.35rem);
  font-weight: 700;
  letter-spacing: -0.02em;
}
.welcome p {
  max-width: 420px;
  margin: 12px 0 0;
  color: var(--ink-soft);
  font-size: 0.92rem;
  line-height: 1.65;
}
.jump-bottom {
  position: sticky;
  bottom: 140px;
  left: calc(50% - 18px);
  width: 36px;
  height: 36px;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--ink-soft);
  background: var(--surface);
  box-shadow: 0 8px 22px rgba(13, 13, 13, 0.12);
  cursor: pointer;
}
</style>
