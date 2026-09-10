<template>
  <div class="chat-markdown">
    <template v-for="block in blocks" :key="block.key">
      <ChatCodeBlock
        v-if="block.type === 'code'"
        :code="block.content"
        :language="block.language"
      />
      <div v-else class="markdown-section" v-html="render(block.content)"></div>
    </template>
  </div>
</template>
<script lang="ts">
import { computed, defineComponent } from "vue";
import ChatCodeBlock from "@/components/chat/ChatCodeBlock.vue";
import { renderSafeMarkdown, splitMarkdownBlocks } from "@/utils/markdown";
export default defineComponent({
  name: "ChatMarkdown",
  components: { ChatCodeBlock },
  props: { content: { type: String, required: true } },
  setup(props) {
    return {
      blocks: computed(() => splitMarkdownBlocks(props.content)),
      render: renderSafeMarkdown,
    };
  },
});
</script>
<style src="katex/dist/katex.min.css"></style>
<style scoped>
.chat-markdown {
  color: #202638;
  font-size: 0.975rem;
  line-height: 1.76;
  overflow-wrap: anywhere;
}
.markdown-section :deep(:first-child) {
  margin-top: 0;
}
.markdown-section :deep(:last-child) {
  margin-bottom: 0;
}
.markdown-section :deep(h1),
.markdown-section :deep(h2),
.markdown-section :deep(h3) {
  margin: 1.5em 0 0.55em;
  color: #121827;
  font-family: "Segoe UI Variable Display", "Segoe UI", sans-serif;
  line-height: 1.25;
}
.markdown-section :deep(h1) {
  font-size: 1.55rem;
}
.markdown-section :deep(h2) {
  font-size: 1.3rem;
}
.markdown-section :deep(h3) {
  font-size: 1.1rem;
}
.markdown-section :deep(p) {
  margin: 0.7em 0;
}
.markdown-section :deep(ul),
.markdown-section :deep(ol) {
  padding-left: 1.45rem;
}
.markdown-section :deep(li) {
  margin: 0.3em 0;
}
.markdown-section :deep(blockquote) {
  margin: 1rem 0;
  padding: 0.1rem 1rem;
  border-left: 3px solid #c9c9ce;
  color: var(--ink-soft);
  background: var(--canvas);
}
.markdown-section :deep(code) {
  padding: 0.16em 0.38em;
  border-radius: 5px;
  background: var(--surface-soft);
  color: #6b2f9e;
  font: 500 0.86em "Cascadia Code", Consolas, monospace;
}
.markdown-section :deep(table) {
  display: block;
  max-width: 100%;
  margin: 1rem 0;
  border-collapse: collapse;
  overflow: auto;
}
.markdown-section :deep(th),
.markdown-section :deep(td) {
  padding: 9px 12px;
  border: 1px solid #dce1eb;
  text-align: left;
}
.markdown-section :deep(th) {
  background: #f5f6f9;
}
.markdown-section :deep(a) {
  color: #415dcc;
  text-underline-offset: 3px;
}
.markdown-section :deep(.katex-display) {
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0.5rem 0;
}
</style>
