<template>
  <div ref="root" class="attachment-picker">
    <button
      class="attachment"
      type="button"
      :disabled="disabled"
      title="从云盘选择或上传附件"
      aria-label="选择聊天附件"
      :aria-expanded="open"
      @click="toggle"
    >
      <span aria-hidden="true">＋</span>
    </button>
    <section v-if="open" class="picker" aria-label="选择附件">
      <header>
        <span
          ><strong>来自我的云盘</strong><small>一次上传，处处可用</small></span
        >
        <router-link to="/files">管理云盘</router-link>
      </header>
      <label
        class="upload-zone"
        :class="{ dragging }"
        @dragenter.prevent="dragging = true"
        @dragover.prevent
        @dragleave.prevent="dragging = false"
        @drop.prevent="drop"
      >
        <input
          ref="pickerInput"
          type="file"
          multiple
          :accept="accept"
          :disabled="disabled"
          @change="choose"
        />
        <span class="upload-mark" aria-hidden="true">↑</span>
        <span class="upload-text">
          <strong>上传文件</strong>
          <small>从本地上传并自动保存至云盘 · 单文件最大 25 MB</small>
        </span>
      </label>
      <p v-if="error" class="state error">{{ error }}</p>
      <p v-if="limitNotice" class="state">{{ limitNotice }}</p>
      <p v-if="uploading.length" class="state uploading">
        <span v-for="item in uploading" :key="item.key">
          {{ item.name }} 上传中…
        </span>
      </p>
      <p v-if="loading && !files.length" class="state">正在加载文件…</p>
      <ul v-else-if="files.length">
        <li v-for="file in files" :key="file.id">
          <button
            type="button"
            :class="{ selected: isSelected(file.id) }"
            @click="select(file)"
          >
            <span class="kind">{{ file.extension.toUpperCase() }}</span>
            <span class="identity"
              ><strong>{{ file.name }}</strong
              ><small>{{ bytes(file.size) }}</small></span
            >
            <span class="check">{{ isSelected(file.id) ? "✓" : "+" }}</span>
          </button>
        </li>
      </ul>
      <p v-else-if="!loading" class="state">
        云盘还没有文件，可在上方直接上传。
      </p>
      <footer>已选 {{ modelValue.length }}/10 个文件</footer>
    </section>
  </div>
</template>
<script lang="ts">
import { defineComponent, onUnmounted, PropType, ref, watch } from "vue";
import { formatBytes, listFiles, uploadFile } from "@/api/files";
import { FileReference, UserFile } from "@/types/file";

const MAX_ATTACHMENTS = 10;
/** 与云盘 FileDropzone 保持同一份类型限制；后端仍会做最终校验。 */
const ACCEPT =
  ".png,.jpg,.jpeg,.gif,.webp,.pdf,.txt,.md,.csv,.json,.docx,.xlsx,.pptx";

