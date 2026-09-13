<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
    :title="isDark ? '切换到浅色模式' : '切换到深色模式'"
    @click="toggle"
  >
    <span aria-hidden="true">{{ isDark ? "☀" : "☾" }}</span>
    <span v-if="withLabel" class="theme-toggle-label">{{
      isDark ? "浅色模式" : "深色模式"
    }}</span>
  </button>
</template>
<script lang="ts">
import { defineComponent } from "vue";
import { useTheme } from "@/composables/useTheme";

export default defineComponent({
  name: "ThemeToggle",
  props: {
    withLabel: Boolean,
  },
  setup() {
    const { isDark, toggle } = useTheme();
    return { isDark, toggle };
  },
});
</script>
<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink);
  background: transparent;
  font-size: 0.8rem;
  cursor: pointer;
  transition: background 0.15s ease;
}
.theme-toggle:hover {
  background: var(--surface-soft);
}
.theme-toggle-label {
  white-space: nowrap;
}
</style>
