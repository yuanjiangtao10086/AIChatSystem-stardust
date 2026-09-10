<template>
  <AuthPanel>
    <p class="auth-brand"><span aria-hidden="true">✦</span>星语 AI</p>
    <h2>创建账户</h2>
    <p class="form-intro">填写昵称、邮箱，并设置一个高强度密码。</p>
    <form @submit.prevent="submit">
      <label>
        昵称
        <input
          v-model.trim="displayName"
          autocomplete="name"
          maxlength="100"
          required
        />
      </label>
      <label>
        邮箱
        <input
          v-model.trim="email"
          type="email"
          autocomplete="email"
          required
        />
      </label>
      <label>
        密码
        <input
          v-model="password"
          type="password"
          autocomplete="new-password"
          minlength="12"
          maxlength="128"
          required
        />
        <small>至少使用 12 个字符。</small>
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="primary-button" type="submit" :disabled="submitting">
        {{ submitting ? "正在创建账户…" : "注册" }}
      </button>
    </form>
    <p class="form-switch">
      已有账户？ <router-link to="/login">直接登录</router-link>
    </p>
  </AuthPanel>
</template>

<script lang="ts">
import { defineComponent, ref } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import AuthPanel from "@/components/AuthPanel.vue";
import { ApiError } from "@/api/client";

export default defineComponent({
  name: "RegisterView",
  components: { AuthPanel },
  setup() {
    const store = useStore();
    const router = useRouter();
    const displayName = ref("");
    const email = ref("");
    const password = ref("");
    const error = ref("");
    const submitting = ref(false);
    const submit = async () => {
      error.value = "";
      submitting.value = true;
      try {
        await store.dispatch("auth/register", {
          displayName: displayName.value,
          email: email.value,
          password: password.value,
        });
        await router.replace({ name: "chat" });
      } catch (reason) {
        error.value =
          reason instanceof ApiError ? reason.message : "注册失败，请重试。";
      } finally {
        submitting.value = false;
      }
    };
    return { displayName, email, password, error, submitting, submit };
  },
});
</script>
