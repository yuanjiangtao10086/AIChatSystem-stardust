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
    <details ref="exportMenu" class="export-menu">
      <summary :aria-disabled="!conversationId">导出</summary>
      <div>
        <p v-if="!conversationId" class="export-hint">
          请先创建或打开一个对话后再导出
        </p>
        <button type="button" @click="emitExport('MARKDOWN')">Markdown</button>
        <button type="button" @click="emitExport('JSON')">JSON</button>
      </div>
    </details>
  </header>
</template>
<script lang="ts">
import { defineComponent, PropType, ref, onMounted, onUnmounted } from "vue";
import { AiModel, ConversationExportFormat } from "@/types/conversation";
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
  emits: ["menu", "update:modelValue", "export"],
  setup(_props, { emit }) {
    const exportMenu = ref<HTMLDetailsElement | null>(null);
    const emitExport = (format: ConversationExportFormat): void => {
      // Collapse the popover right away: the browser would otherwise leave it open behind the download.
      if (exportMenu.value) exportMenu.value.open = false;
      emit("export", format);
    };
    // <details> only toggles on its <summary>; it does not close when clicking elsewhere. Close it on any
    // outside click or Escape so the format menu behaves like a normal popover.
    const onDocumentClick = (event: MouseEvent): void => {
      const el = exportMenu.value;
      if (el && el.open && !el.contains(event.target as Node | null)) {
        el.open = false;
      }
    };
    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") {
        if (exportMenu.value) exportMenu.value.open = false;
      }
    };
    onMounted(() => {
      document.addEventListener("click", onDocumentClick);
      document.addEventListener("keydown", onKeyDown);
    });
    onUnmounted(() => {
      document.removeEventListener("click", onDocumentClick);
      document.removeEventListener("keydown", onKeyDown);
    });
    return { exportMenu, emitExport };
  },
});
</script>
<style scoped>
.chat-header {
  position: relative;
  z-index: 10;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 8px 18px;
  border-bottom: 1px solid var(--line);
  background: var(--topbar-bg);
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
    grid-template-columns: auto auto auto auto;
  }
}
.export-menu {
  position: relative;
}
.export-menu summary {
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-soft);
  font-size: 0.78rem;
  cursor: pointer;
  list-style: none;
  white-space: nowrap;
}
.export-menu summary::-webkit-details-marker {
  display: none;
}
.export-menu summary[aria-disabled="true"] {
  opacity: 0.5;
  cursor: not-allowed;
}
.export-menu div {
  position: absolute;
  top: 40px;
  right: 0;
  z-index: 6;
  display: grid;
  min-width: 132px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow);
}
.export-menu .export-hint {
  margin: 0 0 4px;
  padding: 6px 10px;
  color: var(--ink-faint);
  font-size: 0.72rem;
  line-height: 1.4;
}
.export-menu button {
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  color: var(--ink);
  background: transparent;
  text-align: left;
  font-size: 0.8rem;
  cursor: pointer;
}
.export-menu button:hover:not(:disabled) {
  background: var(--surface-soft);
}
.export-menu button:disabled {
  color: var(--ink-faint);
  cursor: not-allowed;
}
</style>
