<template>
  <section class="memory-page">
    <header>
      <p class="eyebrow">个人上下文</p>
      <h1>长期记忆</h1>
      <p>查看并管理会影响未来对话个性化的偏好、项目和长期目标。</p>
    </header>
    <div class="toolbar">
      <input
        v-model="search"
        type="search"
        placeholder="搜索记忆"
        @keyup.enter="load(0)"
      /><select v-model="filter">
        <option value="">全部</option>
        <option value="enabled">已启用</option>
        <option value="disabled">已禁用</option></select
      ><button @click="load(0)">搜索</button
      ><button class="primary" @click="beginCreate">添加记忆</button>
      <div v-if="selectedIds.length" class="batch-inline">
        <span>已选 {{ selectedIds.length }} 条</span>
        <button type="button" :disabled="busy" @click="clearSelection">
          取消选择
        </button>
        <button
          type="button"
          class="danger"
          :disabled="busy"
          @click="batchRemove"
        >
          {{ busy ? "删除中…" : "批量删除" }}
        </button>
      </div>
    </div>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div class="grid">
      <main class="cards">
        <article
          v-for="memory in memories"
          :key="memory.id"
          :class="{ disabled: !memory.enabled }"
          @click="edit(memory)"
        >
          <label class="card-check" @click.stop>
            <input
              class="ui-checkbox"
              type="checkbox"
              :checked="selectedIds.includes(memory.id)"
              aria-label="选择记忆"
              @change="toggleMemory(memory.id)"
            />
          </label>
          <div>
            <span class="type">{{ labels[memory.memoryType] }}</span
            ><span class="origin">{{
              memory.origin === "AUTO" ? "对话自动提取" : "手动添加"
            }}</span>
          </div>
          <h2>{{ memory.summary }}</h2>
          <p>{{ memory.content }}</p>
          <footer>
            <span>重要性 {{ memory.importance }}</span
            ><button @click.stop="toggle(memory)">
              {{ memory.enabled ? "禁用" : "启用" }}
            </button>
          </footer>
        </article>
        <div v-if="!memories.length" class="empty">
          没有匹配当前筛选条件的记忆。
        </div>
      </main>
      <aside class="editor">
        <h2>{{ editingId ? "编辑记忆" : "添加记忆" }}</h2>
        <label>摘要<input v-model="form.summary" maxlength="300" /></label>
        <label
          >详情<textarea
            v-model="form.content"
            maxlength="2000"
            rows="7"
          ></textarea>
        </label>
        <div class="row">
          <label
            >类型<select v-model="form.memoryType">
              <option v-for="(_, key) in labels" :key="key" :value="key">
                {{ labels[key] }}
              </option>
            </select></label
          ><label
            >重要性<input
              v-model.number="form.importance"
              type="number"
              min="1"
              max="100"
          /></label>
        </div>
        <div class="actions">
          <button class="primary" :disabled="saving" @click="save">保存</button
          ><button @click="beginCreate">清空</button
          ><button v-if="editingId" class="danger" @click="remove">删除</button>
        </div>
        <p class="hint">
          自动记忆较保守，密码、Token、身份信息和支付信息不会被自动保存。
        </p>
      </aside>
    </div>
  </section>
