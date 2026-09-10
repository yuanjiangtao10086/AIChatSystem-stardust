<template>
  <div class="admin-shell">
    <aside class="admin-rail">
      <router-link to="/admin" class="admin-brand"
        ><b>✦</b
        ><span>星语 AI<br /><small>管理控制台</small></span></router-link
      >
      <nav>
        <router-link
          v-for="item in items"
          :key="item.id"
          :to="item.to"
          :class="['admin-link', { active: isActive(item) }]"
          :aria-disabled="item.pending || undefined"
          @click="item.pending && $event.preventDefault()"
        >
          <span>{{ item.mark }}</span>
          <span class="admin-link-label">{{ item.label }}</span>
          <em v-if="item.pending" class="admin-link-pending">待接入</em>
        </router-link>
      </nav>
      <div class="admin-identity">
        <span>{{ initials }}</span>
        <div>
          <strong>{{ name }}</strong
          ><small>{{ roleLabel }}</small>
        </div>
        <button class="admin-exit" type="button" @click="exit">退出</button>
      </div>
    </aside>
    <main class="admin-main">
      <header>
        <div>
          <p>控制台 / {{ activeLabel.toUpperCase() }}</p>
          <h1>{{ activeLabel }}</h1>
        </div>
        <div class="admin-main-actions">
          <router-link to="/chat" class="admin-back">返回前台</router-link>
          <div class="live"><i></i> 系统运行中</div>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>
<script lang="ts">
import { computed, defineComponent, PropType } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useStore } from "vuex";

export type AdminSection =
  | "dashboard"
  | "users"
  | "conversations"
  | "files"
  | "rag"
  | "ai"
  | "audit";

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: "超级管理员",
  ADMIN: "管理员",
  USER: "用户",
};

