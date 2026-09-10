<template>
  <AuthPanel>
    <p class="auth-brand"><span aria-hidden="true">✦</span>星语 AI</p>
    <h2>欢迎回来</h2>
    <p class="form-intro">请输入账户绑定的邮箱和密码登录。</p>
    <form @submit.prevent="submit">
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
          autocomplete="current-password"
          maxlength="128"
          required
        />
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="primary-button" type="submit" :disabled="submitting">
        {{ submitting ? "正在登录…" : "登录" }}
      </button>
    </form>
    <p class="form-switch">
      还没有账户？
      <router-link to="/register">立即注册</router-link>
    </p>
  </AuthPanel>
</template>

<script lang="ts">
import { defineComponent, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useStore } from "vuex";
import AuthPanel from "@/components/AuthPanel.vue";
import { ApiError } from "@/api/client";

export default defineComponent({
  name: "LoginView",
  components: { AuthPanel },
  setup() {
    const store = useStore();
    const route = useRoute();
    const router = useRouter();
    const email = ref("");
    const password = ref("");
    const error = ref("");
    const submitting = ref(false);

    const submit = async () => {
      error.value = "";
      submitting.value = true;
      try {
        await store.dispatch("auth/login", {
          email: email.value,
          password: password.value,
        });
        const redirect =
          typeof route.query.redirect === "string" ? route.query.redirect : "/";
        await router.replace(redirect);
      } catch (reason) {
        error.value =
          reason instanceof ApiError ? reason.message : "登录失败，请重试。";
      } finally {
        submitting.value = false;
      }
    };
    return { email, password, error, submitting, submit };
  },
});
</script>
