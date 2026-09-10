<template>
  <div class="admin-files">
    <div class="admin-toolbar">
      <input
        v-model.trim="search"
        class="admin-search"
        placeholder="搜索文件名"
        @input="onSearchInput"
      />
      <input
        v-model.trim="userSearch"
        class="admin-search admin-user-filter"
        placeholder="按用户名 / 邮箱筛选"
        @input="onSearchInput"
      />
      <input
        v-model.trim="mime"
        class="admin-search admin-mime-filter"
        placeholder="MIME 前缀，如 image/"
        @change="reload"
      />
      <select v-model="status" class="admin-select" @change="reload">
        <option value="">全部状态</option>
        <option v-for="s in FILE_STATUSES" :key="s" :value="s">
          {{ FILE_STATUS_LABELS[s] }}
        </option>
      </select>
    </div>

    <div class="admin-toolbar admin-toolbar--sub">
      <input
        v-model.trim="minMb"
        class="admin-search admin-size-input"
        type="number"
        min="0"
        placeholder="最小(MB)"
        @change="reload"
      />
      <span class="admin-date-sep">—</span>
      <input
        v-model.trim="maxMb"
        class="admin-search admin-size-input"
        type="number"
        min="0"
        placeholder="最大(MB)"
        @change="reload"
      />
      <input v-model="from" type="date" class="admin-date" @change="reload" />
      <span class="admin-date-sep">至</span>
      <input v-model="to" type="date" class="admin-date" @change="reload" />
      <button class="row-btn" type="button" @click="resetFilters">重置</button>
    </div>

    <p v-if="error" class="admin-error">{{ error }}</p>
    <div v-if="loading" class="admin-loading">加载中…</div>
    <div
      v-else-if="!pageData || pageData.items.length === 0"
      class="admin-empty"
    >
      没有匹配的文件。
    </div>

    <template v-else>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>文件</th>
              <th>所属用户</th>
              <th>类型</th>
              <th>大小</th>
              <th>状态</th>
              <th>引用</th>
              <th>上传时间</th>
              <th class="admin-actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="file in pageData.items" :key="file.id">
              <td>
                <button
                  class="admin-user-link"
                  type="button"
                  @click="openDetail(file)"
                >
                  <strong>{{ file.name }}</strong>
                  <small class="mono">{{ file.id }}</small>
                </button>
              </td>
              <td>
                <div class="admin-user-cell">
                  <strong>{{ file.userName }}</strong>
                  <small>{{ file.userEmail }}</small>
                </div>
              </td>
              <td>
                <span class="tag">{{ file.mime }}</span>
              </td>
              <td>{{ formatBytes(file.sizeBytes) }}</td>
              <td>
                <span
                  class="file-status"
                  :class="'file-' + file.status.toLowerCase()"
                >
                  {{ FILE_STATUS_LABELS[file.status] || file.status }}
                </span>
              </td>
              <td>
                <span
                  class="file-ref"
                  :class="file.referenced ? 'used' : 'idle'"
                >
                  {{ file.referenced ? "使用中" : "空闲" }}
                </span>
              </td>
              <td>
                <small>{{ formatDateTime(file.createdAt) }}</small>
              </td>
              <td class="admin-actions-col">
                <div class="admin-actions">
                  <button
                    class="row-btn"
                    type="button"
                    @click="openDetail(file)"
                  >
                    查看
                  </button>
                  <button
                    class="row-btn"
                    type="button"
                    :disabled="busyId === file.id"
                    @click="download(file)"
                  >
                    下载
                  </button>
                  <button
                    class="row-btn row-danger"
                    type="button"
                    :disabled="file.referenced || file.status !== 'AVAILABLE'"
                    :title="deleteHint(file)"
                    @click="askDelete(file)"
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
import {
  deleteAdminFile,
  downloadAdminFile,
  listAdminFiles,
} from "@/api/admin";
import { AdminFile, AdminFilePage } from "@/types/admin";
import { formatBytes, formatDateTime } from "@/utils/admin";

