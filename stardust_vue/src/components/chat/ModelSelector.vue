<template>
  <label class="model-select"
    ><span>模型</span
    ><select
      :value="modelValue"
      :disabled="disabled"
      @change="
        $emit('update:modelValue', ($event.target as HTMLSelectElement).value)
      "
    >
      <option value="" disabled>尚未配置模型</option>
      <option v-for="model in models" :key="model.id" :value="model.id">
        {{ model.displayName }}
      </option>
    </select></label
  >
</template>
<script lang="ts">
import { defineComponent, PropType } from "vue";
import { AiModel } from "@/types/conversation";
export default defineComponent({
  name: "ModelSelector",
  props: {
    models: { type: Array as PropType<AiModel[]>, required: true },
    modelValue: { type: String, required: true },
    disabled: Boolean,
  },
  emits: ["update:modelValue"],
});
</script>
<style scoped>
.model-select {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-soft);
  font-size: 0.72rem;
  font-weight: 600;
}
.model-select > span {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
select {
  max-width: 230px;
  padding: 8px 30px 8px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  color: var(--ink);
  background: var(--surface-soft);
  font: 600 0.82rem "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  cursor: pointer;
}
select:hover,
select:focus {
  border-color: var(--line);
  outline: none;
}
select:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
