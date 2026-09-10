<template>
  <header class="chat-header">
    <button
      class="menu-button"
      type="button"
      aria-label="打开对话列表"
      @click="$emit('menu')"
    >
      ☰
    </button>
    <div class="title">
      <strong>{{ title }}</strong
      ><span v-if="status">{{ status }}</span>
    </div>
    <KnowledgeBaseSelector
      :conversation-id="conversationId"
      :disabled="disabled"
    />
    <ModelSelector
      :models="models"
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="$emit('update:modelValue', $event)"
    />
  </header>
</template>
<script lang="ts">
import { defineComponent, PropType } from "vue";
import { AiModel } from "@/types/conversation";
import ModelSelector from "./ModelSelector.vue";
import KnowledgeBaseSelector from "./KnowledgeBaseSelector.vue";
export default defineComponent({
  name: "ChatHeader",
  components: { ModelSelector, KnowledgeBaseSelector },
  props: {
    title: { type: String, default: "新对话" },
    status: String,
    models: { type: Array as PropType<AiModel[]>, required: true },
    modelValue: { type: String, required: true },
    disabled: Boolean,
    conversationId: String,
  },
  emits: ["menu", "update:modelValue"],
});
</script>
<style scoped>
.chat-header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 8px 18px;
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
}
.menu-button {
  display: none;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 10px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font-size: 1rem;
  cursor: pointer;
}
.title {
  min-width: 0;
}
.title strong,
.title span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.title strong {
  color: var(--ink);
  font-size: 0.9rem;
}
.title span {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.68rem;
}
@media (max-width: 760px) {
  .chat-header {
    padding: 8px 11px;
  }
  .menu-button {
    display: block;
  }
  .title span {
    display: none;
  }
}
@media (max-width: 520px) {
  .title {
    display: none;
  }
  .chat-header {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }
}
</style>
