<template>
  <div v-if="loading" class="admin-loading">加载中…</div>
  <div v-else-if="!detail" class="admin-empty">
    {{ notFound ? "未找到该文件，或已被删除。" : error }}
  </div>
  <div v-else class="admin-file-detail">
    <p v-if="error" class="admin-error">{{ error }}</p>
    <button class="admin-back-link" type="button" @click="back">
      ← 返回文件列表
    </button>

    <header class="admin-detail-head">
      <div>
        <h2>{{ detail.name }}</h2>
        <p class="admin-detail-sub">
          {{ detail.userName }} · {{ detail.userEmail }}
        </p>
      </div>
      <span class="file-status" :class="'file-' + detail.status.toLowerCase()">
        {{ FILE_STATUS_LABELS[detail.status] || detail.status }}
      </span>
    </header>

    <section class="admin-card">
      <h3>文件元数据</h3>
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
          <dt>文件 ID</dt>
          <dd class="mono">{{ detail.id }}</dd>
        </div>
        <div>
          <dt>扩展名</dt>
          <dd>{{ detail.extension || "—" }}</dd>
        </div>
        <div>
          <dt>声明类型</dt>
          <dd>{{ detail.declaredMime }}</dd>
        </div>
        <div>
          <dt>检测类型</dt>
          <dd>{{ detail.detectedMime }}</dd>
        </div>
        <div>
          <dt>大小</dt>
          <dd>{{ formatBytes(detail.sizeBytes) }}</dd>
        </div>
        <div>
          <dt>存储后端</dt>
          <dd>{{ detail.storageProvider }}</dd>
        </div>
        <div>
          <dt>SHA-256</dt>
          <dd class="mono" :title="detail.sha256">{{ detail.sha256 }}</dd>
        </div>
        <div>
          <dt>上传时间</dt>
          <dd>{{ formatDateTime(detail.createdAt) }}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{{ formatDateTime(detail.updatedAt) }}</dd>
        </div>
        <div v-if="detail.metadataJson">
          <dt>元数据</dt>
          <dd class="mono">{{ detail.metadataJson }}</dd>
        </div>
      </dl>
      <p class="admin-note">
        出于安全考虑，服务端不会返回文件的内部存储路径；下载统一经过受审计的管理员接口。
      </p>
    </section>

    <section class="admin-card">
      <h3>引用情况</h3>
      <dl class="admin-meta">
        <div>
          <dt>会话消息附件引用</dt>
          <dd>{{ detail.attachmentCount }} 条</dd>
        </div>
        <div>
          <dt>知识库文档引用</dt>
          <dd>{{ detail.knowledgeDocumentCount }} 条</dd>
        </div>
        <div>
          <dt>整体状态</dt>
          <dd>
            <span class="file-ref" :class="detail.referenced ? 'used' : 'idle'">
              {{ detail.referenced ? "被引用中" : "空闲" }}
            </span>
          </dd>
        </div>
      </dl>
      <p v-if="detail.referenced" class="admin-note">
        该文件仍被引用。请先删除引用的知识库文档或会话消息，之后才能删除文件本体。
      </p>
    </section>

    <section class="admin-card">
      <h3>操作</h3>
      <div class="admin-actions">
        <button
          class="row-btn"
          type="button"
          :disabled="busy"
          @click="download"
        >
          下载文件
        </button>
        <button
          class="row-btn row-danger"
          type="button"
          :disabled="detail.referenced || detail.status !== 'AVAILABLE' || busy"
          @click="askDelete"
        >
          删除违规文件
        </button>
      </div>
      <p class="admin-note">查看本页、下载与删除都会写入管理员审计日志。</p>
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
import { deleteAdminFile, downloadAdminFile, getAdminFile } from "@/api/admin";
import { AdminFileDetail } from "@/types/admin";
import {
  describeError,
  formatBytes,
  formatDateTime,
  isNotFoundError,
} from "@/utils/admin";

const FILE_STATUS_LABELS: Record<string, string> = {
  UPLOADING: "上传中",
  AVAILABLE: "可用",
  DELETING: "删除中",
  FAILED: "失败",
  DELETED: "已删除",
};

export default defineComponent({
  name: "AdminFileDetailView",
  components: { AdminConfirmModal },
  props: { id: { type: String, required: true } },
  setup(props) {
    const router = useRouter();
    const detail = ref<AdminFileDetail | null>(null);
    const loading = ref(false);
    const error = ref("");
    const notFound = ref(false);
    const busy = ref(false);

    const load = async () => {
      loading.value = true;
      error.value = "";
      notFound.value = false;
      try {
        detail.value = await getAdminFile(props.id);
      } catch (e) {
        detail.value = null;
        notFound.value = isNotFoundError(e);
        error.value = describeError(e, "加载文件详情失败");
      } finally {
        loading.value = false;
      }
    };

    const back = () => router.push({ name: "admin-files" });

    const download = async () => {
      if (!detail.value) return;
      busy.value = true;
      error.value = "";
      try {
        await downloadAdminFile(detail.value.id, detail.value.name);
      } catch (e) {
        error.value = e instanceof Error ? e.message : "下载失败";
      } finally {
        busy.value = false;
      }
    };

    const confirm = ref({ open: false, title: "", message: "" });
    const confirmBusy = ref(false);
    const confirmError = ref("");
    const askDelete = () => {
      confirm.value = {
        open: true,
        title: "删除违规文件",
        message: `确认删除文件「${detail.value?.name}」（用户 ${detail.value?.userEmail}）？该操作会写入审计日志且不可恢复。`,
      };
      confirmError.value = "";
    };
    const runConfirm = async () => {
      if (!detail.value) return;
      confirmBusy.value = true;
      confirmError.value = "";
      try {
        await deleteAdminFile(detail.value.id);
        confirm.value.open = false;
        router.push({ name: "admin-files" });
      } catch (e) {
        confirmError.value = e instanceof Error ? e.message : "删除失败";
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
      download,
      confirm,
      confirmBusy,
      confirmError,
      askDelete,
      runConfirm,
      FILE_STATUS_LABELS,
      formatBytes,
      formatDateTime,
    };
  },
});
</script>