const FILE_STATUSES = [
  "UPLOADING",
  "AVAILABLE",
  "DELETING",
  "FAILED",
  "DELETED",
];
const FILE_STATUS_LABELS: Record<string, string> = {
  UPLOADING: "上传中",
  AVAILABLE: "可用",
  DELETING: "删除中",
  FAILED: "失败",
  DELETED: "已删除",
};

interface ConfirmState {
  open: boolean;
  title: string;
  message: string;
  file: AdminFile | null;
}

export default defineComponent({
  name: "AdminFilesView",
  components: { AdminPager, AdminConfirmModal },
  setup() {
    const router = useRouter();

    const search = ref("");
    const userSearch = ref("");
    const mime = ref("");
    const status = ref("");
    const minMb = ref("");
    const maxMb = ref("");
    const from = ref("");
    const to = ref("");

    const page = ref(0);
    const pageData = ref<AdminFilePage | null>(null);
    const loading = ref(false);
    const error = ref("");
    const busyId = ref("");

    const toInstantStart = (d: string) => (d ? `${d}T00:00:00Z` : "");
    const toInstantEnd = (d: string) => {
      if (!d) return "";
      const dt = new Date(`${d}T00:00:00Z`);
      dt.setUTCDate(dt.getUTCDate() + 1);
      return dt.toISOString().replace(".000Z", "Z");
    };
    /** MB input -> bytes, matching the backend minSize/maxSize byte filters. */
    const toBytes = (mb: string) => {
      const value = Number(mb);
      return mb !== "" && Number.isFinite(value) && value > 0
        ? String(Math.round(value * 1024 * 1024))
        : "";
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
    const resetFilters = () => {
      search.value = "";
      userSearch.value = "";
      mime.value = "";
      status.value = "";
      minMb.value = "";
      maxMb.value = "";
      from.value = "";
      to.value = "";
      reload();
    };

    const load = async () => {
      loading.value = true;
      error.value = "";
      try {
        pageData.value = await listAdminFiles({
          page: page.value,
          search: search.value,
          userSearch: userSearch.value,
          mime: mime.value,
          status: status.value,
          minSize: toBytes(minMb.value),
          maxSize: toBytes(maxMb.value),
          from: toInstantStart(from.value),
          to: toInstantEnd(to.value),
        });
      } catch (e) {
        error.value = e instanceof Error ? e.message : "加载文件列表失败";
      } finally {
        loading.value = false;
      }
    };

    const openDetail = (file: AdminFile) =>
      router.push({ name: "admin-file-detail", params: { id: file.id } });

    const download = async (file: AdminFile) => {
      busyId.value = file.id;
      error.value = "";
      try {
        await downloadAdminFile(file.id, file.name);
      } catch (e) {
        error.value = e instanceof Error ? e.message : "下载失败";
      } finally {
        busyId.value = "";
      }
    };

    const deleteHint = (file: AdminFile) => {
      if (file.referenced) return "文件仍被会话或知识库引用，请先解除引用";
      if (file.status !== "AVAILABLE") return "仅可删除状态为“可用”的文件";
      return "删除违规文件（写入审计日志）";
    };

    const confirm = ref<ConfirmState>({
      open: false,
      title: "",
      message: "",
      file: null,
    });
    const confirmBusy = ref(false);
    const confirmError = ref("");
    const askDelete = (file: AdminFile) => {
      confirm.value = {
        open: true,
        title: "删除违规文件",
        message: `确认删除文件「${file.name}」（用户 ${file.userEmail}）？该操作会写入审计日志且不可恢复。`,
        file,
      };
      confirmError.value = "";
    };
    const runConfirm = async () => {
      const file = confirm.value.file;
      if (!file) return;
      confirmBusy.value = true;
      confirmError.value = "";
      try {
        await deleteAdminFile(file.id);
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
      userSearch,
      mime,
      status,
      minMb,
      maxMb,
      from,
      to,
      page,
      pageData,
      loading,
      error,
      busyId,
      onSearchInput,
      reload,
      changePage,
      resetFilters,
      openDetail,
      download,
      deleteHint,
      confirm,
      confirmBusy,
      confirmError,
      askDelete,
      runConfirm,
      FILE_STATUSES,
      FILE_STATUS_LABELS,
      formatBytes,
      formatDateTime,
    };
  },
});
</script>
