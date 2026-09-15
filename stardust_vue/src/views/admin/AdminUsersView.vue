<template>
  <div class="admin-users">
    <div class="admin-toolbar">
      <input
        v-model.trim="search"
        class="admin-search"
        placeholder="搜索邮箱或显示名"
        @input="onSearchInput"
      />
      <select v-model="statusFilter" class="admin-select" @change="reload">
        <option value="">全部状态</option>
        <option v-for="s in statuses" :key="s" :value="s">
          {{ USER_STATUS_LABELS[s] }}
        </option>
      </select>
      <select v-model="roleFilter" class="admin-select" @change="reload">
        <option value="">全部角色</option>
        <option v-for="r in roles" :key="r" :value="r">
          {{ USER_ROLE_LABELS[r] }}
        </option>
      </select>
      <select v-model="direction" class="admin-select" @change="reload">
        <option value="DESC">创建时间 ↓新</option>
        <option value="ASC">创建时间 ↑旧</option>
      </select>
      <button
        class="primary-button admin-create"
        type="button"
        @click="openCreate"
      >
        + 新建用户
      </button>
      <div v-if="selectedIds.length" class="batch-inline">
        <span>已选 {{ selectedIds.length }} 个用户</span>
        <button type="button" :disabled="busy" @click="clearSelection">
          取消选择
        </button>
        <button
          type="button"
          class="row-danger"
          :disabled="busy"
          @click="batchRemove"
        >
          {{ busy ? "删除中…" : "批量删除" }}
        </button>
      </div>
    </div>

    <p v-if="error" class="admin-error">{{ error }}</p>
    <p v-if="flash" class="admin-flash">{{ flash }}</p>

    <div v-if="loading" class="admin-loading">加载中…</div>

    <div
      v-else-if="!pageData || pageData.items.length === 0"
      class="admin-empty"
    >
      没有匹配的用户。
    </div>

    <template v-else>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th class="col-check">
                <input
                  class="ui-checkbox"
                  type="checkbox"
                  :checked="pageData.items.length > 0 && allSelected"
                  :indeterminate.prop="someSelected && !allSelected"
                  aria-label="全选当前页"
                  @change="toggleAll(!allSelected)"
                />
              </th>
              <th>用户</th>
              <th>状态</th>
              <th>角色</th>
              <th>存储</th>
              <th>AI 用量</th>
              <th>创建时间</th>
              <th class="admin-actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in pageData.items" :key="u.id">
              <td class="col-check">
                <input
                  class="ui-checkbox"
                  type="checkbox"
                  :checked="selectedIds.includes(u.id)"
                  :disabled="u.status === 'DELETED' || u.id === meId"
                  aria-label="选择用户"
                  @change="toggle(u.id)"
                />
              </td>
              <td>
                <button
                  class="admin-user-link"
                  type="button"
                  @click="openDetail(u)"
                >
                  <strong>{{ u.displayName }}</strong>
                  <small>{{ u.email }}</small>
                </button>
              </td>
              <td>
                <span
                  class="status"
                  :class="'status-' + u.status.toLowerCase()"
                >
                  {{ USER_STATUS_LABELS[u.status] }}
                </span>
              </td>
              <td>
                <span v-for="r in u.roles" :key="r" class="role-chip">{{
                  USER_ROLE_LABELS[r]
                }}</span>
              </td>
              <td>
                {{ formatBytes(u.usedBytes) }}
                <small> / {{ formatBytes(u.quotaBytes) }}</small>
              </td>
              <td>
                <small
                  >{{ u.aiRequests }} 次 ·
                  {{ formatNumber(u.aiTokens) }} tokens</small
                >
              </td>
              <td>
                <small>{{ formatDateTime(u.createdAt) }}</small>
              </td>
              <td class="admin-actions-col">
                <div class="admin-actions">
                  <button class="row-btn" type="button" @click="openEdit(u)">
                    编辑
                  </button>
                  <button class="row-btn" type="button" @click="openRoles(u)">
                    角色
                  </button>
                  <button class="row-btn" type="button" @click="openReset(u)">
                    密码
                  </button>
                  <button
                    v-if="u.status !== 'BANNED'"
                    class="row-btn"
                    type="button"
                    @click="
                      askStatus(
                        u,
                        'BANNED',
                        '确认封禁该用户？封禁后其会话立即失效。'
                      )
                    "
                  >
                    封禁
                  </button>
                  <button
                    v-if="u.status === 'BANNED'"
                    class="row-btn"
                    type="button"
                    @click="askStatus(u, 'NORMAL', '确认解除封禁？')"
                  >
                    解封
                  </button>
                  <button
                    v-if="u.status !== 'DISABLED'"
                    class="row-btn"
                    type="button"
                    @click="
                      askStatus(
                        u,
                        'DISABLED',
                        '确认停用该用户？停用后无法登录。'
                      )
                    "
                  >
                    停用
                  </button>
                  <button
                    v-if="u.status === 'DISABLED'"
                    class="row-btn"
                    type="button"
                    @click="askStatus(u, 'NORMAL', '确认重新启用该用户？')"
                  >
                    启用
                  </button>
                  <button
                    v-if="u.status === 'DELETED'"
                    class="row-btn"
                    type="button"
                    @click="askRestore(u)"
                  >
                    恢复
                  </button>
                  <button
                    v-if="u.status !== 'DELETED'"
                    class="row-btn row-danger"
                    type="button"
                    @click="askDelete(u)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPager
        :page="page"
        :size="20"
        :total-elements="pageData.totalElements"
        @change="changePage"
      />
    </template>

    <AdminUserFormModal
      v-model="formOpen"
      :user="formUser"
      :busy="formBusy"
      :error="formError"
      @submit="submitForm"
    />
    <AdminRoleModal
      v-model="roleOpen"
      :user="roleUser"
      :can-grant-admin="canGrantAdmin"
      :busy="roleBusy"
      :error="roleError"
      @submit="submitRoles"
    />
    <AdminPasswordModal
      v-model="pwOpen"
      :user="pwUser"
      :busy="pwBusy"
      :error="pwError"
      @submit="submitReset"
    />
    <AdminConfirmModal
      v-model="confirm.open"
      :title="confirm.title"
      :message="confirm.message"
      :busy="confirmBusy"
      :error="confirmError"
      @confirm="runConfirm"
    />
  </div>
