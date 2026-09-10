<template>
  <section class="code-block">
    <header>
      <span>{{ language || "text" }}</span
      ><button type="button" @click="copy">
        {{ copied ? "已复制" : "复制代码" }}
      </button>
    </header>
    <pre><code :class="`language-${language}`" v-html="highlighted"></code></pre>
  </section>
</template>
<script lang="ts">
import { computed, defineComponent, ref } from "vue";
import { highlightCode } from "@/utils/markdown";
export default defineComponent({
  name: "ChatCodeBlock",
  props: {
    code: { type: String, required: true },
    language: { type: String, default: "text" },
  },
  setup(props) {
    const copied = ref(false);
    const highlighted = computed(() =>
      highlightCode(props.code, props.language)
    );
    const copy = async () => {
      await navigator.clipboard.writeText(props.code);
      copied.value = true;
      window.setTimeout(() => (copied.value = false), 1600);
    };
    return { copied, highlighted, copy };
  },
});
</script>
<style scoped>
.code-block {
  margin: 1.15rem 0;
  border: 1px solid #273149;
  border-radius: 12px;
  overflow: hidden;
  background: #111726;
  color: #dbe4ff;
}
.code-block header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #192033;
  color: #9eaccb;
  font: 600 0.72rem "Cascadia Code", monospace;
  text-transform: lowercase;
}
.code-block button {
  border: 0;
  color: #cbd6f4;
  background: transparent;
  cursor: pointer;
  font: inherit;
}
.code-block button:hover {
  color: #fff;
}
.code-block pre {
  margin: 0;
  padding: 17px 18px;
  overflow: auto;
}
.code-block code {
  font: 400 0.84rem/1.7 "Cascadia Code", Consolas, monospace;
  white-space: pre;
}
.code-block :deep(.hljs-keyword),
.code-block :deep(.hljs-selector-tag) {
  color: #c59cff;
}
.code-block :deep(.hljs-string),
.code-block :deep(.hljs-attr) {
  color: #9fe1c1;
}
.code-block :deep(.hljs-number),
.code-block :deep(.hljs-literal) {
  color: #f3b78c;
}
.code-block :deep(.hljs-comment) {
  color: #71809e;
  font-style: italic;
}
.code-block :deep(.hljs-title),
.code-block :deep(.hljs-function) {
  color: #8eb9ff;
}
</style>
