import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import store from "@/store";

const routes: Array<RouteRecordRaw> = [
  {
    path: "/",
    redirect: "/chat",
  },
  {
    path: "/chat",
    name: "chat",
    component: () => import("@/views/ChatView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/chat/:conversationId",
    name: "conversation",
    component: () => import("@/views/ChatView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/conversations/:conversationId",
    redirect: (to) => ({
      name: "conversation",
      params: { conversationId: to.params.conversationId },
    }),
  },
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/LoginView.vue"),
    meta: { guestOnly: true },
  },
  {
    path: "/register",
    name: "register",
    component: () => import("@/views/RegisterView.vue"),
    meta: { guestOnly: true },
  },
  {
    path: "/files",
    name: "files",
    component: () => import("@/views/FilesView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/memories",
    name: "memories",
    component: () => import("@/views/MemoriesView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/knowledge",
    name: "knowledge",
    component: () => import("@/views/KnowledgeView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/profile",
    name: "profile",
    component: () => import("@/views/ProfileView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/admin",
    component: () => import("@/views/admin/AdminLayoutView.vue"),
    meta: { requiresAuth: true, adminOnly: true },
    children: [
      {
        path: "",
        name: "admin-dashboard",
        component: () => import("@/views/admin/AdminDashboardView.vue"),
      },
      {
        path: "users",
        name: "admin-users",
        component: () => import("@/views/admin/AdminUsersView.vue"),
      },
      {
        path: "users/:id",
        name: "admin-user-detail",
        component: () => import("@/views/admin/AdminUserDetailView.vue"),
        props: true,
      },
      {
        path: "conversations",
        name: "admin-conversations",
        component: () => import("@/views/admin/AdminConversationsView.vue"),
      },
      {
        path: "conversations/:id",
        name: "admin-conversation-detail",
        component: () =>
          import("@/views/admin/AdminConversationDetailView.vue"),
        props: true,
      },
      {
        path: "files",
        name: "admin-files",
        component: () => import("@/views/admin/AdminFilesView.vue"),
      },
      {
        path: "files/:id",
        name: "admin-file-detail",
        component: () => import("@/views/admin/AdminFileDetailView.vue"),
        props: true,
      },
      {
        path: "rag",
        name: "admin-rag",
        component: () => import("@/views/admin/AdminKnowledgeView.vue"),
      },
      {
        path: "rag/:id",
        name: "admin-rag-detail",
        component: () =>
          import("@/views/admin/AdminKnowledgeBaseDetailView.vue"),
        props: true,
      },
      {
        path: "usage",
        name: "admin-usage",
        component: () => import("@/views/admin/AdminUsageView.vue"),
      },
      {
        path: "ai",
        name: "admin-ai",
        component: () => import("@/views/admin/AdminAiView.vue"),
      },
      {
        path: "ai/requests",
        name: "admin-ai-requests",
        component: () => import("@/views/admin/AdminAiRequestsView.vue"),
      },
      {
        path: "audit",
        name: "admin-audit",
        component: () => import("@/views/admin/AdminAuditView.vue"),
      },
    ],
  },
  {
    path: "/forbidden",
    name: "forbidden",
    component: () => import("@/views/ForbiddenView.vue"),
    meta: { requiresAuth: true },
  },
  { path: "/:pathMatch(.*)*", redirect: "/chat" },
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach(async (to) => {
  await store.dispatch("auth/bootstrap");
  const isAuthenticated = store.getters["auth/isAuthenticated"] as boolean;
  const canAccessAdmin = store.getters["auth/canAccessAdmin"] as boolean;

  if (to.meta.requiresAuth && !isAuthenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (to.meta.guestOnly && isAuthenticated) {
    return { name: "chat" };
  }
  if (to.meta.adminOnly && !canAccessAdmin) {
    return { name: "forbidden" };
  }
  return true;
});

export default router;
