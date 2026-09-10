<template>
  <span class="streaming-cursor" aria-label="生成中"></span>
</template>
<style scoped>
.streaming-cursor {
  display: inline-block;
  width: 9px;
  height: 18px;
  margin-left: 4px;
  vertical-align: -3px;
  border-radius: 5px;
  background: linear-gradient(180deg, #6884ff, #a17cff);
  box-shadow: 0 0 14px rgba(104, 132, 255, 0.5);
  animation: pulse 1s ease-in-out infinite;
}
@keyframes pulse {
  50% {
    opacity: 0.28;
    transform: scaleY(0.7);
  }
}
@media (prefers-reduced-motion: reduce) {
  .streaming-cursor {
    animation: none;
  }
}
</style>
