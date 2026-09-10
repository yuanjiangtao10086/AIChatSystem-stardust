<template>
  <details ref="panel" class="source-picker">
    <summary :aria-label="`已选择 ${selected.length} 个知识库`">
      <span aria-hidden="true">◇</span> 知识库
      <b v-if="selected.length">{{ selected.length }}</b>
    </summary>
    <div class="source-menu">
      <p>为本次对话挂载知识库</p>
      <label v-for="base in bases" :key="base.id">
        <input
          type="checkbox"
          :checked="selected.includes(base.id)"
          :disabled="disabled || saving"
          @change="toggle(base.id)"
        />
        <span
          ><strong>{{ base.name }}</strong
          ><small>{{ base.description || "个人知识库" }}</small></span
        >
      </label>
      <small v-if="!bases.length">请先创建一个知识库。</small>
      <button
        type="button"
        :disabled="disabled || saving || !conversationId"
        @click="save"
      >
        {{ saving ? "正在应用…" : "应用" }}
      </button>
      <router-link to="/knowledge">管理知识库</router-link>
      <span v-if="error" class="error" role="alert">{{ error }}</span>
    </div>
  </details>
</template>

<script lang="ts">
import { defineComponent, ref, watch } from "vue";
import {
  getConversationKnowledgeBases,
  listKnowledgeBases,
  setConversationKnowledgeBases,
} from "@/api/knowledge";
import { ApiError } from "@/api/client";
import { KnowledgeBase } from "@/types/knowledge";

export default defineComponent({
  name: "KnowledgeBaseSelector",
  props: { conversationId: String, disabled: Boolean },
  setup(props) {
    const bases = ref<KnowledgeBase[]>([]);
    const selected = ref<string[]>([]);
    const saving = ref(false);
    const error = ref("");
    const panel = ref<HTMLDetailsElement | null>(null);
    const load = async () => {
      error.value = "";
      try {
        bases.value = (await listKnowledgeBases({ size: 100 })).items;
        selected.value = props.conversationId
          ? (
              await getConversationKnowledgeBases(props.conversationId)
            ).items.map((item) => item.id)
          : [];
      } catch (cause) {
        error.value =
          cause instanceof ApiError ? cause.message : "无法加载知识库列表。";
      }
    };
    const toggle = (id: string) => {
      selected.value = selected.value.includes(id)
        ? selected.value.filter((value) => value !== id)
        : [...selected.value, id];
    };
    const save = async () => {
      if (!props.conversationId) return;
      saving.value = true;
      error.value = "";
      try {
        const result = await setConversationKnowledgeBases(
          props.conversationId,
          selected.value
        );
        selected.value = result.items.map((item) => item.id);
        if (panel.value) panel.value.open = false;
      } catch (cause) {
        error.value =
          cause instanceof ApiError ? cause.message : "无法应用所选知识库。";
      } finally {
        saving.value = false;
      }
    };
    watch(() => props.conversationId, load, { immediate: true });
    return { bases, error, panel, save, saving, selected, toggle };
  },
});
</script>

<style scoped>
.source-picker {
  position: relative;
}
.source-picker summary {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-soft);
  background: var(--surface);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  list-style: none;
}
.source-picker summary::-webkit-details-marker {
  display: none;
}
.source-picker summary > span {
  color: var(--ink);
}
.source-picker summary b {
  display: grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  color: #fff;
  background: var(--ink);
  font-size: 0.62rem;
}
.source-menu {
  position: absolute;
  z-index: 25;
  top: 44px;
  right: 0;
  display: grid;
  gap: 8px;
  width: min(320px, 82vw);
  max-height: 390px;
  padding: 14px;
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: 0 18px 50px rgba(13, 13, 13, 0.14);
}
.source-menu > p {
  margin: 0 0 4px;
  color: var(--ink);
  font-size: 0.8rem;
  font-weight: 700;
}
.source-menu label {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 9px;
  align-items: start;
  padding: 9px 4px;
  border-top: 1px solid var(--line);
  cursor: pointer;
}
.source-menu input {
  margin-top: 3px;
  accent-color: var(--ink);
}
.source-menu strong,
.source-menu small {
  display: block;
}
.source-menu strong {
  overflow: hidden;
  font-size: 0.78rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-menu small {
  margin-top: 2px;
  overflow: hidden;
  color: var(--ink-faint);
  font-size: 0.66rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-menu button {
  width: 100%;
  margin-top: 8px;
  padding: 9px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: var(--ink);
  cursor: pointer;
}
.source-menu button:disabled {
  opacity: 0.55;
  cursor: default;
}
.source-menu a {
  display: block;
  margin-top: 10px;
  color: var(--ink-soft);
  text-align: center;
  font-size: 0.72rem;
  text-decoration: none;
}
.error {
  color: #b3271d;
  font-size: 0.7rem;
  line-height: 1.4;
}
@media (max-width: 620px) {
  .source-picker summary {
    padding: 0 8px;
  }
  .source-picker summary span {
    display: none;
  }
}
</style>