</template>
<script lang="ts">
import { defineComponent, onMounted, reactive, ref } from "vue";
import { ApiError } from "@/api/client";
import {
  createMemory,
  deleteMemory,
  deleteMemoriesBatch,
  listMemories,
  setMemoryEnabled,
  updateMemory,
} from "@/api/memories";
import { MemoryType, UserMemory } from "@/types/memory";
const labels: Record<MemoryType, string> = {
  PREFERENCE: "偏好",
  PROJECT: "项目",
  GOAL: "长期目标",
  EXPLICIT: "显式记忆",
};
export default defineComponent({
  name: "MemoriesView",
  setup() {
    const memories = ref<UserMemory[]>([]),
      search = ref(""),
      filter = ref(""),
      notice = ref(""),
      editingId = ref<string | null>(null),
      saving = ref(false);
    const selectedIds = ref<string[]>([]);
    const busy = ref(false);
    const toggleMemory = (id: string) => {
      const index = selectedIds.value.indexOf(id);
      if (index >= 0) selectedIds.value.splice(index, 1);
      else selectedIds.value.push(id);
    };
    const clearSelection = () => {
      selectedIds.value = [];
    };
    const form = reactive({
      summary: "",
      content: "",
      memoryType: "PREFERENCE" as MemoryType,
      importance: 70,
    });
    const describe = (e: unknown) =>
      e instanceof ApiError
        ? `${e.message}${e.requestId ? ` · ${e.requestId}` : ""}`
        : "记忆操作失败。";
    const load = async (page = 0) => {
      try {
        memories.value = (
          await listMemories({
            page,
            search: search.value,
            enabled: filter.value ? filter.value === "enabled" : undefined,
          })
        ).items;
      } catch (e) {
        notice.value = describe(e);
      }
    };
    const beginCreate = () => {
      editingId.value = null;
      Object.assign(form, {
        summary: "",
        content: "",
        memoryType: "PREFERENCE",
        importance: 70,
      });
    };
    const edit = (m: UserMemory) => {
      editingId.value = m.id;
      Object.assign(form, {
        summary: m.summary,
        content: m.content,
        memoryType: m.memoryType,
        importance: m.importance,
      });
    };
    const save = async () => {
      if (!form.summary.trim() || !form.content.trim()) {
        notice.value = "摘要与详情不能为空。";
        return;
      }
      saving.value = true;
      try {
        const payload = {
          ...form,
          summary: form.summary.trim(),
          content: form.content.trim(),
        };
        editingId.value
          ? await updateMemory(editingId.value, payload)
          : await createMemory(payload);
        beginCreate();
        await load();
        notice.value = "记忆已保存。";
      } catch (e) {
        notice.value = describe(e);
      } finally {
        saving.value = false;
      }
    };
    const toggle = async (m: UserMemory) => {
      try {
        await setMemoryEnabled(m.id, !m.enabled);
        await load();
        notice.value = m.enabled ? "记忆已禁用。" : "记忆已启用。";
      } catch (e) {
        notice.value = describe(e);
      }
    };
    const remove = async () => {
      if (!editingId.value || !confirm("确认删除这条记忆吗？")) return;
      try {
        await deleteMemory(editingId.value);
        beginCreate();
        await load();
        notice.value = "记忆已删除。";
      } catch (e) {
        notice.value = describe(e);
      }
    };
    const batchRemove = async () => {
      if (!selectedIds.value.length) return;
      if (
        !confirm(
          `确认批量删除选中的 ${selectedIds.value.length} 条记忆吗？此操作不可恢复。`
        )
      )
        return;
      busy.value = true;
      notice.value = "";
      try {
        const result = await deleteMemoriesBatch([...selectedIds.value]);
        selectedIds.value = [];
        await load();
        notice.value =
          `已删除 ${result.deleted} 条记忆` +
          (result.failures.length
            ? `，${result.failures.length} 条删除失败。`
            : "。");
      } catch (e) {
        notice.value = describe(e);
      } finally {
        busy.value = false;
      }
    };
    onMounted(() => load());
    return {
      beginCreate,
      edit,
      editingId,
      filter,
      form,
      labels,
      load,
      memories,
      notice,
      remove,
      save,
      saving,
      search,
      toggle,
      selectedIds,
      busy,
      toggleMemory,
      clearSelection,
      batchRemove,
    };
  },
});
</script>
<style scoped>
.memory-page {
  width: min(1160px, 92vw);
  margin: auto;
  padding: 64px 0 80px;
}
.eyebrow {
  margin: 0;
  color: var(--ink-faint);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}
header h1 {
  margin: 8px 0;
  color: var(--ink);
  font-size: clamp(2rem, 4vw, 3.4rem);
  letter-spacing: -0.02em;
}
header > p:last-child {
  max-width: 650px;
  color: var(--ink-soft);
  line-height: 1.65;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 32px 0 16px;
}
.toolbar input {
  flex: 1;
}
.toolbar input,
.toolbar select,
.toolbar button,
.editor input,
.editor textarea,
.editor select,
.editor button,
.cards button {
  padding: 10px 12px;
  border: 1px solid #d7dce7;
  border-radius: 9px;
  background: #fff;
}
.toolbar button,
.editor button,
.cards button {
  cursor: pointer;
}
.primary {
  color: #fff !important;
  border-color: var(--ink) !important;
  background: var(--ink) !important;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 18px;
  align-items: start;
}
.cards {
  display: grid;
  gap: 12px;
}
.cards article,
.editor,
.empty {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: 0 8px 24px rgba(13, 13, 13, 0.04);
}
.cards article {
  position: relative;
  cursor: pointer;
}
.card-check {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--surface-soft);
  box-shadow: 0 1px 3px rgba(13, 13, 13, 0.08);
}
.cards article.disabled {
  opacity: 0.56;
}
.type {
  padding: 4px 9px;
  border-radius: 99px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font-size: 0.72rem;
}
.origin {
  margin-left: 8px;
  color: var(--ink-faint);
  font-size: 0.72rem;
}
.cards h2 {
  margin: 13px 0 7px;
  font-size: 1.05rem;
}
.cards p {
  margin: 0;
  color: var(--ink-soft);
  line-height: 1.55;
}
.cards footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  color: var(--ink-faint);
  font-size: 0.75rem;
}
.editor {
  position: sticky;
  top: 90px;
}
.editor h2 {
  margin-top: 0;
}
.editor label {
  display: grid;
  gap: 6px;
  margin: 13px 0;
  color: #5f6a7d;
  font-size: 0.78rem;
}
.editor textarea {
  resize: vertical;
}
.row {
  display: grid;
  grid-template-columns: 1fr 110px;
  gap: 10px;
}
.actions {
  display: flex;
  gap: 8px;
}
.danger {
  margin-left: auto;
  color: var(--danger);
}
.hint,
.notice {
  color: var(--ink-faint);
  font-size: 0.76rem;
  line-height: 1.5;
}
@media (max-width: 760px) {
  .memory-page {
    width: min(100% - 24px, 1160px);
    padding-top: 32px;
  }
  .toolbar {
    flex-wrap: wrap;
  }
  .toolbar input {
    flex-basis: 100%;
  }
  .grid {
    grid-template-columns: 1fr;
  }
  .editor {
    position: static;
    grid-row: 1;
  }
}
</style>
