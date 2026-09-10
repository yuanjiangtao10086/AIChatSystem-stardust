<template>
  <div class="file-table">
    <div class="table-head">
      <span>名称</span><span>大小</span><span>上传时间</span>
    </div>
    <button
      v-for="file in files"
      :key="file.id"
      type="button"
      :class="{ active: file.id === selectedId }"
      @click="$emit('select', file)"
    >
      <span class="file-name"
        ><i>{{ icon(file) }}</i
        ><span
          ><strong>{{ file.name }}</strong
          ><small>{{ file.detectedMimeType }}</small></span
        ></span
      >
      <span>{{ bytes(file.size) }}</span
      ><time :datetime="file.createdAt">{{ date(file.createdAt) }}</time>
    </button>
    <div v-if="!files.length" class="empty">
      <span>✦</span><strong>暂无文件</strong>
      <p>上传一次文件，即可在多个对话中复用。</p>
    </div>
  </div>
</template>
<script lang="ts">
import { defineComponent, PropType } from "vue";
import { formatBytes } from "@/api/files";
import { UserFile } from "@/types/file";
export default defineComponent({
  name: "FileTable",
  props: {
    files: { type: Array as PropType<UserFile[]>, required: true },
    selectedId: String,
  },
  emits: ["select"],
  setup() {
    const icon = (file: UserFile) =>
      file.previewable
        ? "▧"
        : (
            {
              pdf: "PDF",
              txt: "TXT",
              md: "MD",
              csv: "CSV",
              json: "{}",
            } as Record<string, string>
          )[file.extension] || file.extension.slice(0, 3).toUpperCase();
    const date = (value: string) =>
      new Intl.DateTimeFormat("zh-CN", {
        month: "numeric",
        day: "numeric",
        year: "numeric",
      }).format(new Date(value));
    return { bytes: formatBytes, date, icon };
  },
});
</script>
<style scoped>
.file-table {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
}
.table-head,
button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 92px 130px;
  align-items: center;
  gap: 14px;
  width: 100%;
}
.table-head {
  min-height: 40px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
  color: var(--ink-faint);
  background: var(--canvas);
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.04em;
}
button {
  min-height: 66px;
  padding: 9px 16px;
  border: 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink-soft);
  background: var(--surface);
  text-align: left;
  font-size: 0.75rem;
  cursor: pointer;
}
button:last-of-type {
  border-bottom: 0;
}
button:hover,
button.active {
  background: var(--canvas);
}
button.active {
  box-shadow: inset 3px 0 var(--ink);
}
.file-name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
}
.file-name i {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font: 700 0.58rem "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-style: normal;
}
.file-name strong,
.file-name small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-name strong {
  color: var(--ink);
  font-size: 0.82rem;
}
.file-name small {
  margin-top: 3px;
  color: var(--ink-faint);
  font-size: 0.64rem;
}
.empty {
  display: grid;
  place-items: center;
  min-height: 280px;
  padding: 30px;
  text-align: center;
}
.empty > span {
  color: var(--ink-soft);
  font-size: 1.2rem;
}
.empty strong {
  margin-top: 10px;
  color: var(--ink);
}
.empty p {
  margin: 7px 0 0;
  color: var(--ink-faint);
  font-size: 0.78rem;
}
@media (max-width: 680px) {
  .table-head {
    display: none;
  }
  button {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
    padding: 11px 13px;
  }
  button > time {
    grid-column: 2;
    color: #9aa1b0;
    font-size: 0.64rem;
  }
}
</style>
