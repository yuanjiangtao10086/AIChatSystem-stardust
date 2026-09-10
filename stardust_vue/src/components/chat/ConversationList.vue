<template>
  <div class="conversation-group">
    <p class="group-label">最近对话</p>
    <div v-if="loading" class="list-state">正在加载…</div>
    <div v-else-if="!conversations.length" class="list-state">
      暂无对话<br /><small>点击上方按钮开始新的聊天。</small>
    </div>
    <nav v-else aria-label="最近对话">
      <ConversationItem
        v-for="item in conversations"
        :key="item.id"
        :conversation="item"
        :active="item.id === activeId"
        @select="$emit('select')"
      />
    </nav>
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from "vue";
import { Conversation } from "@/types/conversation";
import ConversationItem from "./ConversationItem.vue";

export default defineComponent({
  name: "ConversationList",
  components: { ConversationItem },
  props: {
    conversations: {
      type: Array as PropType<Conversation[]>,
      required: true,
    },
    activeId: String,
    loading: Boolean,
  },
  emits: ["select"],
});
</script>

<style scoped>
.conversation-group {
  min-height: 0;
  overflow: auto;
}
.group-label {
  margin: 14px 11px 7px;
  color: var(--ink-faint);
  font-size: 0.72rem;
  font-weight: 600;
}
.list-state {
  padding: 18px 11px;
  color: var(--ink-faint);
  font-size: 0.82rem;
  line-height: 1.6;
}
.list-state small {
  color: var(--ink-faint);
}
</style>
