<template>
  <div class="app-frame">
    <header
      v-if="isAuthenticated && !isChatRoute && !isAdminRoute"
      class="topbar"
    >
      <router-link class="brand" to="/chat" aria-label="星语 AI 聊天">
        <span class="brand-mark" aria-hidden="true">✦</span>
        <span>星语 AI</span>
      </router-link>
      <nav class="topbar-links" aria-label="主导航">
        <router-link to="/chat">聊天</router-link>
        <router-link to="/files">云盘</router-link>
        <router-link to="/memories">记忆</router-link>
        <router-link to="/knowledge">知识库</router-link>
        <router-link to="/profile">个人资料</router-link>
        <router-link v-if="canAccessAdmin" to="/admin">管理后台</router-link>
      </nav>
      <div class="topbar-actions">
        <ThemeToggle />
        <button class="quiet-button" type="button" @click="logout">
          退出登录
        </button>
      </div>
    </header>
    <main
      :class="{ 'app-main': isAuthenticated && !isChatRoute && !isAdminRoute }"
    >
      <router-view />
    </main>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useStore } from "vuex";
import ThemeToggle from "@/components/ThemeToggle.vue";

export default defineComponent({
  name: "App",
  components: { ThemeToggle },
  setup() {
    const store = useStore();
    const router = useRouter();
    const route = useRoute();
    const isAuthenticated = computed(
      () => store.getters["auth/isAuthenticated"] as boolean
    );
    const canAccessAdmin = computed(
      () => store.getters["auth/canAccessAdmin"] as boolean
    );
    const isChatRoute = computed(() => route.path.startsWith("/chat"));
    const isAdminRoute = computed(() => route.path.startsWith("/admin"));
    const logout = async () => {
      await store.dispatch("auth/logout");
      await router.replace({ name: "login" });
    };
    return {
      isAuthenticated,
      canAccessAdmin,
      isChatRoute,
      isAdminRoute,
      logout,
    };
  },
});
</script>

<style scoped>
.topbar-actions {
  display: flex;
  justify-self: end;
  align-items: center;
  gap: 10px;
}
</style>
