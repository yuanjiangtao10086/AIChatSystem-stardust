<template>
  <AdminModal
    :model-value="modelValue"
    :title="`角色管理 · ${user?.displayName || ''}`"
    :error="error"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="admin-hint">角色变更会被服务端 RBAC 强制校验并写入审计日志。</p>
    <fieldset class="admin-form-roles">
      <label v-for="r in roleOptions" :key="r" class="admin-check">
        <input
          v-model="roles"
          type="checkbox"
          :value="r"
          :disabled="busy || (!canGrantAdmin && r !== 'USER')"
        />
        {{ roleLabel(r) }}
        <small v-if="!canGrantAdmin && r !== 'USER'">（需超级管理员）</small>
      </label>
    </fieldset>
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
        :disabled="busy || roles.length === 0"
        @click="submit"
      >
        {{ busy ? "提交中…" : "保存角色" }}
      </button>
    </template>
  </AdminModal>
</template>
<script lang="ts">
import { defineComponent, PropType, ref, watch } from "vue";
import AdminModal from "@/components/admin/AdminModal.vue";
import { AdminUser } from "@/types/admin";
import { UserRole } from "@/types/auth";

const ROLE_LABELS: Record<UserRole, string> = {
  USER: "用户",
  ADMIN: "管理员",
  SUPER_ADMIN: "超级管理员",
};

export default defineComponent({
  name: "AdminRoleModal",
  components: { AdminModal },
  props: {
    modelValue: { type: Boolean, default: false },
    user: { type: Object as PropType<AdminUser | null>, default: null },
    canGrantAdmin: { type: Boolean, default: false },
    busy: { type: Boolean, default: false },
    error: { type: String, default: "" },
  },
  emits: ["update:modelValue", "submit"],
  setup(props, { emit }) {
    const roles = ref<UserRole[]>(["USER"]);
    const roleOptions: UserRole[] = ["USER", "ADMIN", "SUPER_ADMIN"];
    watch(
      () => props.modelValue,
      (open) => {
        if (open) {
          roles.value = props.user?.roles?.length
            ? [...props.user.roles]
            : ["USER"];
        }
      }
    );
    const close = () => emit("update:modelValue", false);
    const submit = () => emit("submit", roles.value);
    return {
      roles,
      roleOptions,
      roleLabel: (r: UserRole) => ROLE_LABELS[r],
      close,
      submit,
    };
  },
});
</script>
<style scoped>
.admin-hint {
  color: #6e7686;
  font-size: 0.82rem;
  margin: 0 0 16px;
}
.admin-form-roles {
  border: 0;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.admin-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: auto;
  font-weight: 500;
}
.admin-check input {
  width: auto;
  min-height: 0;
}
.admin-check small {
  color: #97a0b2;
  font-weight: 400;
}
</style>
