<template>
  <div class="hello">
    <h1>{{ msg }}</h1>
    <p>星语 AI —— 生产级 AI 对话 SaaS 平台。</p>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";

export default defineComponent({
  name: "HelloWorld",
  props: {
    msg: String,
  },
});
</script>

<style scoped>
.hello {
  max-width: 720px;
  margin: 80px auto;
  padding: 0 20px;
  text-align: center;
}
.hello p {
  color: var(--ink-soft);
  line-height: 1.7;
}
</style>
