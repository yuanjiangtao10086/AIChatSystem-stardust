<template>
  <div class="conversation-item" :class="{ active }">
    <label class="col-check" @click.stop>
      <input
        class="ui-checkbox"
        type="checkbox"
        :checked="selected"
        aria-label="选择对话"
        @change="$emit('toggle')"
      />
    </label>
    <router-link
      class="item-link"
      :to="{
        name: 'conversation',
        params: { conversationId: conversation.id },
      }"
      @click="$emit('select')"
      ><span class="item-title">{{ conversation.title }}</span
      ><span class="item-meta">{{ relativeTime }}</span></router-link
    >
  </div>
</template>
<script lang="ts">
import { computed, defineComponent, PropType } from "vue";
import { Conversation } from "@/types/conversation";
export default defineComponent({
  name: "ConversationItem",
  props: {
    conversation: { type: Object as PropType<Conversation>, required: true },
    active: Boolean,
    selected: Boolean,
  },
  emits: ["select", "toggle"],
  setup(props) {
    const relativeTime = computed(() => {
      const value =
        props.conversation.lastMessageAt || props.conversation.updatedAt;
      return new Intl.DateTimeFormat("zh-CN", {
        month: "numeric",
        day: "numeric",
      }).format(new Date(value));
    });
    return { relativeTime };
  },
});
</script>
<style scoped>
.conversation-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 6px 11px;
  border-radius: 10px;
  color: var(--ink);
  transition: background 0.15s ease;
}
.col-check {
  display: grid;
  place-items: center;
}
.item-link {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}
.conversation-item:hover {
  background: var(--surface-soft);
}
.conversation-item.active {
  background: #ececee;
}
.item-title {
  overflow: hidden;
  font-size: 0.86rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-meta {
  color: var(--ink-faint);
  font-size: 0.66rem;
}
.active .item-meta {
  color: var(--ink-soft);
}
</style>
