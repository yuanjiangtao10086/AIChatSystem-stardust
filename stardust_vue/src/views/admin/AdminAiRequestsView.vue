<template>
  <div class="admin-requests">
    <div class="admin-toolbar">
      <select v-model="filters.status" class="admin-select">
        <option value="">全部状态</option>
        <option
          v-for="(label, key) in AI_REQUEST_STATUS_LABELS"
          :key="key"
          :value="key"
        >
          {{ label }}
        </option>
      </select>
      <input
        v-model.trim="filters.provider"
        class="admin-user-filter"
        type="text"
        placeholder="服务商 code"
        @keyup.enter="search"
      />
      <input
        v-model.trim="filters.model"
        class="admin-user-filter"
        type="text"
        placeholder="模型 code"
        @keyup.enter="search"
      />
      <input
        v-model.trim="filters.userId"
        class="admin-user-filter"
        type="text"
        placeholder="用户 publicId"
        @keyup.enter="search"
      />
      <input v-model="fromInput" class="admin-date" type="datetime-local" />
      <span class="admin-date-sep">至</span>
      <input v-model="toInput" class="admin-date" type="datetime-local" />
      <button
        class="primary-button"
        type="button"
        :disabled="loading"
        @click="search"
      >
        筛选
      </button>
      <button
        class="quiet-button"
        type="button"
        :disabled="loading"
        @click="reset"
      >
        重置
      </button>
    </div>

    <p v-if="error" class="admin-error">{{ error }}</p>
    <div v-if="loading" class="admin-loading">加载中…</div>
    <div v-else-if="items.length === 0" class="admin-empty">
      没有匹配的调用记录。
    </div>
    <template v-else>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>服务商 / 模型</th>
              <th>用户 / 会话</th>
              <th>状态</th>
              <th>耗时</th>
              <th>Token</th>
              <th>错误码</th>
              <th class="admin-actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.requestId">
              <td>
                <small>{{ formatDateTime(row.createdAt) }}</small>
              </td>
              <td>
                <div class="admin-user-cell">
                  <strong>{{ row.provider }}</strong>
                  <small class="mono">{{ row.model }}</small>
                </div>
              </td>
              <td>
                <div class="admin-user-cell">
                  <span class="mono">{{ row.userId }}</span>
                  <small class="mono">{{ row.conversationId }}</small>
                </div>
              </td>
              <td>
                <span class="status" :class="statusClass(row.status)">
                  {{ AI_REQUEST_STATUS_LABELS[row.status] || row.status }}
                </span>
              </td>
              <td>{{ row.latencyMs ?? "—" }} ms</td>
              <td>
                <small>
                  {{ row.promptTokens ?? "—" }} +
                  {{ row.completionTokens ?? "—" }} =
                  {{ row.totalTokens ?? "—" }}
                </small>
              </td>
              <td>
                <span class="tag">{{ row.errorCode || "—" }}</span>
              </td>
              <td class="admin-actions-col">
                <button class="row-btn" type="button" @click="openDetail(row)">
                  详情
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPager
        :page="page"
        :size="PAGE_SIZE"
        :total-elements="totalElements"
        @change="goToPage"
      />
    </template>

    <AdminModal v-model="detailOpen" title="调用详情" :error="detailError">
      <dl v-if="detail" class="admin-meta">
        <div>
          <dt>Request ID</dt>
          <dd class="mono">{{ detail.requestId }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>
            {{ AI_REQUEST_STATUS_LABELS[detail.status] || detail.status }}
          </dd>
        </div>
        <div>
          <dt>服务商 / 模型</dt>
          <dd>{{ detail.provider }} / {{ detail.model }}</dd>
        </div>
        <div>
          <dt>用户 / 会话</dt>
          <dd class="mono">
            {{ detail.userId }} / {{ detail.conversationId }}
          </dd>
        </div>
        <div>
          <dt>耗时</dt>
          <dd>{{ detail.latencyMs ?? "—" }} ms</dd>
        </div>
        <div>
          <dt>Token（提示 + 补全 = 合计）</dt>
          <dd>
            {{ detail.promptTokens ?? "—" }} +
            {{ detail.completionTokens ?? "—" }} =
            {{ detail.totalTokens ?? "—" }}
          </dd>
        </div>
        <div>
          <dt>错误码</dt>
          <dd>{{ detail.errorCode || "—" }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ formatDateTime(detail.createdAt) }}</dd>
        </div>
      </dl>
      <p v-else class="dash-empty">加载中…</p>
      <template #footer>
        <button class="quiet-button" type="button" @click="detailOpen = false">
          关闭
        </button>
      </template>
    </AdminModal>
  </div>
