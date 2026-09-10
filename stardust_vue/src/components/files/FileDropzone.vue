<template>
  <label
    class="dropzone"
    :class="{ dragging, disabled }"
    @dragenter.prevent="dragging = true"
    @dragover.prevent
    @dragleave.prevent="dragging = false"
    @drop.prevent="drop"
  >
    <input
      ref="input"
      type="file"
      :accept="accept"
      :disabled="disabled"
      @change="choose"
    />
    <span class="upload-mark">↑</span
    ><span
      ><strong>{{ disabled ? "正在上传…" : "上传到我的云盘" }}</strong
      ><small>拖拽文件到此处或点击选择 · 单文件最大 25 MB</small></span
    >
  </label>
</template>
<script lang="ts">
import { defineComponent, ref } from "vue";
export default defineComponent({
  name: "FileDropzone",
  props: { disabled: Boolean },
  emits: ["upload"],
  setup(_props, { emit }) {
    const dragging = ref(false);
    const input = ref<HTMLInputElement | null>(null);
    const accept =
      ".png,.jpg,.jpeg,.gif,.webp,.pdf,.txt,.md,.csv,.json,.docx,.xlsx,.pptx";
    const send = (files: FileList | null) => {
      const file = files?.item(0);
      if (file) emit("upload", file);
    };
    const choose = (event: Event) => {
      send((event.target as HTMLInputElement).files);
      if (input.value) input.value.value = "";
    };
    const drop = (event: DragEvent) => {
      dragging.value = false;
      send(event.dataTransfer?.files || null);
    };
    return { accept, choose, dragging, drop, input };
  },
});
</script>
<style scoped>
.dropzone {
  display: flex;
  align-items: center;
  gap: 13px;
  min-height: 82px;
  padding: 14px 17px;
  border: 1px dashed #c4c4c8;
  border-radius: 16px;
  color: var(--ink);
  background: var(--canvas);
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease;
}
.dropzone:hover,
.dropzone.dragging {
  border-color: var(--ink);
  background: var(--surface-soft);
}
.dropzone.disabled {
  cursor: wait;
  opacity: 0.65;
}
input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}
.upload-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  color: var(--ink);
  background: var(--surface-soft);
  font-size: 1.25rem;
  font-weight: 700;
}
strong,
small {
  display: block;
}
strong {
  font-size: 0.86rem;
}
small {
  margin-top: 4px;
  color: var(--ink-faint);
  font-size: 0.68rem;
}
</style>
