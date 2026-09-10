<template>
  <div class="admin-knowledge">
    <div class="admin-toolbar">
      <input
        v-model.trim="search"
        class="admin-search"
        placeholder="搜索知识库名称"
        @input="onSearchInput"
      />
      <input
        v-model.trim="userSearch"
        class="admin-search admin-user-filter"
        placeholder="按用户名 / 邮箱筛选"
        @input="onSearchInput"
      />
      <select v-model="status" class="admin-select" @change="reload">
        <option value="">全部状态</option>
        <option v-for="s in BASE_STATUSES" :key="s" :value="s">
          {{ BASE_STATUS_LABELS[s] }}
        </option>
      </select>
    </div>

    <p v-if="error" class="admin-error">{{ error }}</p>
    <div v-if="loading" class="admin-loading">加载中…</div>
    <div
      v-else-if="!pageData || pageData.items.length === 0"
      class="admin-empty"
    >
      没有匹配的知识库。
    </div>

    <template v-else>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>知识库</th>
              <th>所属用户</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>更新时间</th>
              <th class="admin-actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="base in pageData.items" :key="base.id">
              <td>
                <button
                  class="admin-user-link"
                  type="button"
                  @click="openDetail(base)"
                >
                  <strong>{{ base.name }}</strong>
                  <small class="mono">{{ base.id }}</small>
                </button>
              </td>
              <td>
                <div class="admin-user-cell">
                  <strong>{{ base.userName }}</strong>
                  <small>{{ base.userEmail }}</small>
                </div>
              </td>
              <td>
                <span
                  class="kb-status"
                  :class="base.status === 'ACTIVE' ? 'kb-active' : 'kb-deleted'"
                >
                  {{ BASE_STATUS_LABELS[base.status] || base.status }}
                </span>
              </td>
              <td>
                <small>{{ formatDateTime(base.createdAt) }}</small>
              </td>
              <td>
                <small>{{ formatDateTime(base.updatedAt) }}</small>
              </td>
              <td class="admin-actions-col">
                <div class="admin-actions">
                  <button
                    class="row-btn"
                    type="button"
                    @click="openDetail(base)"
                  >
                    查看
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
  </div>
</template>
<script lang="ts">
import { defineComponent, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import AdminPager from "@/components/admin/AdminPager.vue";
import { listAdminKnowledgeBases } from "@/api/admin";
import { AdminKnowledgeBase, AdminKnowledgeBasePage } from "@/types/admin";
import { formatDateTime } from "@/utils/admin";

const BASE_STATUSES = ["ACTIVE", "DELETED"];
const BASE_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "启用",
  DELETED: "已删除",
};

export default defineComponent({
  name: "AdminKnowledgeView",
  components: { AdminPager },
  setup() {
    const router = useRouter();

    const search = ref("");
    const userSearch = ref("");
    const status = ref("");
    const page = ref(0);
    const pageData = ref<AdminKnowledgeBasePage | null>(null);
    const loading = ref(false);
    const error = ref("");

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
        pageData.value = await listAdminKnowledgeBases({
          page: page.value,
          search: search.value,
          userSearch: userSearch.value,
          status: status.value,
        });
      } catch (e) {
        error.value = e instanceof Error ? e.message : "加载知识库列表失败";
      } finally {
        loading.value = false;
      }
    };

    const openDetail = (base: AdminKnowledgeBase) =>
      router.push({ name: "admin-rag-detail", params: { id: base.id } });

    onMounted(load);
    onUnmounted(() => window.clearTimeout(searchTimer));

    return {
      search,
      userSearch,
      status,
      page,
      pageData,
      loading,
      error,
      onSearchInput,
      reload,
      changePage,
      openDetail,
      BASE_STATUSES,
      BASE_STATUS_LABELS,
      formatDateTime,
    };
  },
});
</script>
