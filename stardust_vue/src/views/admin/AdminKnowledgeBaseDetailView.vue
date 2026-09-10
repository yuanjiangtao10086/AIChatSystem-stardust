<template>
  <div v-if="loading" class="admin-loading">加载中…</div>
  <div v-else-if="!detail" class="admin-empty">
    {{ notFound ? "未找到该知识库，或已被删除。" : error }}
  </div>
  <div v-else class="admin-kb-detail">
    <p v-if="error" class="admin-error">{{ error }}</p>
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
      <h3>文档（共 {{ detail.documentCount }} 个）</h3>
      <div v-if="detail.documents.items.length === 0" class="admin-empty">
        该知识库暂无文档。
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
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
import { defineComponent, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import AdminConfirmModal from "@/components/admin/AdminConfirmModal.vue";
import {
  deleteAdminDocument,
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
    };
  },
});
</script>