</template>
<script lang="ts">
import { computed, defineComponent, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import AdminPager from "@/components/admin/AdminPager.vue";
import AdminUserFormModal from "@/components/admin/AdminUserFormModal.vue";
import AdminRoleModal from "@/components/admin/AdminRoleModal.vue";
import AdminPasswordModal from "@/components/admin/AdminPasswordModal.vue";
import AdminConfirmModal from "@/components/admin/AdminConfirmModal.vue";
import {
  listAdminUsers,
  createAdminUser,
  updateAdminUser,
  setAdminUserStatus,
  restoreAdminUser,
  deleteAdminUser,
  deleteAdminUsersBatch,
  resetAdminPassword,
  setAdminUserRoles,
} from "@/api/admin";
import { AdminUser, AdminUserPage } from "@/types/admin";
import { UserRole, UserStatus } from "@/types/auth";
import {
  formatBytes,
  formatDateTime,
  formatNumber,
  USER_ROLE_LABELS,
  USER_STATUS_LABELS,
} from "@/utils/admin";

interface ConfirmState {
  open: boolean;
  title: string;
  message: string;
  user: AdminUser | null;
  kind: "ban" | "unban" | "disable" | "enable" | "delete" | "restore";
}

export default defineComponent({
  name: "AdminUsersView",
  components: {
    AdminPager,
    AdminUserFormModal,
    AdminRoleModal,
    AdminPasswordModal,
    AdminConfirmModal,
  },
  setup() {
    const router = useRouter();
    const store = useStore();
    const me = computed(() => store.getters["auth/user"]);
    const canGrantAdmin = computed(() =>
      me.value?.roles?.includes("SUPER_ADMIN")
    );

    const statuses: UserStatus[] = ["NORMAL", "BANNED", "DISABLED", "DELETED"];
    const roles: UserRole[] = ["USER", "ADMIN", "SUPER_ADMIN"];

    const search = ref("");
    const statusFilter = ref("");
    const roleFilter = ref("");
    const direction = ref<"ASC" | "DESC">("DESC");
    const page = ref(0);
    const pageData = ref<AdminUserPage | null>(null);
    const loading = ref(false);
    const error = ref("");
    const selectedIds = ref<string[]>([]);
    const busy = ref(false);
    const flash = ref("");
    const meId = computed(() => me.value?.id ?? "");
    const allSelected = computed(
      () =>
        !!pageData.value &&
        pageData.value.items.length > 0 &&
        pageData.value.items.every(
          (u) =>
            selectedIds.value.includes(u.id) &&
            u.status !== "DELETED" &&
            u.id !== meId.value
        )
    );
    const someSelected = computed(() => selectedIds.value.length > 0);
    const toggle = (id: string) => {
      const index = selectedIds.value.indexOf(id);
      if (index >= 0) selectedIds.value.splice(index, 1);
      else selectedIds.value.push(id);
    };
    const toggleAll = (on: boolean) => {
      if (!pageData.value) return;
      const selectable = pageData.value.items
        .filter((u) => u.status !== "DELETED" && u.id !== meId.value)
        .map((u) => u.id);
      selectedIds.value = on ? selectable : [];
    };
    const clearSelection = () => {
      selectedIds.value = [];
    };

    let searchTimer: number | undefined;
    const onSearchInput = () => {
      window.clearTimeout(searchTimer);
      searchTimer = window.setTimeout(() => {
        page.value = 0;
        load();
      }, 300);
    };
    const reload = () => {
      page.value = 0;
      load();
    };
    const changePage = (p: number) => {
      page.value = p;
      load();
    };

    const load = async () => {
      loading.value = true;
      error.value = "";
      try {
        pageData.value = await listAdminUsers(
          page.value,
          search.value,
          statusFilter.value,
          roleFilter.value,
          direction.value
        );
      } catch (e) {
        error.value = e instanceof Error ? e.message : "加载用户列表失败";
      } finally {
        loading.value = false;
      }
    };

    // generic form (create / edit)
    const formOpen = ref(false);
    const formUser = ref<AdminUser | null>(null);
    const formBusy = ref(false);
    const formError = ref("");
    const openCreate = () => {
      formUser.value = null;
      formError.value = "";
      formOpen.value = true;
    };
    const openEdit = (u: AdminUser) => {
      formUser.value = u;
      formError.value = "";
      formOpen.value = true;
    };
    const submitForm = async (payload: {
      email: string;
      displayName: string;
      password: string;
      roles: UserRole[];
    }) => {
      formBusy.value = true;
      formError.value = "";
      try {
        if (formUser.value) {
          await updateAdminUser(formUser.value.id, {
            email: payload.email,
            displayName: payload.displayName,
          });
        } else {
          await createAdminUser(payload);
        }
        formOpen.value = false;
        await load();
      } catch (e) {
        formError.value = e instanceof Error ? e.message : "操作失败";
      } finally {
        formBusy.value = false;
      }
    };

    // roles
    const roleOpen = ref(false);
    const roleUser = ref<AdminUser | null>(null);
    const roleBusy = ref(false);
    const roleError = ref("");
    const openRoles = (u: AdminUser) => {
      roleUser.value = u;
      roleError.value = "";
      roleOpen.value = true;
    };
    const submitRoles = async (rs: UserRole[]) => {
      if (!roleUser.value) return;
      roleBusy.value = true;
      roleError.value = "";
      try {
        await setAdminUserRoles(roleUser.value.id, rs);
        roleOpen.value = false;
        await load();
      } catch (e) {
        roleError.value = e instanceof Error ? e.message : "角色更新失败";
      } finally {
        roleBusy.value = false;
      }
    };

    // reset password
    const pwOpen = ref(false);
    const pwUser = ref<AdminUser | null>(null);
    const pwBusy = ref(false);
    const pwError = ref("");
    const openReset = (u: AdminUser) => {
      pwUser.value = u;
      pwError.value = "";
      pwOpen.value = true;
    };
    const submitReset = async (pw: string) => {
      if (!pwUser.value) return;
      pwBusy.value = true;
      pwError.value = "";
      try {
        await resetAdminPassword(pwUser.value.id, pw);
        pwOpen.value = false;
        await load();
      } catch (e) {
        pwError.value = e instanceof Error ? e.message : "密码重置失败";
      } finally {
        pwBusy.value = false;
      }
    };

    // status / delete confirmations
    const confirm = ref<ConfirmState>({
      open: false,
      title: "",
      message: "",
      user: null,
      kind: "ban",
    });
    const confirmBusy = ref(false);
    const confirmError = ref("");
    const askStatus = (
      u: AdminUser,
      status: "BANNED" | "NORMAL" | "DISABLED",
      message: string
    ) => {
      const kind =
        status === "BANNED"
          ? "ban"
          : status === "DISABLED"
          ? "disable"
          : u.status === "BANNED"
          ? "unban"
          : "enable";
      confirm.value = {
        open: true,
        title: "请确认操作",
        message,
        user: u,
        kind,
      };
      confirmError.value = "";
    };
    const askRestore = (u: AdminUser) => {
      confirm.value = {
        open: true,
        title: "恢复用户",
        message: "确认恢复该已删除用户？其状态将重置为正常。",
        user: u,
        kind: "restore",
      };
      confirmError.value = "";
    };
    const askDelete = (u: AdminUser) => {
      confirm.value = {
        open: true,
        title: "删除用户",
        message: "确认软删除该用户？其数据和原因将写入审计日志，可随后恢复。",
        user: u,
        kind: "delete",
      };
      confirmError.value = "";
    };
    const runConfirm = async () => {
      const u = confirm.value.user;
      if (!u) return;
      if (u.id === me.value?.id) {
        confirmError.value = "不能对自己执行该操作";
        return;
      }
      confirmBusy.value = true;
      confirmError.value = "";
      try {
        switch (confirm.value.kind) {
          case "ban":
            await setAdminUserStatus(u.id, "BANNED");
            break;
          case "unban":
          case "enable":
            await setAdminUserStatus(u.id, "NORMAL");
            break;
          case "disable":
            await setAdminUserStatus(u.id, "DISABLED");
            break;
          case "restore":
            await restoreAdminUser(u.id);
            break;
          case "delete":
            await deleteAdminUser(u.id);
            break;
        }
        confirm.value.open = false;
        await load();
      } catch (e) {
        confirmError.value = e instanceof Error ? e.message : "操作失败";
      } finally {
        confirmBusy.value = false;
      }
    };

    const openDetail = (u: AdminUser) =>
      router.push({ name: "admin-user-detail", params: { id: u.id } });

    onMounted(load);
    onUnmounted(() => window.clearTimeout(searchTimer));

    const batchRemove = async () => {
      const ids = selectedIds.value.filter((id) => id !== meId.value);
      if (!ids.length) return;
      if (
        !window.confirm(
          `确认批量软删除选中的 ${ids.length} 个用户吗？其数据和原因将写入审计日志，可随后恢复。`
        )
      )
        return;
      busy.value = true;
      flash.value = "";
      try {
        const result = await deleteAdminUsersBatch(ids);
        selectedIds.value = [];
        await load();
        flash.value =
          `已删除 ${result.deleted} 个用户` +
          (result.failures.length
            ? `，${result.failures.length} 个删除失败。`
            : "。");
      } catch (e) {
        flash.value = e instanceof Error ? e.message : "批量删除失败";
      } finally {
        busy.value = false;
      }
    };

    return {
      search,
      statusFilter,
      roleFilter,
      direction,
      statuses,
      roles,
      page,
      pageData,
      loading,
      error,
      onSearchInput,
      reload,
      changePage,
      formOpen,
      formUser,
      formBusy,
      formError,
      openCreate,
      openEdit,
      submitForm,
      roleOpen,
      roleUser,
      roleBusy,
      roleError,
      openRoles,
      submitRoles,
      pwOpen,
      pwUser,
      pwBusy,
      pwError,
      openReset,
      submitReset,
      confirm,
      confirmBusy,
      confirmError,
      askStatus,
      askRestore,
      askDelete,
      runConfirm,
      openDetail,
      canGrantAdmin,
      USER_STATUS_LABELS,
      USER_ROLE_LABELS,
      formatBytes,
      formatDateTime,
      formatNumber,
      selectedIds,
      busy,
      flash,
      meId,
      allSelected,
      someSelected,
      toggle,
      toggleAll,
      clearSelection,
      batchRemove,
    };
  },
});
</script>
<style scoped>
.admin-flash {
  margin: 0 0 14px;
  padding: 11px 14px;
  border: 1px solid #cfe8cf;
  border-radius: 10px;
  color: #2f6b2f;
  background: #eef7ee;
  font-size: 0.78rem;
}
.admin-table th.col-check,
.admin-table td.col-check {
  width: 42px;
  text-align: center;
}
.admin-toolbar .batch-inline {
  margin-left: auto;
}
</style>
