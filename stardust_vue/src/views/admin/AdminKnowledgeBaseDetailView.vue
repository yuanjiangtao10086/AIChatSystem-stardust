<template>
  <div v-if="loading" class="admin-loading">加载中…</div>
  <div v-else-if="!detail" class="admin-empty">
    {{ notFound ? "未找到该知识库，或已被删除。" : error }}
  </div>
  <div v-else class="admin-kb-detail">
    <p v-if="error" class="admin-error">{{ error }}</p>
    <p v-if="flash" class="admin-flash">{{ flash }}</p>
    <button class="admin-back-link" type="button" @click="back">
      ← 返回知识库列表
    </button>

    <header class="admin-detail-head">
      <div>
        <h2>{{ detail.name }}</h2>
        <p class="admin-detail-sub">
          {{ detail.userName }} · {{ detail.userEmail }}
        </p>
      </div>
      <span
        class="kb-status"
        :class="detail.status === 'ACTIVE' ? 'kb-active' : 'kb-deleted'"
      >
        {{ BASE_STATUS_LABELS[detail.status] || detail.status }}
      </span>
    </header>

    <section class="admin-card">
      <h3>基本信息</h3>
      <dl class="admin-meta">
        <div>
          <dt>所属用户</dt>
          <dd>
            {{ detail.userName }} <small>{{ detail.userEmail }}</small>
          </dd>
        </div>
        <div>
          <dt>用户 ID</dt>
          <dd class="mono">{{ detail.userId }}</dd>
        </div>
        <div>
          <dt>知识库 ID</dt>
          <dd class="mono">{{ detail.id }}</dd>
        </div>
        <div>
          <dt>简介</dt>
          <dd>{{ detail.description || "—" }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ formatDateTime(detail.createdAt) }}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{{ formatDateTime(detail.updatedAt) }}</dd>
        </div>
      </dl>
    </section>

    <section class="admin-card">
      <h3>处理概况</h3>
      <div class="kb-stats">
        <article>
          <strong>{{ detail.documentCount }}</strong>
          <span>文档总数</span>
        </article>
        <article>
          <strong>{{ detail.readyDocumentCount }}</strong>
          <span>已就绪</span>
        </article>
        <article>
          <strong>{{ detail.failedDocumentCount }}</strong>
          <span>失败</span>
        </article>
        <article>
          <strong>{{ detail.totalChunks }}</strong>
          <span>已索引分块</span>
        </article>
      </div>
    </section>

    <section class="admin-card">
      <div class="admin-card-head">
        <h3>文档（共 {{ detail.documentCount }} 个）</h3>
        <div v-if="selectedIds.length" class="batch-inline">
          <span>已选 {{ selectedIds.length }} 个文档</span>
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
      <div v-if="detail.documents.items.length === 0" class="admin-empty">
        该知识库暂无文档。
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th class="col-check">
                <input
                  class="ui-checkbox"
                  type="checkbox"
                  :checked="
                    !!detail && detail.documents.items.length > 0 && allSelected
                  "
                  :indeterminate.prop="someSelected && !allSelected"
                  aria-label="全选当前页"
                  @change="toggleAll(!allSelected)"
                />
              </th>
              <th>文档</th>
              <th>状态</th>
              <th>分块</th>
              <th>处理版本</th>
              <th>失败原因</th>
              <th>更新时间</th>
              <th class="admin-actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="doc in detail.documents.items" :key="doc.id">
              <td class="col-check">
                <input
                  class="ui-checkbox"
                  type="checkbox"
                  :checked="selectedIds.includes(doc.id)"
                  aria-label="选择文档"
                  @change="toggle(doc.id)"
                />
              </td>
              <td>
                <div class="admin-user-cell">
                  <strong>{{ doc.filename }}</strong>
                  <small class="mono">{{ doc.id }}</small>
                </div>
              </td>
              <td>
                <span
                  class="doc-status"
                  :class="'doc-' + doc.status.toLowerCase()"
                >
                  {{ DOC_STATUS_LABELS[doc.status] || doc.status }}
                </span>
              </td>
              <td>{{ doc.chunkCount }}</td>
              <td>v{{ doc.processingVersion }}</td>
              <td>
                <span
                  v-if="doc.errorCode"
                  class="doc-error"
                  :title="doc.errorMessage || ''"
                >
                  {{ doc.errorCode }}
                </span>
                <span v-else>—</span>
              </td>
              <td>
                <small>{{ formatDateTime(doc.updatedAt) }}</small>
              </td>
              <td class="admin-actions-col">
                <div class="admin-actions">
                  <button
                    class="row-btn"
                    type="button"
                    :disabled="doc.status !== 'FAILED' || busy"
                    @click="askRetry(doc)"
                  >
                    重新处理
                  </button>
                  <button
                    class="row-btn"
                    type="button"
                    :disabled="doc.status !== 'READY' || busy"
                    @click="askRemoveVectors(doc)"
                  >
                    删除向量
                  </button>
                  <button
                    class="row-btn row-danger"
                    type="button"
                    :disabled="busy"
                    @click="askDelete(doc)"
                  >
                    删除文档
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="admin-note">
        重新处理与向量删除由 Spring Boot 调用 Python AI
        服务完成，浏览器不会直接访问 Python；所有操作均写入管理员审计日志。
      </p>
    </section>

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
import { computed, defineComponent, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import AdminConfirmModal from "@/components/admin/AdminConfirmModal.vue";
import {
  deleteAdminDocument,
  deleteAdminDocumentsBatch,
  getAdminKnowledgeBase,
  removeAdminVectors,
  retryAdminDocument,
} from "@/api/admin";
import {
  AdminKnowledgeBaseDetail,
  AdminKnowledgeDocument,
} from "@/types/admin";
import { describeError, formatDateTime, isNotFoundError } from "@/utils/admin";

const BASE_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "启用",
  DELETED: "已删除",
};
const DOC_STATUS_LABELS: Record<string, string> = {
  UPLOADED: "待处理",
  PARSING: "解析中",
  PARSED: "已解析",
  EMBEDDING: "向量化中",
  READY: "就绪",
  FAILED: "失败",
};

