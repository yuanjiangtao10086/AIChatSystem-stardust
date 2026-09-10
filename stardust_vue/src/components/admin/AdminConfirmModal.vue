<template>
  <AdminModal
    :model-value="modelValue"
    :title="title"
    :error="error"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="admin-confirm-msg">{{ message }}</p>
    <template #footer>
      <button
        class="quiet-button"
        type="button"
        :disabled="busy"
        @click="close"
      >
        取消
      </button>
      <button
        class="danger-button"
        type="button"
        :disabled="busy"
        @click="$emit('confirm')"
      >
        {{ busy ? "处理中…" : confirmText }}
      </button>
    </template>
  </AdminModal>
</template>
<script lang="ts">
import { defineComponent } from "vue";
import AdminModal from "@/components/admin/AdminModal.vue";

export default defineComponent({
  name: "AdminConfirmModal",
  components: { AdminModal },
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: "请确认" },
    message: { type: String, default: "" },
    confirmText: { type: String, default: "确认" },
    busy: { type: Boolean, default: false },
    error: { type: String, default: "" },
  },
  emits: ["update:modelValue", "confirm"],
  setup(_, { emit }) {
    return { close: () => emit("update:modelValue", false) };
  },
});
</script>
<style scoped>
.admin-confirm-msg {
  margin: 0;
  color: #34405a;
  line-height: 1.6;
}
.danger-button {
  min-height: 42px;
  padding: 0 20px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: var(--danger, #ef4146);
  font-weight: 650;
  cursor: pointer;
}
.danger-button:disabled {
  opacity: 0.55;
  cursor: wait;
}
</style>
