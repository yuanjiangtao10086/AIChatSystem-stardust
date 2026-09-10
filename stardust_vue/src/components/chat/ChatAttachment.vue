<template>
  <div class="attachment-picker">
    <button
      class="attachment"
      type="button"
      :disabled="disabled"
      title="从云盘中选择附件"
      aria-label="选择云盘中的文件"
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
      <p v-if="error" class="state error">{{ error }}</p>
      <p v-else-if="loading" class="state">正在加载文件…</p>
      <p v-else-if="!files.length" class="state">请先在云盘页面上传文件。</p>
      <ul v-else>
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
      <footer>已选 {{ modelValue.length }}/10 个文件</footer>
    </section>
  </div>
</template>
<script lang="ts">
import { defineComponent, PropType, ref } from "vue";
import { listFiles, formatBytes } from "@/api/files";
import { FileReference, UserFile } from "@/types/file";

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
    const open = ref(false);
    const loading = ref(false);
    const loaded = ref(false);
    const error = ref("");
    const files = ref<UserFile[]>([]);
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
    const toggle = async () => {
      open.value = !open.value;
      if (open.value && !loaded.value) await load();
    };
    const isSelected = (id: string) =>
      props.modelValue.some((file) => file.id === id);
    const select = (file: UserFile) => {
      if (isSelected(file.id)) {
        emit(
          "update:modelValue",
          props.modelValue.filter((item) => item.id !== file.id)
        );
      } else if (props.modelValue.length < 10) {
        emit("update:modelValue", [...props.modelValue, file]);
      }
    };
    return {
      bytes: formatBytes,
      error,
      files,
      isSelected,
      loading,
      open,
      select,
      toggle,
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
.picker ul {
  max-height: 225px;
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
.picker footer {
  color: #9aa1af;
  text-align: right;
  font: 500 0.58rem "Cascadia Code", monospace;
}
</style>
