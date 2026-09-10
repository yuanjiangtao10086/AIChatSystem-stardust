<template>
  <div class="base-list">
    <button
      v-for="base in items"
      :key="base.id"
      type="button"
      :class="{ active: base.id === activeId }"
      @click="$emit('select', base.id)"
    >
      <span class="glyph">✦</span>
      <span
        ><strong>{{ base.name }}</strong
        ><small>{{ base.description || "暂无描述" }}</small></span
      >
      <span class="chevron">›</span>
    </button>
    <p v-if="loading" class="state">正在加载知识库…</p>
    <p v-else-if="!items.length" class="state">暂无知识库。</p>
  </div>
</template>
<script lang="ts">
import { defineComponent, PropType } from "vue";
import { KnowledgeBase } from "@/types/knowledge";
export default defineComponent({
  name: "KnowledgeBaseList",
  props: {
    items: { type: Array as PropType<KnowledgeBase[]>, required: true },
    activeId: String,
    loading: Boolean,
  },
  emits: ["select"],
});
</script>
<style scoped>
.base-list {
  display: grid;
  gap: 7px;
}
.base-list button {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 9px;
  align-items: center;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--ink);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.base-list button:hover {
  background: var(--surface-soft);
}
.base-list button.active {
  border-color: var(--line);
  background: var(--surface-soft);
}
.glyph {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border-radius: 8px;
  color: var(--ink);
  background: var(--surface);
}
strong,
small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
strong {
  font-size: 0.82rem;
}
small {
  margin-top: 3px;
  color: var(--ink-faint);
  font-size: 0.68rem;
}
.chevron {
  color: var(--ink-faint);
  font-size: 1.2rem;
}
.state {
  padding: 20px 8px;
  color: var(--ink-faint);
  font-size: 0.78rem;
}
</style>
