<template>
  <div class="artifact-card" :class="`is-${artifact.status}`">
    <span class="artifact-icon" aria-hidden="true">{{ icon }}</span>
    <div class="artifact-meta">
      <div class="artifact-name" :title="artifact.filename">
        {{ artifact.filename || "未命名文件" }}
      </div>
      <div class="artifact-sub">
        <template v-if="artifact.status === 'generating'">
          <span class="spinner" aria-hidden="true" /> 生成中…
        </template>
        <template v-else-if="artifact.status === 'error'">
          <span class="artifact-error-text">{{
            artifact.error?.message || "生成失败"
          }}</span>
        </template>
        <template v-else>
          <span>{{ formattedSize }}</span>
          <span class="artifact-ext">{{ extLabel }}</span>
        </template>
      </div>
    </div>
    <button
      v-if="artifact.status === 'ready' && artifact.downloadUrl"
      type="button"
      class="artifact-download"
      @click="onDownload"
    >
      下载
    </button>
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType, computed } from "vue";
import type { ChatArtifact } from "@/types/conversation";
import type { FileReference } from "@/types/file";
import { downloadFile } from "@/api/files";

const ICONS: Record<string, string> = {
  docx: "📝",
  pptx: "📊",
  xlsx: "📈",
  py: "🐍",
  html: "🌐",
  csv: "📊",
  json: "🔧",
  md: "📃",
  txt: "📃",
  sql: "🗄️",
  yaml: "⚙️",
  xml: "⚙️",
};

export default defineComponent({
  name: "ChatArtifactCard",
  props: {
    artifact: { type: Object as PropType<ChatArtifact>, required: true },
  },
  setup(props) {
    const icon = computed(
      () => ICONS[props.artifact.artifactType ?? ""] || "📄"
    );
    const formattedSize = computed(() => {
      const size = props.artifact.size || 0;
      if (size < 1024) return `${size} B`;
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
      return `${(size / 1024 / 1024).toFixed(1)} MB`;
    });
    const extLabel = computed(() => {
      const name = props.artifact.filename || "";
      const dot = name.lastIndexOf(".");
      if (dot >= 0) return name.slice(dot + 1).toUpperCase();
      return (props.artifact.artifactType || "").toUpperCase();
    });
    const onDownload = () => {
      if (!props.artifact.downloadUrl) return;
      const file: FileReference = {
        id: props.artifact.fileId || "",
        name: props.artifact.filename,
        downloadUrl: props.artifact.downloadUrl,
        size: props.artifact.size,
        mimeType: props.artifact.mimeType,
        previewable: false,
      };
      downloadFile(file);
    };
    return { icon, formattedSize, extLabel, onDownload };
  },
});
</script>

<style scoped>
.artifact-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-soft);
}
.artifact-icon {
  font-size: 20px;
}
.artifact-meta {
  min-width: 0;
  flex: 1;
}
.artifact-name {
  font-size: 0.86rem;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.artifact-sub {
  margin-top: 2px;
  font-size: 0.74rem;
  color: var(--ink-soft);
  display: flex;
  gap: 8px;
  align-items: center;
}
.artifact-error-text {
  color: #b3271d;
}
.artifact-ext {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--canvas);
  font-size: 0.66rem;
  font-weight: 600;
}
.artifact-download {
  padding: 6px 14px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: var(--accent);
  font-size: 0.78rem;
  cursor: pointer;
}
.artifact-download:hover {
  filter: brightness(1.05);
}
.spinner {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 4px;
  border: 2px solid var(--ink-faint);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.is-generating {
  opacity: 0.85;
}
.is-error {
  border-color: #b3271d;
}
</style>
