<template>
  <div class="admin-conversations">
    <div class="admin-toolbar">
      <input
        v-model.trim="search"
        class="admin-search"
        placeholder="搜索标题 / 用户名 / 邮箱 / 消息内容"
        @input="onSearchInput"
      />
      <input
        v-model.trim="userId"
        class="admin-search admin-user-filter"
        placeholder="用户 ID（可选）"
        @change="reload"
      />
      <input v-model="from" type="date" class="admin-date" @change="reload" />
      <span class="admin-date-sep">至</span>
      <input v-model="to" type="date" class="admin-date" @change="reload" />
    </div>

    <p v-if="error" class="admin-error">{{ error }}</p>
    <div v-if="loading" class="admin-loading">加载中…</div>
    <div
      v-else-if="!pageData || pageData.items.length === 0"
      class="admin-empty"
    >
      没有匹配的会话。
    </div>

    <template v-else>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>标题</th>
              <th>状态</th>
              <th>消息数</th>
              <th>创建时间</th>
              <th class="admin-actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in pageData.items" :key="c.id">
              <td>
                <div class="admin-user-cell">
                  <strong>{{ c.userName }}</strong>
                  <small>{{ c.userEmail }}</small>
                </div>
              </td>
              <td>
                <button
                  class="admin-user-link"
                  type="button"
                  @click="openDetail(c)"
                >
                  {{ c.title }}
                </button>
              </td>
              <td>
                <span
                  class="conv-status"
                  :class="'conv-' + c.status.toLowerCase()"
                >
                  {{ CONV_STATUS_LABELS[c.status] || c.status }}
                </span>
              </td>
              <td>{{ c.messageCount }}</td>
              <td>
                <small>{{ formatDateTime(c.createdAt) }}</small>
              </td>
              <td class="admin-actions-col">
                <div class="admin-actions">
                  <button class="row-btn" type="button" @click="openDetail(c)">
                    查看
                  </button>
                  <button
                    class="row-btn row-danger"
                    type="button"
                    @click="askDelete(c)"
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
import { defineComponent, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import AdminPager from "@/components/admin/AdminPager.vue";
import AdminConfirmModal from "@/components/admin/AdminConfirmModal.vue";
import { deleteAdminConversation, listAdminConversations } from "@/api/admin";
import { AdminConversation, AdminConversationPage } from "@/types/admin";
import { formatDateTime } from "@/utils/admin";

interface ConfirmState {
  open: boolean;
  title: string;
  message: string;
  conversation: AdminConversation | null;
}

const CONV_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "进行中",
  ARCHIVED: "已归档",
};

export default defineComponent({
  name: "AdminConversationsView",
  components: { AdminPager, AdminConfirmModal },
  setup() {
    const router = useRouter();

    const search = ref("");
    const userId = ref("");
    const from = ref("");
    const to = ref("");
    const page = ref(0);
    const pageData = ref<AdminConversationPage | null>(null);
    const loading = ref(false);
    const error = ref("");

    const toInstantStart = (d: string) => (d ? `${d}T00:00:00Z` : "");
    const toInstantEnd = (d: string) => {
      if (!d) return "";
      const dt = new Date(`${d}T00:00:00Z`);
      dt.setUTCDate(dt.getUTCDate() + 1);
      return dt.toISOString().replace(".000Z", "Z");
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
        pageData.value = await listAdminConversations({
          page: page.value,
          search: search.value,
          userId: userId.value,
          from: toInstantStart(from.value),
          to: toInstantEnd(to.value),
        });
      } catch (e) {
        error.value = e instanceof Error ? e.message : "加载会话列表失败";
      } finally {
        loading.value = false;
      }
    };

    const openDetail = (c: AdminConversation) =>
      router.push({ name: "admin-conversation-detail", params: { id: c.id } });

    const confirm = ref<ConfirmState>({
      open: false,
      title: "",
      message: "",
      conversation: null,
    });
    const confirmBusy = ref(false);
    const confirmError = ref("");
    const askDelete = (c: AdminConversation) => {
      confirm.value = {
        open: true,
        title: "删除会话",
        message: `确认软删除会话「${c.title}」（用户 ${c.userEmail}）？该操作会写入审计日志。`,
        conversation: c,
      };
      confirmError.value = "";
    };
    const runConfirm = async () => {
      const c = confirm.value.conversation;
      if (!c) return;
      confirmBusy.value = true;
      confirmError.value = "";
      try {
        await deleteAdminConversation(c.id);
        confirm.value.open = false;
        await load();
      } catch (e) {
        confirmError.value = e instanceof Error ? e.message : "删除失败";
      } finally {
        confirmBusy.value = false;
      }
    };

    onMounted(load);
    onUnmounted(() => window.clearTimeout(searchTimer));

    return {
      search,
      userId,
      from,
      to,
      page,
      pageData,
      loading,
      error,
      onSearchInput,
      reload,
      changePage,
      openDetail,
      confirm,
      confirmBusy,
      confirmError,
      askDelete,
      runConfirm,
      CONV_STATUS_LABELS,
      formatDateTime,
    };
  },
});
</script>
