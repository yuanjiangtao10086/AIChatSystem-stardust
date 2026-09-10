<template>
  <div class="attachments" aria-label="消息附件">
    <button
      v-for="file in files"
      :key="file.id"
      type="button"
      @click="download(file)"
    >
      <span class="file-mark">{{ mark(file.name) }}</span>
      <span class="file-copy"
        ><strong>{{ file.name }}</strong
        ><small>{{ bytes(file.size) }} · 点击下载</small></span
      >
    </button>
  </div>
</template>
<script lang="ts">
import { defineComponent, PropType } from "vue";
import { downloadFile, formatBytes } from "@/api/files";
import { FileReference } from "@/types/file";

export default defineComponent({
  name: "MessageAttachments",
  props: {
    files: { type: Array as PropType<FileReference[]>, required: true },
  },
  setup() {
    const mark = (name: string) =>
      name.split(".").pop()?.slice(0, 4).toUpperCase() || "文件";
    return { bytes: formatBytes, download: downloadFile, mark };
  },
});
</script>
<style scoped>
.attachments {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 260px));
  gap: 8px;
  margin: 12px 0 3px;
}
.attachments button {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--ink);
  background: var(--surface);
  text-align: left;
  cursor: pointer;
}
.attachments button:hover {
  border-color: #c8c8cc;
  background: var(--canvas);
}
.file-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 34px;
  border-radius: 8px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font: 700 0.55rem "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}
.file-copy {
  min-width: 0;
}
.file-copy strong,
.file-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-copy strong {
  font-size: 0.68rem;
}
.file-copy small {
  margin-top: 3px;
  color: #969dac;
  font: 500 0.56rem "Cascadia Code", monospace;
}
</style>