export default defineComponent({
  name: "AdminShell",
  props: {
    name: { type: String, default: "管理员" },
    role: { type: String as PropType<string>, default: "ADMIN" },
  },
  setup(p) {
    const route = useRoute();
    const router = useRouter();
    const store = useStore();
    const items = [
      {
        id: "dashboard",
        label: "总览",
        mark: "◫",
        to: "/admin",
        pending: false,
      },
      {
        id: "users",
        label: "用户",
        mark: "◎",
        to: "/admin/users",
        pending: false,
      },
      {
        id: "usage",
        label: "用量分析",
        mark: "▦",
        to: "/admin/usage",
        pending: false,
      },
      {
        id: "conversations",
        label: "对话",
        mark: "◌",
        to: "/admin/conversations",
        pending: false,
      },
      {
        id: "files",
        label: "文件",
        mark: "□",
        to: "/admin/files",
        pending: false,
      },
      {
        id: "rag",
        label: "知识库 / RAG",
        mark: "◇",
        to: "/admin/rag",
        pending: false,
      },
      {
        id: "ai",
        label: "AI 运营",
        mark: "⌁",
        to: "/admin/ai",
        pending: false,
      },
      {
        id: "audit",
        label: "审计日志",
        mark: "≡",
        to: "/admin/audit",
        pending: false,
      },
    ] as const;
    const current = computed(() => {
      const seg = route.path.split("/")[2];
      if (seg && seg !== "") return seg;
      return "dashboard";
    });
    const isActive = (item: { id: string }) =>
      item.id === current.value ||
      (item.id === "users" && route.path.startsWith("/admin/users"));
    const roleLabel = computed(() => ROLE_LABELS[p.role] || p.role);
    const activeLabel = computed(
      () => items.find((i) => i.id === current.value)?.label || "管理"
    );
    const exit = async () => {
      await store.dispatch("auth/logout");
      await router.replace({ name: "login" });
    };
    return {
      items,
      isActive,
      activeLabel,
      initials: computed(() => p.name.slice(0, 2).toUpperCase()),
      roleLabel,
      exit,
    };
  },
});
</script>
<style scoped>
.admin-shell {
  --nav: #111827;
  --blue: #4f7cff;
  min-height: 100vh;
  background: #f4f6f9;
  color: #172033;
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
}
.admin-rail {
  position: sticky;
  top: 0;
  height: 100vh;
  padding: 25px 16px;
  display: flex;
  flex-direction: column;
  background: var(--nav);
  color: #dfe7f5;
}
.admin-brand {
  display: flex;
  gap: 11px;
  align-items: center;
  padding: 0 10px 28px;
  color: white;
  text-decoration: none;
  font: 700 13px/1.1 "Cascadia Mono", monospace;
  letter-spacing: 0.08em;
}
.admin-brand b {
  font-size: 25px;
  color: #7fa0ff;
}
.admin-brand small {
  font-size: 8px;
  color: #7c8aa2;
  letter-spacing: 0.18em;
}
.admin-rail nav {
  display: grid;
  gap: 5px;
}
.admin-link {
  display: flex;
  align-items: center;
  gap: 0;
  border: 0;
  background: transparent;
  color: #8f9bb0;
  padding: 11px 13px;
  text-align: left;
  border-radius: 7px;
  cursor: pointer;
  font-weight: 600;
  text-decoration: none;
}
.admin-link > span:first-child {
  display: inline-block;
  width: 25px;
  font-family: monospace;
}
.admin-link-label {
  flex: 1;
}
.admin-link-pending {
  font: 8px/1 monospace;
  letter-spacing: 0.1em;
  color: #6c7a93;
  border: 1px solid #2c3852;
  border-radius: 4px;
  padding: 3px 5px;
}
.admin-link.active {
  color: #fff;
  background: #25304a;
  box-shadow: inset 3px 0 var(--blue);
}
.admin-link[aria-disabled] {
  cursor: not-allowed;
  opacity: 0.7;
}
.admin-identity {
  margin-top: auto;
  border-top: 1px solid #283247;
  padding: 18px 8px 0;
  display: flex;
  gap: 10px;
  align-items: center;
}
.admin-identity > span {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #3156c8;
  font-weight: 800;
}
.admin-identity strong,
.admin-identity small {
  display: block;
}
.admin-identity strong {
  font-size: 12px;
}
.admin-identity small {
  margin-top: 3px;
  color: #79869d;
  font: 9px monospace;
}
.admin-exit {
  margin-left: auto;
  border: 1px solid #2c3852;
  background: transparent;
  color: #b9c4d8;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 11px;
  cursor: pointer;
}
.admin-exit:hover {
  background: #1b2638;
}
.admin-main {
  min-width: 0;
  padding: 32px clamp(22px, 4vw, 58px) 60px;
}
.admin-main > header {
  display: flex;
  justify-content: space-between;
  align-items: end;
  margin-bottom: 28px;
}
.admin-main header p {
  margin: 0 0 6px;
  color: #718096;
  font: 10px "Cascadia Mono", monospace;
  letter-spacing: 0.12em;
}
.admin-main h1 {
  font-family: "Segoe UI Variable", sans-serif;
  font-size: 28px;
  letter-spacing: -0.03em;
  margin: 0;
}
.admin-main-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}
.admin-back {
  color: #4f7cff;
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
}
.live {
  font: 10px monospace;
  color: #526078;
}
.live i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  background: #2fb37c;
  box-shadow: 0 0 0 4px #d9f3e8;
}
@media (max-width: 760px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }
  .admin-rail {
    position: relative;
    width: 100%;
    height: auto;
    padding: 14px;
  }
  .admin-brand {
    padding-bottom: 12px;
  }
  .admin-rail nav {
    display: flex;
    overflow-x: auto;
  }
  .admin-link {
    white-space: nowrap;
  }
  .admin-identity {
    display: none;
  }
  .admin-main {
    padding: 22px 14px;
  }
  .admin-main > header {
    align-items: center;
  }
}
</style>
