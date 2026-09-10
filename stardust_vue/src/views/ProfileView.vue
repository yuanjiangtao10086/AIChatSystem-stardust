<template>
  <section class="page-shell profile-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">账户设置</p>
        <h1>个人资料</h1>
      </div>
      <span class="role-chip">{{ primaryRole }}</span>
    </div>

    <div class="settings-grid">
      <form class="settings-card" @submit.prevent="saveProfile">
        <div>
          <h2>资料</h2>
          <p>设置在工作区中展示的昵称。</p>
        </div>
        <label>
          邮箱
          <input :value="user?.email" type="email" disabled />
        </label>
        <label>
          昵称
          <input v-model.trim="displayName" maxlength="100" required />
        </label>
        <p v-if="profileMessage" class="form-note" role="status">
          {{ profileMessage }}
        </p>
        <button class="primary-button" type="submit">保存资料</button>
      </form>

      <form class="settings-card" @submit.prevent="changePassword">
        <div>
          <h2>密码</h2>
          <p>修改密码后，所有旧的刷新会话与访问令牌都会失效。</p>
        </div>
        <label>
          当前密码
          <input
            v-model="currentPassword"
            type="password"
            autocomplete="current-password"
            maxlength="128"
            required
          />
        </label>
        <label>
          新密码
          <input
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
            minlength="12"
            maxlength="128"
            required
          />
        </label>
        <p v-if="passwordMessage" class="form-note" role="status">
          {{ passwordMessage }}
        </p>
        <button class="primary-button" type="submit">修改密码</button>
      </form>

      <UsagePanel />
    </div>
  </section>
</template>

<script lang="ts">
import { computed, defineComponent, ref, watch } from "vue";
import { useStore } from "vuex";
import { ApiError } from "@/api/client";
import UsagePanel from "@/components/usage/UsagePanel.vue";
import { UserProfile } from "@/types/auth";

export default defineComponent({
  name: "ProfileView",
  components: { UsagePanel },
  setup() {
    const store = useStore();
    const user = computed(() => store.state.auth.user as UserProfile | null);
    const primaryRole = computed(() => user.value?.roles[0] ?? "USER");
    const displayName = ref(user.value?.displayName ?? "");
    const currentPassword = ref("");
    const newPassword = ref("");
    const profileMessage = ref("");
    const passwordMessage = ref("");

    watch(user, (value) => {
      displayName.value = value?.displayName ?? "";
    });

    const saveProfile = async () => {
      profileMessage.value = "";
      try {
        await store.dispatch("auth/updateProfile", displayName.value);
        profileMessage.value = "资料已保存。";
      } catch (reason) {
        profileMessage.value =
          reason instanceof ApiError ? reason.message : "资料保存失败。";
      }
    };

    const changePassword = async () => {
      passwordMessage.value = "";
      try {
        await store.dispatch("auth/changePassword", {
          currentPassword: currentPassword.value,
          newPassword: newPassword.value,
        });
        currentPassword.value = "";
        newPassword.value = "";
        passwordMessage.value = "密码已修改，旧会话已失效。";
      } catch (reason) {
        passwordMessage.value =
          reason instanceof ApiError ? reason.message : "密码修改失败。";
      }
    };

    return {
      user,
      primaryRole,
      displayName,
      currentPassword,
      newPassword,
      profileMessage,
      passwordMessage,
      saveProfile,
      changePassword,
    };
  },
});
</script>
