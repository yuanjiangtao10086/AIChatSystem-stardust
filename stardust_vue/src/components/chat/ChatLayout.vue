<template>
  <div class="chat-layout">
    <div class="sidebar-panel" :class="{ open: sidebarOpen }">
      <slot name="sidebar" />
    </div>
    <button
      v-if="sidebarOpen"
      class="scrim"
      type="button"
      aria-label="Close sidebar"
      @click="$emit('close-sidebar')"
    ></button>
    <main class="chat-main"><slot /></main>
  </div>
</template>
<script lang="ts">
import { defineComponent } from "vue";
export default defineComponent({
  name: "ChatLayout",
  props: { sidebarOpen: Boolean },
  emits: ["close-sidebar"],
});
</script>
<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  height: 100dvh;
  min-height: 520px;
  overflow: hidden;
  background: #fff;
}
.sidebar-panel {
  min-width: 0;
  min-height: 0;
}
.sidebar-panel > * {
  height: 100%;
}
.chat-main {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  background: #fff;
}
.scrim {
  display: none;
}
@media (max-width: 760px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
  .sidebar-panel {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 30;
    width: min(86vw, 300px);
    transform: translateX(-102%);
    transition: transform 0.22s ease;
  }
  .sidebar-panel.open {
    transform: translateX(0);
  }
  .scrim {
    position: fixed;
    inset: 0;
    z-index: 20;
    display: block;
    border: 0;
    background: rgba(13, 17, 25, 0.48);
    backdrop-filter: blur(2px);
  }
  .chat-main {
    grid-column: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .sidebar-panel {
    transition: none;
  }
}
</style>
