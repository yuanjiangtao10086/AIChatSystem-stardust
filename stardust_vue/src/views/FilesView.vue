<template>
  <section class="files-page">
    <header class="files-heading">
      <div>
        <p class="eyebrow">共享资源库</p>
        <h1>一次上传，处处可用</h1>
        <p>文件上传一次，即可在任何对话中作为附件引用。</p>
      </div>
      <FileDropzone :disabled="uploading" @upload="upload" />
    </header>
    <div class="file-toolbar">
      <label
        ><span>⌕</span
        ><input
          v-model="search"
          type="search"
          placeholder="搜索文件"
          aria-label="搜索文件"
          @keyup.enter="load(0)" /></label
      ><button type="button" @click="load(0)">搜索</button
      ><span v-if="message" role="status">{{ message }}</span>
    </div>
    <div class="workspace-grid">
      <main>
        <FileTable
          :files="files"
          :selected-id="selected?.id"
          @select="selected = $event"
        />
        <nav v-if="page.totalPages > 1" class="pager" aria-label="文件分页">
          <button :disabled="page.page === 0" @click="load(page.page - 1)">
            上一页</button
          ><span>{{ page.page + 1 }} / {{ page.totalPages }}</span
          ><button :disabled="!page.hasNext" @click="load(page.page + 1)">
            下一页
          </button>
        </nav>
      </main>
      <div class="side-stack">
        <StorageMeter :usage="usage" /><FileDetailPanel
          :file="selected"
          @download="download"
          @rename="rename"
          @delete="remove"
        />
      </div>
    </div>
  </section>
</template>
<script lang="ts">
import { defineComponent, onMounted, ref } from "vue";
import { ApiError } from "@/api/client";
import {
  deleteFile,
  downloadFile,
  getStorageUsage,
  listFiles,
  renameFile,
  uploadFile,
} from "@/api/files";
import FileDetailPanel from "@/components/files/FileDetailPanel.vue";
import FileDropzone from "@/components/files/FileDropzone.vue";
import FileTable from "@/components/files/FileTable.vue";
import StorageMeter from "@/components/files/StorageMeter.vue";
import { FilePage, StorageUsage, UserFile } from "@/types/file";
const emptyPage = (): FilePage => ({
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
});
export default defineComponent({
  name: "FilesView",
  components: { FileDetailPanel, FileDropzone, FileTable, StorageMeter },
  setup() {
    const files = ref<UserFile[]>([]);
    const page = ref(emptyPage());
    const usage = ref<StorageUsage | null>(null);
    const selected = ref<UserFile | null>(null);
    const search = ref("");
    const uploading = ref(false);
    const message = ref("");
    const describe = (error: unknown) =>
      error instanceof ApiError
        ? `${error.message}${error.requestId ? ` · ${error.requestId}` : ""}`
        : "文件操作未能完成。";
    const load = async (index = page.value.page) => {
      message.value = "";
      try {
        const [next, nextUsage] = await Promise.all([
          listFiles({ page: index, search: search.value }),
          getStorageUsage(),
        ]);
        page.value = next;
        files.value = next.items;
        usage.value = nextUsage;
        if (selected.value)
          selected.value =
            next.items.find((item) => item.id === selected.value?.id) || null;
      } catch (error) {
        message.value = describe(error);
      }
    };
    const upload = async (file: File) => {
      uploading.value = true;
      message.value = "";
      try {
        const created = await uploadFile(file);
        await load(0);
        selected.value = created;
        message.value = `${created.name} 上传成功。`;
      } catch (error) {
        message.value = describe(error);
      } finally {
        uploading.value = false;
      }
    };
    const rename = async (file: UserFile, name: string) => {
      try {
        selected.value = await renameFile(file.id, name);
        await load();
        message.value = "文件已重命名。";
      } catch (error) {
        message.value = describe(error);
      }
    };
    const download = async (file: UserFile) => {
      try {
        await downloadFile(file);
      } catch (error) {
        message.value = describe(error);
      }
    };
    const remove = async (file: UserFile) => {
      if (
        !window.confirm(
          `确认删除 ${file.name} 吗？已被对话引用的文件无法删除。`
        )
      )
        return;
      try {
        await deleteFile(file.id);
        selected.value = null;
        await load();
        message.value = "文件已删除。";
      } catch (error) {
        message.value = describe(error);
      }
    };
    onMounted(() => load(0));
    return {
      download,
      files,
      load,
      message,
      page,
      remove,
      rename,
      search,
      selected,
      upload,
      uploading,
      usage,
    };
  },
});
</script>
<style scoped>
.files-page {
  width: min(1180px, 92vw);
  margin: 0 auto;
  padding: 64px 0 80px;
}
.files-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
  gap: 56px;
  align-items: end;
}
.eyebrow {
  margin: 0 0 10px;
  color: var(--ink-faint);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.files-heading h1 {
  max-width: 650px;
  margin: 0;
  color: var(--ink);
  font-size: clamp(2rem, 4vw, 3.4rem);
  font-weight: 700;
  line-height: 1.03;
  letter-spacing: -0.02em;
}
.files-heading > div > p:last-child {
  max-width: 580px;
  margin: 16px 0 0;
  color: var(--ink-soft);
  line-height: 1.65;
}
.file-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 34px 0 14px;
}
.file-toolbar label {
  display: flex;
  align-items: center;
  gap: 8px;
  width: min(420px, 100%);
  padding: 0 11px;
  border: 1px solid #d7dce7;
  border-radius: 9px;
  background: #fff;
}
.file-toolbar input {
  width: 100%;
  min-height: 39px;
  border: 0;
  outline: 0;
}
.file-toolbar button,
.pager button {
  padding: 9px 12px;
  border: 1px solid #d7dce7;
  border-radius: 8px;
  color: #4e576c;
  background: #fff;
  cursor: pointer;
}
.file-toolbar > span {
  margin-left: auto;
  color: var(--ink-faint);
  font-size: 0.72rem;
}
.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: start;
}
.side-stack {
  display: grid;
  gap: 16px;
}
.pager {
  display: flex;
  justify-content: center;
  gap: 12px;
  align-items: center;
  margin-top: 14px;
  color: var(--ink-faint);
  font-size: 0.74rem;
}
.pager button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
@media (max-width: 850px) {
  .files-page {
    padding-top: 38px;
  }
  .files-heading {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .workspace-grid {
    grid-template-columns: 1fr;
  }
  .side-stack {
    grid-row: 1;
    grid-template-columns: 1fr 1fr;
  }
  .detail-panel {
    min-height: 0;
  }
}
@media (max-width: 600px) {
  .files-page {
    width: min(100% - 24px, 1180px);
    padding-top: 28px;
  }
  .files-heading h1 {
    font-size: 2rem;
  }
  .side-stack {
    grid-row: auto;
    grid-template-columns: 1fr;
  }
  .file-toolbar {
    flex-wrap: wrap;
  }
  .file-toolbar label {
    flex: 1;
  }
  .file-toolbar > span {
    width: 100%;
    margin-left: 0;
  }
}
</style>