export default defineComponent({
  name: "ChatAttachment",
  props: {
    modelValue: {
      type: Array as PropType<FileReference[]>,
      default: () => [],
    },
    disabled: Boolean,
  },
  emits: ["update:modelValue"],
  setup(props, { emit }) {
    const root = ref<HTMLElement | null>(null);
    const open = ref(false);
    const loading = ref(false);
    const loaded = ref(false);
    const error = ref("");
    const limitNotice = ref("");
    const files = ref<UserFile[]>([]);
    const pickerInput = ref<HTMLInputElement | null>(null);
    const dragging = ref(false);
    const uploading = ref<{ key: string; name: string }[]>([]);

    const load = async () => {
      loading.value = true;
      error.value = "";
      try {
        files.value = (await listFiles({ page: 0, size: 50 })).items;
        loaded.value = true;
      } catch (value) {
        error.value =
          value instanceof Error ? value.message : "文件列表加载失败。";
      } finally {
        loading.value = false;
      }
    };
    const close = () => {
      open.value = false;
    };
    const toggle = async () => {
      open.value = !open.value;
      if (open.value && !loaded.value) await load();
    };
    // Popover 交互：仅当按下位置在面板（含"+"按钮）之外时关闭。
    // 面板内部的任何点击（上传、选择文件、管理云盘）都不会触发关闭。
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (
        root.value &&
        target instanceof Node &&
        !root.value.contains(target)
      ) {
        close();
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    // 监听器只随 open 状态挂载/卸载一次，组件销毁时兜底移除，避免泄漏。
    watch(open, (value) => {
      if (value) {
        document.addEventListener("pointerdown", handlePointerDown);
        document.addEventListener("keydown", handleKeyDown);
      } else {
        document.removeEventListener("pointerdown", handlePointerDown);
        document.removeEventListener("keydown", handleKeyDown);
      }
    });
    onUnmounted(() => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    });

    const isSelected = (id: string) =>
      props.modelValue.some((file) => file.id === id);
    const select = (file: UserFile) => {
      limitNotice.value = "";
      if (isSelected(file.id)) {
        emit(
          "update:modelValue",
          props.modelValue.filter((item) => item.id !== file.id)
        );
      } else if (props.modelValue.length < MAX_ATTACHMENTS) {
        emit("update:modelValue", [...props.modelValue, file]);
      } else {
        limitNotice.value = `最多选择 ${MAX_ATTACHMENTS} 个文件。`;
      }
    };
    /**
     * 上传本地文件：走云盘同一上传接口（POST /api/v1/files），文件会真正
     * 落库到"我的云盘"；成功后立即插入面板列表并自动加入当前消息附件。
     */
    const uploadAll = async (list: FileList | null) => {
      const incoming = Array.from(list || []);
      if (pickerInput.value) pickerInput.value.value = "";
      if (!incoming.length || props.disabled) return;
      // 用本地数组累积本轮选择：同一批次连续 emit 时父组件的 props 可能
      // 还没刷新，读 props 会丢掉前一次的选择；上限判断也以本地为准。
      let selected = [...props.modelValue];
      for (const file of incoming) {
        const key = `${file.name}:${file.size}:${file.lastModified}`;
        // 防止同一文件（同名同大小同修改时间）被重复触发上传。
        if (uploading.value.some((item) => item.key === key)) continue;
        const existing = files.value.find(
          (item) => item.name === file.name && item.size === file.size
        );
        if (existing) {
          // 云盘已有同名同大小文件：不重复上传，直接引用已有记录。
          if (
            !selected.some((item) => item.id === existing.id) &&
            selected.length < MAX_ATTACHMENTS
          ) {
            selected = [...selected, existing];
            emit("update:modelValue", selected);
          }
          continue;
        }
        uploading.value.push({ key, name: file.name });
        try {
          const created = await uploadFile(file);
          loaded.value = true;
          if (!files.value.some((item) => item.id === created.id)) {
            files.value.unshift(created);
          }
          if (
            !selected.some((item) => item.id === created.id) &&
            selected.length < MAX_ATTACHMENTS
          ) {
            selected = [...selected, created];
            emit("update:modelValue", selected);
          }
        } catch (value) {
          error.value = `${file.name} 上传失败，请重试。`;
        } finally {
          uploading.value = uploading.value.filter((item) => item.key !== key);
        }
      }
      if (selected.length >= MAX_ATTACHMENTS) {
        limitNotice.value = `最多选择 ${MAX_ATTACHMENTS} 个文件。`;
      }
    };
    const choose = (event: Event) => {
      void uploadAll((event.target as HTMLInputElement).files);
    };
    const drop = (event: DragEvent) => {
      dragging.value = false;
      void uploadAll(event.dataTransfer?.files || null);
    };
    return {
      accept: ACCEPT,
      bytes: formatBytes,
      choose,
      dragging,
      drop,
      error,
      files,
      isSelected,
      limitNotice,
      loading,
      open,
      pickerInput,
      root,
      select,
      toggle,
      uploading,
    };
  },
});
</script>
<style scoped>
.attachment-picker {
  position: relative;
}
.attachment {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  color: var(--ink-soft);
  background: transparent;
  font-size: 1.35rem;
  cursor: pointer;
}
.attachment:hover {
  background: var(--surface-soft);
}
.attachment:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.picker {
  position: absolute;
  bottom: 43px;
  left: 0;
  z-index: 12;
  width: min(330px, calc(100vw - 36px));
  max-height: 340px;
  padding: 12px;
  border: 1px solid #d8dde8;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(31, 40, 65, 0.18);
}
.picker header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  padding: 2px 3px 10px;
  border-bottom: 1px solid #eceef3;
}
.picker header span,
.identity {
  min-width: 0;
}
.picker header strong,
.picker header small,
.identity strong,
.identity small {
  display: block;
}
.picker header strong {
  color: #30384b;
  font-size: 0.75rem;
}
.picker header small {
  margin-top: 2px;
  color: #9299a9;
  font-size: 0.62rem;
}
.picker a {
  color: #536bd6;
  font-size: 0.68rem;
  text-decoration: none;
}
/* 面板内上传区：点击或拖拽，复用云盘 accept 规则与上传接口 */
.upload-zone {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 10px 11px;
  border: 1px dashed #c4c4c8;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.upload-zone:hover,
.upload-zone.dragging {
  border-color: var(--ink);
  background: var(--surface-soft);
}
.upload-zone input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}
.upload-mark {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: 9px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font-size: 1rem;
  font-weight: 700;
}
.upload-text {
  min-width: 0;
}
.upload-text strong {
  display: block;
  color: #30384b;
  font-size: 0.72rem;
}
.upload-text small {
  display: block;
  margin-top: 2px;
  color: #9299a9;
  font-size: 0.62rem;
}
.picker ul {
  max-height: 190px;
  margin: 8px 0;
  padding: 0;
  overflow: auto;
  list-style: none;
}
.picker li button {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 22px;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px;
  border: 0;
  border-radius: 8px;
  color: #3d465b;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.picker li button:hover,
.picker li button.selected {
  background: #f1f3fa;
}
.kind {
  display: grid;
  place-items: center;
  width: 34px;
  height: 32px;
  border-radius: 7px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font: 700 0.55rem "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}
.identity strong {
  overflow: hidden;
  font-size: 0.7rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.identity small {
  margin-top: 3px;
  color: #969dac;
  font: 500 0.58rem "Cascadia Code", monospace;
}
.check {
  color: var(--ink);
  text-align: center;
  font-weight: 700;
}
.state {
  margin: 17px 5px;
  color: #8c94a5;
  font-size: 0.68rem;
}
.state.error {
  color: #9b4946;
}
.state.uploading span {
  display: block;
}
.picker footer {
  color: #9aa1af;
  text-align: right;
  font: 500 0.58rem "Cascadia Code", monospace;
}
</style>
