<template>
  <div v-if="user" class="admin-detail">
    <button class="admin-back-link" type="button" @click="back">
      ← 返回用户列表
    </button>

    <header class="admin-detail-head">
      <div>
        <h2>{{ user.displayName }}</h2>
        <p class="admin-detail-email">{{ user.email }}</p>
      </div>
      <span class="status" :class="'status-' + user.status.toLowerCase()">
        {{ USER_STATUS_LABELS[user.status] }}
      </span>
    </header>

    <div v-if="error" class="admin-error">{{ error }}</div>

    <section class="admin-card">
      <h3>基础资料</h3>
      <dl class="admin-meta">
        <div>
          <dt>用户 ID</dt>
          <dd class="mono">{{ user.id }}</dd>
        </div>
        <div>
          <dt>邮箱</dt>
          <dd>{{ user.email }}</dd>
        </div>
        <div>
          <dt>显示名称</dt>
          <dd>{{ user.displayName }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ USER_STATUS_LABELS[user.status] }}</dd>
        </div>
        <div v-if="user.banReason">
          <dt>封禁原因</dt>
          <dd>{{ user.banReason }}</dd>
        </div>
        <div>
          <dt>角色</dt>
          <dd>
            <span v-for="r in user.roles" :key="r" class="role-chip">{{
              USER_ROLE_LABELS[r]
            }}</span>
          </dd>
        </div>
        <div>
          <dt>最后登录</dt>
          <dd>{{ formatDateTime(user.lastLoginAt) }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ formatDateTime(user.createdAt) }}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{{ formatDateTime(user.updatedAt) }}</dd>
        </div>
      </dl>
    </section>

    <section class="admin-card">
      <h3>存储用量</h3>
      <div class="admin-meter">
        <div class="admin-meter-bar">
          <i :style="{ width: storagePct + '%' }"></i>
        </div>
        <p>
          {{ formatBytes(user.usedBytes) }} /
          {{ formatBytes(user.quotaBytes) }}（{{ storagePct }}%）
        </p>
      </div>
    </section>

    <section v-if="user.usage" class="admin-card">
      <h3>AI 额度（{{ user.usage.currency }}）</h3>
      <dl class="admin-meta">
        <div>
          <dt>周期额度 tokens</dt>
          <dd>{{ formatNumber(user.usage.quotaTokens) }}</dd>
        </div>
        <div>
          <dt>已用 tokens</dt>
          <dd>{{ formatNumber(user.usage.usedTokens) }}</dd>
        </div>
        <div>
          <dt>可用 tokens</dt>
          <dd>{{ formatNumber(user.usage.availableTokens) }}</dd>
        </div>
        <div>
          <dt>已用费用</dt>
          <dd>{{ user.usage.usedCost }} {{ user.usage.currency }}</dd>
        </div>
        <div>
          <dt>周期开始</dt>
          <dd>{{ formatDateTime(user.usage.periodStart) }}</dd>
        </div>
        <div>
          <dt>周期结束</dt>
          <dd>{{ formatDateTime(user.usage.periodEnd) }}</dd>
        </div>
      </dl>
    </section>

    <section class="admin-card">
      <h3>操作</h3>
      <div class="admin-actions">
        <button class="row-btn" type="button" @click="openEdit">
          编辑资料
        </button>
        <button class="row-btn" type="button" @click="openRoles">
          角色管理
        </button>
        <button class="row-btn" type="button" @click="openReset">
          重置密码
        </button>
        <button
          v-if="user.status !== 'BANNED'"
          class="row-btn"
          type="button"
          @click="ask('BANNED', '确认封禁该用户？')"
        >
          封禁
        </button>
        <button
          v-if="user.status === 'BANNED'"
          class="row-btn"
          type="button"
          @click="ask('NORMAL', '确认解除封禁？')"
        >
          解封
        </button>
        <button
          v-if="user.status !== 'DISABLED'"
          class="row-btn"
          type="button"
          @click="ask('DISABLED', '确认停用该用户？')"
        >
          停用
        </button>
        <button
          v-if="user.status === 'DISABLED'"
          class="row-btn"
          type="button"
          @click="ask('NORMAL', '确认重新启用？')"
        >
          启用
        </button>
        <button
          v-if="user.status === 'DELETED'"
          class="row-btn"
          type="button"
          @click="ask('RESTORE', '确认恢复该用户？')"
        >
          恢复
        </button>
        <button
          v-if="user.status !== 'DELETED'"
          class="row-btn row-danger"
          type="button"
          @click="ask('DELETE', '确认软删除该用户？')"
        >
          删除
        </button>
      </div>
    </section>

    <AdminUserFormModal
      v-model="formOpen"
      :user="user"
      :busy="formBusy"
      :error="formError"
      @submit="submitForm"
    />
    <AdminRoleModal
      v-model="roleOpen"
      :user="user"
      :can-grant-admin="canGrantAdmin"
      :busy="roleBusy"
      :error="roleError"
      @submit="submitRoles"
    />
    <AdminPasswordModal
      v-model="pwOpen"
      :user="user"
      :busy="pwBusy"
      :error="pwError"
      @submit="submitReset"
    />
    <AdminConfirmModal
      v-model="confirmOpen"
      :title="confirmTitle"
      :message="confirmMessage"
      :busy="confirmBusy"
      :error="confirmError"
      @confirm="runConfirm"
    />
  </div>
  <div v-else-if="loading" class="admin-loading">加载中…</div>
  <div v-else class="admin-empty">
    {{ notFound ? "未找到该用户。" : error }}
  </div>
