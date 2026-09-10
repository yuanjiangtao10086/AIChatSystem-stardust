<template>
  <AdminModal
    :model-value="modelValue"
    :title="`重置密码 · ${user?.displayName || ''}`"
    :error="error"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="admin-hint">
      重置后该用户全部登录会话会被吊销，需使用新密码重新登录。
    </p>
    <form @submit.prevent="submit">
      <label>
        新密码（≥12 位）
        <input
          v-model="password"
          type="text"
          autocomplete="new-password"
          :disabled="busy"
        />
      </label>
    </form>
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
        class="primary-button"
        type="button"
        :disabled="busy || password.length < 12"
        @click="submit"
      >
        {{ busy ? "提交中…" : "重置密码" }}
      </button>
    </template>
  </AdminModal>
</template>
<script lang="ts">
import { defineComponent, PropType, ref, watch } from "vue";
import AdminModal from "@/components/admin/AdminModal.vue";
import { AdminUser } from "@/types/admin";

export default defineComponent({
  name: "AdminPasswordModal",
  components: { AdminModal },
  props: {
    modelValue: { type: Boolean, default: false },
    user: { type: Object as PropType<AdminUser | null>, default: null },
    busy: { type: Boolean, default: false },
    error: { type: String, default: "" },
  },
  emits: ["update:modelValue", "submit"],
  setup(props, { emit }) {
    const password = ref("");
    watch(
      () => props.modelValue,
      (open) => {
        if (open) password.value = "";
      }
    );
    const close = () => emit("update:modelValue", false);
    const submit = () => {
      if (password.value.length < 12) return;
      emit("submit", password.value);
    };
    return { password, close, submit };
  },
});
</script>
<style scoped>
.admin-hint {
  color: #6e7686;
  font-size: 0.82rem;
  margin: 0 0 16px;
}
</style>
