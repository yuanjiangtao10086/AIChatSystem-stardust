<template>
  <div class="artifact-list">
    <ChatArtifactCard
      v-for="(artifact, index) in artifacts"
      :key="artifact.artifactId || `artifact-${index}`"
      :artifact="artifact"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from "vue";
import ChatArtifactCard from "./ChatArtifactCard.vue";
import type { ChatArtifact } from "@/types/conversation";

export default defineComponent({
  name: "ChatArtifactList",
  components: { ChatArtifactCard },
  props: {
    artifacts: { type: Array as PropType<ChatArtifact[]>, required: true },
  },
});
</script>

<style scoped>
.artifact-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
</style>
