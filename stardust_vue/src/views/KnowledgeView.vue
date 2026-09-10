<template>
  <section class="knowledge-page">
    <aside class="library-rail">
      <header>
        <span>✦</span>
        <div><strong>知识库</strong><small>为对话提供检索来源</small></div>
      </header>
      <label class="search"
        ><span>⌕</span
        ><input
          v-model="search"
          type="search"
          placeholder="搜索知识库"
          @input="loadBases"
      /></label>
      <KnowledgeBaseList
        :items="bases"
        :active-id="selectedId || undefined"
        :loading="loading"
        @select="select"
      />
      <button class="new-base" type="button" @click="creating = true">
        ＋ 新建知识库
      </button>
    </aside>

    <main class="knowledge-main">
      <header class="page-header">
        <div>
          <p class="eyebrow">检索工作区</p>
          <h1>{{ selected?.name || "知识库" }}</h1>
          <p>
            {{
              selected?.description ||
              "将文档组织为聚焦的知识库，再在对话中挂载使用。"
            }}
          </p>
        </div>
        <div v-if="selected" class="header-actions">
          <button @click="beginEdit">编辑</button
          ><button class="danger" @click="removeBase">删除</button>
        </div>
      </header>

      <section v-if="selected" class="ingest-card">
        <div>
          <span class="step">01</span>
          <div>
            <strong>添加原始资料</strong
            ><small>首批解析适配器支持 TXT、Markdown、CSV 与 JSON。</small>
          </div>
        </div>
        <label :class="['upload', { busy: uploading }]">
          <input
            type="file"
            accept=".txt,.md,.markdown,.csv,.json,text/plain,text/markdown,text/csv,application/json"
            :disabled="uploading"
            @change="upload"
          />
          <span>{{ uploading ? "正在上传并索引…" : "选择文档" }}</span>
        </label>
      </section>

      <section v-if="selected" class="document-section">
        <div class="section-title">
          <div>
            <span class="step">02</span>
            <div>
              <strong>处理流水线</strong
              ><small>解析 → 清洗 → 切分 → 向量化 → 入库</small>
            </div>
          </div>
          <span>共 {{ documents.length }} 个文档</span>
        </div>
        <DocumentPipelineList
          :items="documents"
          @retry="retry"
          @delete="removeDocument"
        />
      </section>
      <div v-else class="welcome">
        <span>✦</span>
        <h2>构建一个聚焦的资料库</h2>
        <p>创建一个知识库、添加文档，然后在对话的「知识库」菜单中选择它。</p>
        <button @click="creating = true">创建第一个知识库</button>
      </div>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    </main>

    <div
      v-if="creating || editing"
      class="modal-backdrop"
      @click.self="closeModal"
    >
      <form class="modal" @submit.prevent="saveBase">
        <p class="eyebrow">{{ editing ? "编辑知识库" : "新建知识库" }}</p>
        <h2>
          {{ editing ? "完善知识库说明" : "这个知识库要承载哪些内容？" }}
        </h2>
        <label
          >名称<input v-model="form.name" maxlength="120" required autofocus
        /></label>
        <label
          >描述<textarea
            v-model="form.description"
            maxlength="1000"
            rows="4"
          ></textarea>
        </label>
        <div>
          <button type="button" @click="closeModal">取消</button
          ><button class="primary" :disabled="saving">
            {{ saving ? "正在保存…" : "保存知识库" }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<script lang="ts">
import { computed, defineComponent, onMounted, reactive, ref } from "vue";
import { ApiError } from "@/api/client";
import { uploadFile } from "@/api/files";
import {
  addKnowledgeDocument,
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  listKnowledgeBases,
  listKnowledgeDocuments,
  retryKnowledgeDocument,
  updateKnowledgeBase,
} from "@/api/knowledge";
import { KnowledgeBase, KnowledgeDocument } from "@/types/knowledge";
import KnowledgeBaseList from "@/components/knowledge/KnowledgeBaseList.vue";
import DocumentPipelineList from "@/components/knowledge/DocumentPipelineList.vue";

export default defineComponent({
  name: "KnowledgeView",
  components: { KnowledgeBaseList, DocumentPipelineList },
  setup() {
    const bases = ref<KnowledgeBase[]>([]),
      documents = ref<KnowledgeDocument[]>([]);
    const selectedId = ref<string | null>(null),
      search = ref(""),
      notice = ref("");
    const loading = ref(false),
      uploading = ref(false),
      creating = ref(false),
      editing = ref(false),
      saving = ref(false);
    const form = reactive({ name: "", description: "" });
    const selected = computed(
      () => bases.value.find((item) => item.id === selectedId.value) || null
    );
    const describe = (cause: unknown) =>
      cause instanceof ApiError
        ? `${cause.message}${cause.requestId ? ` · ${cause.requestId}` : ""}`
        : "知识库操作失败。";
    const loadDocuments = async () => {
      documents.value = selectedId.value
        ? (await listKnowledgeDocuments(selectedId.value)).items
        : [];
    };
    const loadBases = async () => {
      loading.value = true;
      try {
        bases.value = (
          await listKnowledgeBases({ size: 100, search: search.value })
        ).items;
        if (
          !selectedId.value ||
          !bases.value.some((item) => item.id === selectedId.value)
        )
          selectedId.value = bases.value[0]?.id || null;
        await loadDocuments();
      } catch (cause) {
        notice.value = describe(cause);
      } finally {
        loading.value = false;
      }
    };
    const select = async (id: string) => {
      selectedId.value = id;
      await loadDocuments();
    };
    const closeModal = () => {
      creating.value = false;
      editing.value = false;
      Object.assign(form, { name: "", description: "" });
    };
    const beginEdit = () => {
      if (!selected.value) return;
      Object.assign(form, {
        name: selected.value.name,
        description: selected.value.description || "",
      });
      editing.value = true;
    };
    const saveBase = async () => {
      saving.value = true;
      notice.value = "";
      try {
        const saved =
          editing.value && selectedId.value
            ? await updateKnowledgeBase(selectedId.value, {
                name: form.name.trim(),
                description: form.description.trim(),
              })
            : await createKnowledgeBase(
                form.name.trim(),
                form.description.trim()
              );
        closeModal();
        await loadBases();
        selectedId.value = saved.id;
        await loadDocuments();
        notice.value = "知识库已保存。";
      } catch (cause) {
        notice.value = describe(cause);
      } finally {
        saving.value = false;
      }
    };
    const removeBase = async () => {
      if (
        !selectedId.value ||
        !confirm("确认删除该知识库吗？请先移除其中的文档。")
      )
        return;
      try {
        await deleteKnowledgeBase(selectedId.value);
        selectedId.value = null;
        await loadBases();
        notice.value = "知识库已删除。";
      } catch (cause) {
        notice.value = describe(cause);
      }
    };
    const upload = async (event: Event) => {
      const input = event.target as HTMLInputElement,
        file = input.files?.[0];
      if (!file || !selectedId.value) return;
      uploading.value = true;
      notice.value = "";
      try {
        const stored = await uploadFile(file);
        await addKnowledgeDocument(selectedId.value, stored.id);
        await loadDocuments();
        notice.value = "文档处理完成。";
      } catch (cause) {
        notice.value = describe(cause);
      } finally {
        uploading.value = false;
        input.value = "";
      }
    };
    const retry = async (id: string) => {
      try {
        await retryKnowledgeDocument(id);
        await loadDocuments();
        notice.value = "文档已重新处理。";
      } catch (cause) {
        notice.value = describe(cause);
      }
    };
    const removeDocument = async (id: string) => {
      if (!confirm("Remove this document from the knowledge base?")) return;
      try {
        await deleteKnowledgeDocument(id);
        await loadDocuments();
        notice.value = "文档已移除，源文件仍保留在云盘中。";
      } catch (cause) {
        notice.value = describe(cause);
      }
    };
    onMounted(loadBases);
    return {
      bases,
      beginEdit,
      closeModal,
      creating,
      documents,
      editing,
      form,
      loadBases,
      loading,
      notice,
      removeBase,
      removeDocument,
      retry,
      saveBase,
      saving,
      search,
      select,
      selected,
      selectedId,
      upload,
      uploading,
    };
  },
});
</script>

<style scoped>
.knowledge-page {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  min-height: calc(100vh - 72px);
  background: #f5f7fa;
}
.library-rail {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  gap: 13px;
  padding: 22px 14px;
  color: var(--ink);
  background: var(--sidebar);
}
.library-rail > header {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 4px 8px 12px;
}
.library-rail > header > span {
  color: var(--ink);
  font-size: 1.2rem;
}
.library-rail strong,
.library-rail small {
  display: block;
}
.library-rail small {
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 0.68rem;
}
.search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border-radius: 12px;
  color: var(--ink-faint);
  background: var(--surface-soft);
}
.search input {
  width: 100%;
  min-height: 38px;
  border: 0;
  color: var(--ink);
  background: transparent;
  outline: 0;
}
.new-base {
  min-height: 42px;
  border: 1px solid var(--line);
  border-radius: 12px;
  color: var(--ink);
  background: var(--surface);
  cursor: pointer;
  font-weight: 600;
}
.new-base:hover {
  background: var(--surface-soft);
}
.knowledge-main {
  position: relative;
  width: min(980px, calc(100% - 48px));
  margin: 0 auto;
  padding: 48px 0 80px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: end;
}
.page-header h1 {
  margin: 5px 0 9px;
  color: var(--ink);
  font-size: clamp(2.2rem, 5vw, 4rem);
  font-weight: 700;
  letter-spacing: -0.02em;
}
.page-header > div > p:last-child {
  max-width: 640px;
  color: var(--ink-soft);
  line-height: 1.55;
}
.header-actions {
  display: flex;
  gap: 7px;
  padding-bottom: 8px;
}
.header-actions button {
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--ink-soft);
  background: var(--surface);
  cursor: pointer;
}
.header-actions .danger {
  color: var(--danger);
}
.ingest-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin: 34px 0 24px;
  padding: 19px;
  border: 1px solid #d9e0ea;
  border-radius: 15px;
  background: #fff;
  box-shadow: 0 16px 45px rgba(27, 39, 62, 0.06);
}
.ingest-card > div,
.section-title > div {
  display: flex;
  gap: 12px;
  align-items: center;
}
.ingest-card strong,
.ingest-card small,
.section-title strong,
.section-title small {
  display: block;
}
.ingest-card small,
.section-title small {
  margin-top: 4px;
  color: #8490a2;
  font-size: 0.72rem;
}
.step {
  color: var(--ink-faint);
  font: 700 0.68rem "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}
.upload input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}
.upload span {
  display: block;
  padding: 10px 14px;
  border-radius: 999px;
  color: #fff;
  background: var(--ink);
  cursor: pointer;
  font-weight: 600;
  font-size: 0.8rem;
}
.upload.busy span {
  opacity: 0.6;
  cursor: wait;
}
.document-section {
  padding-top: 6px;
}
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 13px;
}
.section-title > span {
  color: #8791a2;
  font-size: 0.72rem;
}
.welcome {
  display: grid;
  justify-items: center;
  max-width: 520px;
  margin: 100px auto;
  text-align: center;
}
.welcome > span {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  color: var(--ink);
  background: var(--surface-soft);
  font-size: 1.6rem;
}
.welcome h2 {
  margin: 20px 0 8px;
  font-size: 1.7rem;
}
.welcome p {
  color: var(--ink-soft);
  line-height: 1.6;
}
.welcome button {
  padding: 11px 16px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: var(--ink);
  cursor: pointer;
}
.notice {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 50;
  max-width: 420px;
  padding: 11px 14px;
  border: 1px solid #d9dff0;
  border-radius: 9px;
  color: #4f5d7a;
  background: #fff;
  box-shadow: 0 12px 35px rgba(25, 36, 59, 0.14);
  font-size: 0.76rem;
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(14, 19, 29, 0.55);
  backdrop-filter: blur(4px);
}
.modal {
  width: min(470px, 100%);
  padding: 26px;
  border-radius: 17px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.25);
}
.modal h2 {
  margin-bottom: 22px;
  font-size: 1.55rem;
}
.modal label {
  display: grid;
  gap: 6px;
  margin: 14px 0;
  color: #606b7e;
  font-size: 0.78rem;
}
.modal input,
.modal textarea {
  padding: 10px 11px;
  border: 1px solid #d5dce6;
  border-radius: 8px;
  resize: vertical;
}
.modal > div {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}
.modal button {
  padding: 9px 13px;
  border: 1px solid #d6dde7;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}
.modal .primary {
  border-color: var(--ink);
  color: #fff;
  background: var(--ink);
}
@media (max-width: 760px) {
  .knowledge-page {
    grid-template-columns: 1fr;
  }
  .library-rail {
    grid-template-rows: auto auto auto auto;
  }
  .knowledge-main {
    width: min(100% - 24px, 980px);
    padding-top: 30px;
  }
  .page-header,
  .ingest-card {
    align-items: stretch;
    flex-direction: column;
  }
  .header-actions {
    padding: 0;
  }
  .upload span {
    text-align: center;
  }
}
</style>
