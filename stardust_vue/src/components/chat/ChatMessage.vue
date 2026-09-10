<template>
  <article class="chat-message" :class="`role-${message.role.toLowerCase()}`">
    <div
      class="message-avatar"
      :class="`avatar-${message.role.toLowerCase()}`"
      aria-hidden="true"
    >
      <img
        v-if="avatarUrl && !avatarFailed"
        class="avatar-img"
        :src="avatarUrl"
        alt=""
        @error="avatarFailed = true"
      />
      <span v-else class="avatar-fallback">{{
        message.role === "USER" ? initials : "✦"
      }}</span>
    </div>
    <div class="message-body">
      <div class="message-label">
        <strong>{{ message.role === "USER" ? "你" : "星语" }}</strong>
        <span v-if="message.variantNo" class="variant-tag"
          >版本 {{ message.variantNo + 1 }}</span
        >
      </div>

      <p v-if="message.role === 'USER'" class="user-copy">
        {{ message.content }}
      </p>

      <div
        v-else-if="message.content || message.status === 'STREAMING'"
        class="ai-bubble"
      >
        <ChatMarkdown v-if="message.content" :content="message.content" />
        <p v-else-if="message.status === 'STREAMING'" class="thinking">
          正在思考<StreamingCursor />
        </p>
      </div>

      <details v-if="message.reasoningContent" class="reasoning">
        <summary>推理过程</summary>
        <p>{{ message.reasoningContent }}</p>
      </details>
      <p v-if="message.errorMessage" class="message-error">
        {{ message.errorMessage }}
      </p>

      <MessageAttachments
        v-if="message.attachments?.length"
        :files="message.attachments"
      />

      <div class="message-actions">
        <button type="button" @click="copy">
          {{ copied ? "已复制" : "复制" }}
        </button>
        <button
          v-if="message.role === 'USER' && message.status === 'COMPLETED'"
          type="button"
          :disabled="busy"
          @click="$emit('edit', message)"
        >
          编辑
        </button>
        <button
          v-if="message.role === 'ASSISTANT' && message.status !== 'STREAMING'"
          type="button"
          :disabled="busy"
          @click="$emit('regenerate', message)"
        >
          重新生成
        </button>
        <span v-if="message.totalTokens" class="token-count"
          >{{ message.totalTokens }} token</span
        ><span v-if="message.status !== 'COMPLETED'" class="status">{{
          statusLabel
        }}</span>
      </div>
    </div>
  </article>
</template>
<script lang="ts">
import { computed, defineComponent, PropType, ref } from "vue";
import { ChatMessage as Message } from "@/types/conversation";
import ChatMarkdown from "./ChatMarkdown.vue";
import StreamingCursor from "./StreamingCursor.vue";
import MessageAttachments from "./MessageAttachments.vue";

const STATUS_LABELS: Record<string, string> = {
  streaming: "生成中",
  pending: "排队中",
  completed: "已完成",
  failed: "失败",
  cancelled: "已停止",
};

