<template>
  <div class="pager">
    <button
      class="pager-btn"
      type="button"
      :disabled="page <= 0"
      @click="$emit('change', page - 1)"
    >
      上一页
    </button>
    <span class="pager-info"
      >第 {{ page + 1 }} / {{ totalPages }} 页 · 共 {{ totalElements }} 条</span
    >
    <button
      class="pager-btn"
      type="button"
      :disabled="page + 1 >= totalPages"
      @click="$emit('change', page + 1)"
    >
      下一页
    </button>
  </div>
</template>
<script lang="ts">
import { computed, defineComponent } from "vue";

export default defineComponent({
  name: "AdminPager",
  props: {
    page: { type: Number, required: true },
    size: { type: Number, default: 20 },
    totalElements: { type: Number, default: 0 },
  },
  emits: ["change"],
  setup(props) {
    return {
      totalPages: computed(() =>
        Math.max(1, Math.ceil(props.totalElements / props.size))
      ),
    };
  },
});
</script>
<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 18px;
}
.pager-btn {
  border: 1px solid #d8dde6;
  background: #fff;
  border-radius: 8px;
  padding: 8px 16px;
  cursor: pointer;
  font-weight: 600;
  color: #2a2a2a;
}
.pager-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.pager-info {
  color: #6e7686;
  font-size: 0.85rem;
}
</style>
