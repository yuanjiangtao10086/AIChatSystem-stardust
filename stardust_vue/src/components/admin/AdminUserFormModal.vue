<template>
  <AdminModal
    :model-value="modelValue"
    :title="isEdit ? '编辑用户资料' : '新建用户'"
    :error="error"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <form @submit.prevent="submit">
      <label>
        邮箱
        <input
          v-model.trim="email"
          type="email"
          autocomplete="off"
          :disabled="busy"
        />
      </label>
      <label>
        显示名称
        <input v-model.trim="displayName" type="text" :disabled="busy" />
      </label>
      <label v-if="!isEdit">
        初始密码（≥12 位）
        <input
          v-model="password"
          type="text"
          autocomplete="new-password"
          :disabled="busy"
        />
      </label>
      <fieldset v-if="!isEdit" class="admin-form-roles">
        <legend>角色</legend>
        <label v-for="r in roleOptions" :key="r" class="admin-check">
          <input v-model="roles" type="checkbox" :value="r" :disabled="busy" />
          {{ roleLabel(r) }}
        </label>
      </fieldset>
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
        :disabled="busy || !valid"
        @click="submit"
      >
        {{ busy ? "提交中…" : isEdit ? "保存" : "创建" }}
      </button>
    </template>
  </AdminModal>
</template>
<script lang="ts">
import { computed, defineComponent, PropType, ref, watch } from "vue";
import AdminModal from "@/components/admin/AdminModal.vue";
import { AdminUser } from "@/types/admin";
import { UserRole } from "@/types/auth";

const ROLE_LABELS: Record<UserRole, string> = {
  USER: "用户",
  ADMIN: "管理员",
  SUPER_ADMIN: "超级管理员",
};

export default defineComponent({
  name: "AdminUserFormModal",
  components: { AdminModal },
  props: {
    modelValue: { type: Boolean, default: false },
    user: { type: Object as PropType<AdminUser | null>, default: null },
    busy: { type: Boolean, default: false },
    error: { type: String, default: "" },
  },
  emits: ["update:modelValue", "submit"],
  setup(props, { emit }) {
    const email = ref("");
    const displayName = ref("");
    const password = ref("");
    const roles = ref<UserRole[]>(["USER"]);
    const roleOptions: UserRole[] = ["USER", "ADMIN", "SUPER_ADMIN"];
    const isEdit = computed(() => !!props.user);

    watch(
      () => props.modelValue,
      (open) => {
        if (open) {
          email.value = props.user?.email || "";
          displayName.value = props.user?.displayName || "";
          password.value = "";
          roles.value = props.user?.roles?.length
            ? [...props.user.roles]
            : ["USER"];
        }
      }
    );

    const emailOk = computed(() =>
      /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.value)
    );
    const valid = computed(() => {
      if (!emailOk.value || displayName.value.length < 2) return false;
      if (!isEdit.value) {
        if (password.value.length < 12 || roles.value.length === 0)
          return false;
      }
      return true;
    });

    const close = () => emit("update:modelValue", false);
    const submit = () => {
      if (!valid.value) return;
      emit("submit", {
        email: email.value,
        displayName: displayName.value,
        password: password.value,
        roles: roles.value,
      });
    };
    return {
      email,
      displayName,
      password,
      roles,
      roleOptions,
      isEdit,
      valid,
      roleLabel: (r: UserRole) => ROLE_LABELS[r],
      close,
      submit,
    };
  },
});
</script>
<style scoped>
.admin-form-roles {
  border: 0;
  padding: 0;
  margin: 4px 0 0;
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}
.admin-form-roles legend {
  font-size: 0.82rem;
  font-weight: 600;
  margin-bottom: 10px;
  color: #344;
}
.admin-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: auto;
  font-weight: 500;
}
.admin-check input {
  width: auto;
  min-height: 0;
}
</style>