</template>
<script lang="ts">
import { defineComponent, onMounted, reactive, ref } from "vue";
import AdminPager from "@/components/admin/AdminPager.vue";
import AdminModal from "@/components/admin/AdminModal.vue";
import { getAdminAiRequest, listAdminAiRequests } from "@/api/admin";
import { AdminAiRequest } from "@/types/admin";
import { AI_REQUEST_STATUS_LABELS, formatDateTime } from "@/utils/admin";

const PAGE_SIZE = 20;

export default defineComponent({
  name: "AdminAiRequestsView",
  components: { AdminPager, AdminModal },
  setup() {
    const filters = reactive({
      status: "",
      provider: "",
      model: "",
      userId: "",
    });
    const fromInput = ref("");
    const toInput = ref("");
    const items = ref<AdminAiRequest[]>([]);
    const page = ref(0);
    const totalElements = ref(0);
    const loading = ref(false);
    const error = ref("");

    const detailOpen = ref(false);
    const detail = ref<AdminAiRequest | null>(null);
    const detailError = ref("");

    const toInstant = (value: string): string | undefined => {
      if (!value) return undefined;
      const date = new Date(value);
      return isNaN(date.getTime()) ? undefined : date.toISOString();
    };

    const load = async () => {
      loading.value = true;
      error.value = "";
      try {
        const result = await listAdminAiRequests({
          page: page.value,
          size: PAGE_SIZE,
          status: filters.status || undefined,
          provider: filters.provider || undefined,
          model: filters.model || undefined,
          userId: filters.userId || undefined,
          from: toInstant(fromInput.value),
          to: toInstant(toInput.value),
        });
        items.value = result.items;
        totalElements.value = result.totalElements;
      } catch (e) {
        error.value = e instanceof Error ? e.message : "调用日志加载失败";
      } finally {
        loading.value = false;
      }
    };

    const search = () => {
      page.value = 0;
      return load();
    };
    const goToPage = (next: number) => {
      page.value = next;
      return load();
    };
    const reset = () => {
      filters.status = "";
      filters.provider = "";
      filters.model = "";
      filters.userId = "";
      fromInput.value = "";
      toInput.value = "";
      return search();
    };

    /** The row already carries every field, but the console re-reads it so the drawer is authoritative. */
    const openDetail = async (row: AdminAiRequest) => {
      detail.value = row;
      detailError.value = "";
      detailOpen.value = true;
      try {
        detail.value = await getAdminAiRequest(row.requestId);
      } catch (e) {
        detailError.value = e instanceof Error ? e.message : "详情加载失败";
      }
    };

    const statusClass = (status: string) =>
      status === "COMPLETED"
        ? "status-normal"
        : status === "FAILED"
        ? "status-banned"
        : "status-disabled";

    onMounted(load);

    return {
      filters,
      fromInput,
      toInput,
      items,
      page,
      totalElements,
      loading,
      error,
      search,
      reset,
      goToPage,
      openDetail,
      detailOpen,
      detail,
      detailError,
      statusClass,
      formatDateTime,
      AI_REQUEST_STATUS_LABELS,
      PAGE_SIZE,
    };
  },
});
</script>
<style scoped>
.dash-empty {
  color: var(--ink-soft, #6e6e80);
}
</style>
