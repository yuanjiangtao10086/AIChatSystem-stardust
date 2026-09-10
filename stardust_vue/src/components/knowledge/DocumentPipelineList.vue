<template>
  <div class="documents">
    <article v-for="document in items" :key="document.id">
      <div class="document-icon">▤</div>
      <div class="document-copy">
        <strong>{{ document.fileName }}</strong>
        <div
          class="pipeline"
          :aria-label="`Document status ${document.status}`"
        >
          <span
            v-for="stage in stages"
            :key="stage"
            :class="stageClass(document.status, stage)"
          >
            <i></i>{{ stageLabel[stage] }}
          </span>
        </div>
        <small v-if="document.status === 'READY'"
          >{{ document.chunkCount }} 个分块 · 已入库</small
        >
        <small v-else-if="document.status === 'FAILED'" class="failed">{{
          document.errorCode || "处理失败"
        }}</small>
        <small v-else>处理版本 {{ document.processingVersion }}</small>
      </div>
      <div class="document-actions">
        <button
          v-if="document.status === 'FAILED'"
          type="button"
          @click="$emit('retry', document.id)"
        >
          重试
        </button>
        <button
          class="delete"
          type="button"
          @click="$emit('delete', document.id)"
        >
          删除
        </button>
      </div>
    </article>
    <p v-if="!items.length" class="empty">
      请先上传 TXT、Markdown、CSV 或 JSON 文档。
    </p>
  </div>
</template>
<script lang="ts">
import { defineComponent, PropType } from "vue";
import { KnowledgeDocument, KnowledgeDocumentStatus } from "@/types/knowledge";
const stages = ["UPLOADED", "PARSING", "EMBEDDING", "READY"] as const;
type Stage = (typeof stages)[number];
const stageLabel: Record<Stage, string> = {
  UPLOADED: "上传",
  PARSING: "解析",
  EMBEDDING: "向量化",
  READY: "入库",
};
const rank: Record<KnowledgeDocumentStatus, number> = {
  UPLOADED: 0,
  PARSING: 1,
  PARSED: 2,
  EMBEDDING: 2,
  READY: 3,
  FAILED: -1,
};
export default defineComponent({
  name: "DocumentPipelineList",
  props: {
    items: { type: Array as PropType<KnowledgeDocument[]>, required: true },
  },
  emits: ["retry", "delete"],
  setup() {
    const stageClass = (status: KnowledgeDocumentStatus, stage: Stage) => ({
      complete: status !== "FAILED" && rank[status] >= stages.indexOf(stage),
      failed: status === "FAILED" && stage === "READY",
    });
    return { stages, stageLabel, stageClass };
  },
});
</script>
<style scoped>
.documents {
  display: grid;
  gap: 10px;
}
article {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
  padding: 17px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
}
.document-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 48px;
  border-radius: 8px 8px 13px 8px;
  color: var(--ink-soft);
  background: var(--surface-soft);
  font-size: 1.15rem;
}
.document-copy {
  min-width: 0;
}
.document-copy strong {
  display: block;
  overflow: hidden;
  color: var(--ink);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.88rem;
}
.pipeline {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 12px 0 7px;
}
.pipeline span {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 4px;
  color: var(--ink-faint);
  font-size: 0.62rem;
}
.pipeline span::after {
  content: "";
  flex: 1;
  height: 1px;
  background: var(--line);
}
.pipeline span:last-child {
  flex: 0 0 auto;
}
.pipeline span:last-child::after {
  display: none;
}
.pipeline i {
  width: 7px;
  height: 7px;
  border: 1px solid #b9b9c0;
  border-radius: 50%;
}
.pipeline .complete {
  color: var(--ink);
}
.pipeline .complete i {
  border-color: var(--ink);
  background: var(--ink);
  box-shadow: 0 0 0 3px var(--surface-soft);
}
.pipeline .failed {
  color: var(--danger);
}
.pipeline .failed i {
  border-color: var(--danger);
  background: var(--danger);
}
small {
  color: var(--ink-faint);
  font-size: 0.67rem;
}
small.failed {
  color: var(--danger);
}
.document-actions {
  display: flex;
  gap: 6px;
}
.document-actions button {
  padding: 7px 9px;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--ink-soft);
  background: var(--surface);
  cursor: pointer;
  font-size: 0.7rem;
}
.document-actions .delete {
  color: var(--danger);
}
.empty {
  padding: 50px 18px;
  border: 1px dashed #c9c9ce;
  border-radius: 14px;
  color: var(--ink-faint);
  text-align: center;
}
@media (max-width: 650px) {
  article {
    grid-template-columns: 36px minmax(0, 1fr);
  }
  .document-actions {
    grid-column: 2;
  }
  .pipeline span {
    font-size: 0;
  }
}
</style>