type ConfirmKind = "retry" | "vectors" | "delete";

export default defineComponent({
  name: "AdminKnowledgeBaseDetailView",
  components: { AdminConfirmModal },
  props: { id: { type: String, required: true } },
  setup(props) {
    const router = useRouter();
    const detail = ref<AdminKnowledgeBaseDetail | null>(null);
    const loading = ref(false);
    const error = ref("");
    const notFound = ref(false);
    const busy = ref(false);
    const selectedIds = ref<string[]>([]);
    const flash = ref("");
    const allSelected = computed(
      () =>
        !!detail.value &&
        detail.value.documents.items.length > 0 &&
        detail.value.documents.items.every((doc) =>
          selectedIds.value.includes(doc.id)
        )
    );
    const someSelected = computed(() => selectedIds.value.length > 0);
    const toggle = (id: string) => {
      const index = selectedIds.value.indexOf(id);
      if (index >= 0) selectedIds.value.splice(index, 1);
      else selectedIds.value.push(id);
    };
    const toggleAll = (on: boolean) => {
      if (!detail.value) return;
      selectedIds.value = on
        ? detail.value.documents.items.map((doc) => doc.id)
        : [];
    };
    const clearSelection = () => {
      selectedIds.value = [];
    };

    const load = async () => {
      loading.value = true;
      error.value = "";
      notFound.value = false;
      try {
        detail.value = await getAdminKnowledgeBase(props.id);
      } catch (e) {
        detail.value = null;
        notFound.value = isNotFoundError(e);
        error.value = describeError(e, "加载知识库详情失败");
      } finally {
        loading.value = false;
      }
    };

    const back = () => router.push({ name: "admin-rag" });

    const confirm = ref({
      open: false,
      title: "",
      message: "",
      kind: "retry" as ConfirmKind,
      doc: null as AdminKnowledgeDocument | null,
    });
    const confirmBusy = ref(false);
    const confirmError = ref("");

    const ask = (kind: ConfirmKind, doc: AdminKnowledgeDocument) => {
      const messages: Record<ConfirmKind, { title: string; message: string }> =
        {
          retry: {
            title: "重新处理文档",
            message: `确认重新处理文档「${doc.filename}」？该操作会调用 Python AI 服务重新解析与向量化，并写入审计日志。`,
          },
          vectors: {
            title: "删除向量数据",
            message: `确认删除文档「${doc.filename}」的向量索引？文档记录会保留但不再可检索，并写入审计日志。`,
          },
          delete: {
            title: "删除文档",
            message: `确认删除文档「${doc.filename}」？将同时移除分块与向量数据，该操作不可恢复并写入审计日志。`,
          },
        };
      confirm.value = {
        open: true,
        title: messages[kind].title,
        message: messages[kind].message,
        kind,
        doc,
      };
      confirmError.value = "";
    };
    const askRetry = (doc: AdminKnowledgeDocument) => ask("retry", doc);
    const askRemoveVectors = (doc: AdminKnowledgeDocument) =>
      ask("vectors", doc);
    const askDelete = (doc: AdminKnowledgeDocument) => ask("delete", doc);

    const runConfirm = async () => {
      const doc = confirm.value.doc;
      if (!doc) return;
      confirmBusy.value = true;
      confirmError.value = "";
      try {
        if (confirm.value.kind === "retry") {
          await retryAdminDocument(doc.id);
          confirm.value.open = false;
          await load();
        } else if (confirm.value.kind === "vectors") {
          await removeAdminVectors(doc.id);
          confirm.value.open = false;
          await load();
        } else {
          await deleteAdminDocument(doc.id);
          confirm.value.open = false;
          await load();
        }
      } catch (e) {
        confirmError.value = e instanceof Error ? e.message : "操作失败";
      } finally {
        confirmBusy.value = false;
      }
    };

    const batchRemove = async () => {
      if (!selectedIds.value.length) return;
      if (
        !window.confirm(
          `确认批量删除选中的 ${selectedIds.value.length} 个文档吗？将同时移除分块与向量数据，该操作不可恢复并写入审计日志。`
        )
      )
        return;
      busy.value = true;
      flash.value = "";
      try {
        const result = await deleteAdminDocumentsBatch([...selectedIds.value]);
        selectedIds.value = [];
        await load();
        flash.value =
          `已删除 ${result.deleted} 个文档` +
          (result.failures.length
            ? `，${result.failures.length} 个删除失败。`
            : "。");
      } catch (e) {
        flash.value = e instanceof Error ? e.message : "批量删除失败";
      } finally {
        busy.value = false;
      }
    };

    onMounted(load);

    return {
      detail,
      loading,
      error,
      notFound,
      busy,
      back,
      confirm,
      confirmBusy,
      confirmError,
      askRetry,
      askRemoveVectors,
      askDelete,
      runConfirm,
      BASE_STATUS_LABELS,
      DOC_STATUS_LABELS,
      formatDateTime,
      selectedIds,
      flash,
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
.admin-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