export default defineComponent({
  name: "ChatMessage",
  components: { ChatMarkdown, StreamingCursor, MessageAttachments },
  props: {
    message: { type: Object as PropType<Message>, required: true },
    initials: { type: String, default: "我" },
    busy: Boolean,
    avatarUrl: { type: String, default: "" },
  },
  emits: ["edit", "regenerate"],
  setup(props) {
    const copied = ref(false);
    const avatarFailed = ref(false);
    const statusLabel = computed(
      () =>
        STATUS_LABELS[props.message.status.toLowerCase()] ||
        props.message.status
    );
    const copy = async () => {
      await navigator.clipboard.writeText(props.message.content);
      copied.value = true;
      window.setTimeout(() => (copied.value = false), 1500);
    };
    return { copied, copy, statusLabel, avatarFailed };
  },
});
</script>
<style scoped>
.chat-message {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 16px;
  width: min(100%, 48rem);
  margin: 0 auto;
  padding: 18px 20px;
}
.chat-message + .chat-message {
  border-top: 1px solid var(--line);
}
/* 用户消息：右侧，头像在右 */
.role-user {
  grid-template-columns: minmax(0, 1fr) 32px;
}
.role-user .message-avatar {
  order: 2;
}
.role-user .message-body {
  order: 1;
  justify-self: end;
  max-width: 85%;
}
.role-user .message-label {
  justify-content: flex-end;
}
/* 头像：圆形 32px，图片不变形，加载失败兜底首字符 */
.message-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  overflow: hidden;
  border-radius: 50%;
  color: var(--ink-soft);
  background: var(--canvas);
  font-size: 0.8rem;
  font-weight: 700;
  user-select: none;
}
.avatar-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
}
.avatar-assistant {
  color: var(--ink);
  background: var(--surface-soft);
}
.message-body {
  min-width: 0;
}
/* 昵称：14px / 500；“版本 N” 小号次要色胶囊 */
.message-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 2px 0 10px;
}
.message-label strong {
  font-size: 14px;
  font-weight: 500;
  color: var(--ink);
}
.variant-tag {
  padding: 1px 8px;
  border-radius: 999px;
  color: var(--ink-faint);
  background: var(--surface-soft);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.6;
}
/* 用户气泡：品牌色实心 */
.user-copy {
  margin: 0;
  padding: 12px 16px;
  border-radius: 18px;
  color: #fff;
  background: var(--accent);
  font-size: 0.96rem;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
/* AI 气泡：浅灰圆角 */
.ai-bubble {
  padding: 12px 16px;
  border-radius: 18px;
  background: var(--surface-soft);
  color: var(--ink);
  font-size: 0.96rem;
  line-height: 1.7;
}
.ai-bubble :deep(.chat-markdown) {
  font-size: 0.96rem;
  line-height: 1.7;
}
.ai-bubble :deep(.chat-markdown) > :first-child {
  margin-top: 0;
}
.ai-bubble :deep(.chat-markdown) > :last-child {
  margin-bottom: 0;
}
.thinking {
  margin: 0;
  color: var(--ink-soft);
  font-size: 0.92rem;
}
.reasoning {
  margin: 12px 0;
  padding: 10px 12px;
  border-left: 2px solid var(--ink-faint);
  color: var(--ink-soft);
  background: var(--canvas);
  font-size: 0.78rem;
}
.reasoning summary {
  cursor: pointer;
  font-weight: 650;
}
.reasoning p {
  margin: 8px 0 0;
  white-space: pre-wrap;
}
.message-error {
  margin: 10px 0 0;
  color: #b3271d;
  font-size: 0.78rem;
}
/* 操作按钮：默认淡显，hover 浮现，位于消息左下 */
.message-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 26px;
  margin-top: 10px;
  opacity: 0.5;
  transition: opacity 0.15s ease;
  color: var(--ink-faint);
  font-size: 0.7rem;
}
.chat-message:hover .message-actions {
  opacity: 1;
}
.message-actions button {
  padding: 5px 8px;
  border: 0;
  border-radius: 8px;
  color: var(--ink-soft);
  background: transparent;
  font-size: 0.72rem;
  cursor: pointer;
}
.message-actions button:hover {
  color: var(--ink);
  background: var(--surface-soft);
}
.message-actions button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.token-count {
  margin-left: 6px;
  color: var(--ink-faint);
  font-size: 11px;
}
.message-actions .status {
  margin-left: 6px;
  color: #b0672f;
}
@media (max-width: 600px) {
  .chat-message {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 12px;
    padding: 16px 14px;
  }
  .role-user {
    grid-template-columns: minmax(0, 1fr) 28px;
  }
  .role-user .message-body {
    max-width: 92%;
  }
  .message-avatar {
    width: 28px;
    height: 28px;
  }
}
</style>
