<template>
  <div class="composer-wrap">
    <div v-if="editingMessage" class="edit-banner">
      <span
        ><strong>正在编辑你的消息</strong
        ><small>发送后将创建新的分支，历史记录保持不变。</small></span
      ><button type="button" @click="cancelEdit">×</button>
    </div>
    <form class="composer" @submit.prevent="submit">
      <div v-if="attachments.length" class="selected-files">
        <span v-for="file in attachments" :key="file.id">
          <strong>{{ file.name }}</strong>
          <button
            type="button"
            :aria-label="`移除 ${file.name}`"
            @click="remove(file.id)"
          >
            ×
          </button>
        </span>
      </div>
      <div class="composer-row">
        <ChatAttachment v-model="attachments" :disabled="sending" />
        <textarea
          ref="input"
          v-model="draft"
          rows="1"
          maxlength="32000"
          :disabled="sending || !modelAvailable"
          :placeholder="
            modelAvailable ? '询问任何问题…' : '请先配置可用的 AI 模型'
          "
          aria-label="消息输入框"
          @input="resize"
          @keydown.enter.exact.prevent="submit"
          @keydown.shift.enter.stop
        ></textarea>
        <div class="composer-actions">
          <button
            v-if="sending"
            class="stop"
            type="button"
            @click="$emit('stop')"
          >
            <span></span>停止生成
          </button>
          <button
            v-else
            class="send"
            type="submit"
            :disabled="!draft.trim() || !modelAvailable"
            aria-label="发送消息"
          >
            ↑
          </button>
        </div>
      </div>
      <p class="composer-hint">
        <span class="hint">Shift + Enter 换行</span>
      </p>
    </form>
    <p class="disclaimer">AI 也可能出错，请核实重要信息。</p>
  </div>
</template>
<script lang="ts">
import { defineComponent, nextTick, PropType, ref, watch } from "vue";
import { ChatMessage } from "@/types/conversation";
import { FileReference } from "@/types/file";
import ChatAttachment from "./ChatAttachment.vue";
export default defineComponent({
  name: "ChatComposer",
  components: { ChatAttachment },
  props: {
    sending: Boolean,
    modelAvailable: Boolean,
    editingMessage: {
      type: Object as PropType<ChatMessage | null>,
      default: null,
    },
  },
  emits: ["send", "stop", "cancel-edit"],
  setup(props, { emit }) {
    const draft = ref("");
    const attachments = ref<FileReference[]>([]);
    const input = ref<HTMLTextAreaElement | null>(null);
    const resize = () => {
      const el = input.value;
      if (el) {
        el.style.height = "auto";
        el.style.height = `${Math.min(el.scrollHeight, 200)}px`;
      }
    };
    watch(
      () => props.editingMessage,
      (message) => {
        draft.value = message?.content || "";
        attachments.value = message?.attachments
          ? [...message.attachments]
          : [];
        nextTick(() => {
          resize();
          input.value?.focus();
        });
      }
    );
    const submit = () => {
      const content = draft.value.trim();
      if (!content || props.sending || !props.modelAvailable) return;
      emit("send", content, [...attachments.value]);
      draft.value = "";
      attachments.value = [];
      nextTick(resize);
    };
    const cancelEdit = () => {
      draft.value = "";
      attachments.value = [];
      emit("cancel-edit");
      nextTick(resize);
    };
    // Puts a failed submission back into the composer. Called by the chat view when the request
    // never reached the backend, so a failed send costs neither the text nor the picked files.
    const restore = (content: string, files: FileReference[]) => {
      draft.value = content;
      attachments.value = [...files];
      nextTick(resize);
    };
    const remove = (id: string) => {
      attachments.value = attachments.value.filter((file) => file.id !== id);
    };
    return {
      attachments,
      draft,
      input,
      resize,
      submit,
      cancelEdit,
      remove,
      restore,
    };
  },
});
</script>
<style scoped>
.composer-wrap {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 4;
  padding: 18px max(16px, calc((100% - 48rem) / 2));
  background: linear-gradient(180deg, rgba(255, 255, 255, 0), #fff 34%);
}
.composer {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 8px 8px 6px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: var(--surface);
  box-shadow: 0 10px 40px rgba(13, 13, 13, 0.1);
}
.composer:focus-within {
  border-color: rgba(13, 13, 13, 0.35);
}
/* 单行：＋ / 输入框 / 发送，垂直居中 */
.composer-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 4px;
}
textarea {
  flex: 1 1 auto;
  min-height: 46px;
  max-height: 200px;
  padding: 12px 14px;
  border: 0;
  color: var(--ink);
  background: transparent;
  font: 400 0.95rem/1.55 "Segoe UI", "PingFang SC", "Microsoft YaHei",
    sans-serif;
  resize: none;
  outline: none;
}
textarea::placeholder {
  color: var(--ink-faint);
}
.composer-actions {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
}
.composer-hint {
  margin: 2px 0 0;
  padding: 0 6px;
  text-align: right;
}
.hint {
  color: var(--ink-faint);
  font-size: 0.66rem;
}
/* 发送按钮：圆形实心品牌色，居中，无内容置灰禁用 */
.send {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 50%;
  color: #fff;
  background: var(--accent);
  font-size: 1.15rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s ease, opacity 0.15s ease;
}
.send:hover:not(:disabled) {
  background: #0c8a6a;
}
.send:disabled {
  color: #fff;
  background: #cfd3d6;
  cursor: not-allowed;
}
.stop {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 36px;
  padding: 0 13px;
  border: 0;
  border-radius: 999px;
  color: var(--ink);
  background: var(--surface-soft);
  font-size: 0.76rem;
  font-weight: 650;
  cursor: pointer;
}
.stop span {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  background: var(--ink);
}
.selected-files {
  display: flex;
  gap: 7px;
  padding: 4px 7px 7px;
  overflow-x: auto;
}
.selected-files > span {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 210px;
  padding: 6px 7px 6px 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--ink-soft);
  background: var(--canvas);
  font-size: 0.66rem;
}
.selected-files strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-files button {
  padding: 0;
  border: 0;
  color: var(--ink-faint);
  background: transparent;
  cursor: pointer;
}
.disclaimer {
  margin: 8px 0 0;
  color: var(--ink-faint);
  text-align: center;
  font-size: 11px;
}
.edit-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  margin: 0 10px -4px;
  padding: 9px 12px 13px;
  border: 1px solid var(--line);
  border-bottom: 0;
  border-radius: 14px 14px 0 0;
  background: var(--canvas);
}
.edit-banner strong,
.edit-banner small {
  display: block;
}
.edit-banner strong {
  color: var(--ink);
  font-size: 0.74rem;
}
.edit-banner small {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.64rem;
}
.edit-banner button {
  border: 0;
  color: var(--ink-soft);
  background: transparent;
  font-size: 1rem;
  cursor: pointer;
}
@media (max-width: 600px) {
  .composer-wrap {
    padding: 14px 10px;
  }
  .hint {
    display: none;
  }
}
</style>