</template>
<script lang="ts">
import { computed, defineComponent, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import AdminUserFormModal from "@/components/admin/AdminUserFormModal.vue";
import AdminRoleModal from "@/components/admin/AdminRoleModal.vue";
import AdminPasswordModal from "@/components/admin/AdminPasswordModal.vue";
import AdminConfirmModal from "@/components/admin/AdminConfirmModal.vue";
import {
  getAdminUser,
  updateAdminUser,
  setAdminUserStatus,
  restoreAdminUser,
  deleteAdminUser,
  resetAdminPassword,
  setAdminUserRoles,
} from "@/api/admin";
import { AdminUser } from "@/types/admin";
import { UserRole } from "@/types/auth";
import {
  describeError,
  formatBytes,
  formatDateTime,
  formatNumber,
  isNotFoundError,
  USER_ROLE_LABELS,
  USER_STATUS_LABELS,
} from "@/utils/admin";

export default defineComponent({
  name: "AdminUserDetailView",
  components: {
    AdminUserFormModal,
    AdminRoleModal,
    AdminPasswordModal,
    AdminConfirmModal,
  },
  props: { id: { type: String, required: true } },
  setup(props) {
    const router = useRouter();
    const store = useStore();
    const me = computed(() => store.getters["auth/user"]);
    const canGrantAdmin = computed(() =>
      me.value?.roles?.includes("SUPER_ADMIN")
    );

    const user = ref<AdminUser | null>(null);
    const loading = ref(false);
    const error = ref("");
    const notFound = ref(false);

    const load = async () => {
      loading.value = true;
      error.value = "";
      notFound.value = false;
      try {
        user.value = await getAdminUser(props.id);
      } catch (e) {
        user.value = null;
        notFound.value = isNotFoundError(e);
        error.value = describeError(e, "加载用户详情失败");
      } finally {
        loading.value = false;
      }
    };

    const storagePct = computed(() => {
      if (!user.value || !user.value.quotaBytes) return 0;
      return Math.min(
        100,
        Math.round((user.value.usedBytes / user.value.quotaBytes) * 100)
      );
    });

    const formOpen = ref(false);
    const formBusy = ref(false);
    const formError = ref("");
    const openEdit = () => {
      formError.value = "";
      formOpen.value = true;
    };
    const submitForm = async (payload: {
      email: string;
      displayName: string;
      password: string;
      roles: UserRole[];
    }) => {
      if (!user.value) return;
      formBusy.value = true;
      formError.value = "";
      try {
        await updateAdminUser(user.value.id, {
          email: payload.email,
          displayName: payload.displayName,
        });
        formOpen.value = false;
        await load();
      } catch (e) {
        formError.value = e instanceof Error ? e.message : "保存失败";
      } finally {
        formBusy.value = false;
      }
    };

    const roleOpen = ref(false);
    const roleBusy = ref(false);
    const roleError = ref("");
    const openRoles = () => {
      roleError.value = "";
      roleOpen.value = true;
    };
    const submitRoles = async (rs: UserRole[]) => {
      if (!user.value) return;
      roleBusy.value = true;
      roleError.value = "";
      try {
        await setAdminUserRoles(user.value.id, rs);
        roleOpen.value = false;
        await load();
      } catch (e) {
        roleError.value = e instanceof Error ? e.message : "角色更新失败";
      } finally {
        roleBusy.value = false;
      }
    };

    const pwOpen = ref(false);
    const pwBusy = ref(false);
    const pwError = ref("");
    const openReset = () => {
      pwError.value = "";
      pwOpen.value = true;
    };
    const submitReset = async (pw: string) => {
      if (!user.value) return;
      pwBusy.value = true;
      pwError.value = "";
      try {
        await resetAdminPassword(user.value.id, pw);
        pwOpen.value = false;
        await load();
      } catch (e) {
        pwError.value = e instanceof Error ? e.message : "密码重置失败";
      } finally {
        pwBusy.value = false;
      }
    };

    const confirmOpen = ref(false);
    const confirmTitle = ref("");
    const confirmMessage = ref("");
    const confirmBusy = ref(false);
    const confirmError = ref("");
    const pendingKind = ref<
      "BANNED" | "NORMAL" | "DISABLED" | "RESTORE" | "DELETE"
    >("BANNED");
    const ask = (
      kind: "BANNED" | "NORMAL" | "DISABLED" | "RESTORE" | "DELETE",
      message: string
    ) => {
      pendingKind.value = kind;
      confirmTitle.value = "请确认操作";
      confirmMessage.value = message;
      confirmError.value = "";
      confirmOpen.value = true;
    };
    const runConfirm = async () => {
      if (!user.value) return;
      if (user.value.id === me.value?.id) {
        confirmError.value = "不能对自己执行该操作";
        return;
      }
      confirmBusy.value = true;
      confirmError.value = "";
      try {
        switch (pendingKind.value) {
          case "BANNED":
            await setAdminUserStatus(user.value.id, "BANNED");
            break;
          case "NORMAL":
            await setAdminUserStatus(user.value.id, "NORMAL");
            break;
          case "DISABLED":
            await setAdminUserStatus(user.value.id, "DISABLED");
            break;
          case "RESTORE":
            await restoreAdminUser(user.value.id);
            break;
          case "DELETE":
            await deleteAdminUser(user.value.id);
            break;
        }
        confirmOpen.value = false;
        await load();
      } catch (e) {
        confirmError.value = e instanceof Error ? e.message : "操作失败";
      } finally {
        confirmBusy.value = false;
      }
    };

    const back = () => router.push({ name: "admin-users" });
    onMounted(load);

    return {
      user,
      loading,
      error,
      notFound,
      storagePct,
      formOpen,
      formBusy,
      formError,
      openEdit,
      submitForm,
      roleOpen,
      roleBusy,
      roleError,
      openRoles,
      submitRoles,
      pwOpen,
      pwBusy,
      pwError,
      openReset,
      submitReset,
      confirmOpen,
      confirmTitle,
      confirmMessage,
      confirmBusy,
      confirmError,
      ask,
      runConfirm,
      back,
      canGrantAdmin,
      USER_STATUS_LABELS,
      USER_ROLE_LABELS,
      formatBytes,
      formatDateTime,
      formatNumber,
    };
  },
});
</script>
