<template>
  <aside class="detail-panel">
    <template v-if="file">
      <div class="preview" :class="{ image: previewUrl }">
        <img v-if="previewUrl" :src="previewUrl" :alt="file.name" /><span
          v-else
          >{{ file.extension.toUpperCase() }}</span
        >
      </div>
      <form v-if="renaming" @submit.prevent="save">
        <input v-model.trim="name" maxlength="255" aria-label="File name" />
        <div>
          <button type="button" @click="renaming = false">取消</button
          ><button class="primary" type="submit">保存</button>
        </div>
      </form>
      <template v-else
        ><h2>{{ file.name }}</h2>
        <p>{{ file.detectedMimeType }}</p></template
      >
      <dl>
        <div>
          <dt>大小</dt>
          <dd>{{ bytes(file.size) }}</dd>
        </div>
        <div>
          <dt>上传时间</dt>
          <dd>{{ date(file.createdAt) }}</dd>
        </div>
        <div>
          <dt>校验值</dt>
          <dd :title="file.sha256">{{ file.sha256.slice(0, 12) }}…</dd>
        </div>
      </dl>
      <div class="actions">
        <button type="button" @click="$emit('download', file)">下载</button
        ><button type="button" @click="startRename">重命名</button
        ><button class="danger" type="button" @click="$emit('delete', file)">
          删除
        </button>
      </div>
    </template>
    <div v-else class="no-selection">
      <span>↗</span>
      <p>选择一个文件查看详情。</p>
    </div>
  </aside>
</template>
<script lang="ts">
import { defineComponent, onBeforeUnmount, PropType, ref, watch } from "vue";
import { fetchFileBlob, formatBytes } from "@/api/files";
import { UserFile } from "@/types/file";
export default defineComponent({
  name: "FileDetailPanel",
  props: { file: Object as PropType<UserFile | null> },
  emits: ["download", "rename", "delete"],
  setup(props, { emit }) {
    const renaming = ref(false);
    const name = ref("");
    const previewUrl = ref("");
    let previewGeneration = 0;
    const release = () => {
      if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
      previewUrl.value = "";
    };
    watch(
      () => props.file,
      async (file) => {
        const generation = ++previewGeneration;
        release();
        renaming.value = false;
        name.value = file?.name || "";
        if (file?.previewable) {
          try {
            const url = URL.createObjectURL(await fetchFileBlob(file, true));
            if (generation === previewGeneration) previewUrl.value = url;
            else URL.revokeObjectURL(url);
          } catch {
            if (generation === previewGeneration) previewUrl.value = "";
          }
        }
      },
      { immediate: true }
    );
    onBeforeUnmount(() => {
      previewGeneration++;
      release();
    });
    const startRename = () => {
      name.value = props.file?.name || "";
      renaming.value = true;
    };
    const save = () => {
      if (props.file && name.value) {
        emit("rename", props.file, name.value);
        renaming.value = false;
      }
    };
    const date = (value: string) =>
      new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value));
    return {
      bytes: formatBytes,
      date,
      name,
      previewUrl,
      renaming,
      save,
      startRename,
    };
  },
});
</script>
<style scoped>
.detail-panel {
  min-height: 360px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
}
.preview {
  display: grid;
  place-items: center;
  aspect-ratio: 16/10;
  overflow: hidden;
  border-radius: 11px;
  color: var(--ink-soft);
  background: var(--canvas);
  font: 700 0.85rem "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}
.preview.image {
  background: var(--surface-soft);
}
.preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
h2 {
  overflow-wrap: anywhere;
  margin: 17px 0 4px;
  color: var(--ink);
  font-size: 1.02rem;
  letter-spacing: -0.01em;
}
p {
  margin: 0;
  color: var(--ink-faint);
  font-size: 0.68rem;
}
dl {
  margin: 18px 0;
  border-top: 1px solid var(--line);
}
dl div {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 0.7rem;
}
dt {
  color: var(--ink-faint);
}
dd {
  overflow: hidden;
  margin: 0;
  color: var(--ink-soft);
  font-family: "Cascadia Code", Consolas, monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.actions,
form > div {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
button {
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--ink-soft);
  background: var(--surface);
  font-size: 0.7rem;
  cursor: pointer;
}
button:hover {
  background: var(--surface-soft);
}
button.primary {
  border-color: var(--ink);
  color: #fff;
  background: var(--ink);
}
button.danger {
  margin-left: auto;
  color: var(--danger);
}
form {
  margin-top: 15px;
}
input {
  width: 100%;
  margin-bottom: 9px;
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--ink);
}
.no-selection {
  display: grid;
  place-items: center;
  min-height: 320px;
  align-content: center;
  color: var(--ink-faint);
  text-align: center;
}
.no-selection span {
  font-size: 1.3rem;
}
.no-selection p {
  margin-top: 9px;
}
@media (max-width: 600px) {
  .no-selection {
    min-height: 150px;
  }
}
</style>
