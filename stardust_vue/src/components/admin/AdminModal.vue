<template>
  <teleport to="body">
    <div v-if="modelValue" class="modal-overlay" @click.self="close">
      <div class="modal" role="dialog" aria-modal="true">
        <header class="modal-head">
          <h2>{{ title }}</h2>
          <button
            class="modal-x"
            type="button"
            aria-label="关闭"
            @click="close"
          >
            ×
          </button>
        </header>
        <div class="modal-body">
          <p v-if="error" class="form-error">{{ error }}</p>
          <slot />
        </div>
        <footer v-if="$slots.footer" class="modal-foot">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </teleport>
</template>
<script lang="ts">
import { defineComponent } from "vue";

export default defineComponent({
  name: "AdminModal",
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, required: true },
    error: { type: String, default: "" },
  },
  emits: ["update:modelValue"],
  setup(_, { emit }) {
    const close = () => emit("update:modelValue", false);
    return { close };
  },
});
</script>
<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: grid;
  place-items: center;
  padding: 20px;
  z-index: 100;
}
.modal {
  width: min(560px, 100%);
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.28);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 90vh;
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-bottom: 1px solid #eceef3;
}
.modal-head h2 {
  margin: 0;
  font-size: 17px;
  letter-spacing: -0.01em;
}
.modal-x {
  border: 0;
  background: transparent;
  font-size: 24px;
  line-height: 1;
  color: #8a93a6;
  cursor: pointer;
}
.modal-body {
  padding: 22px;
  overflow: auto;
}
.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 22px;
  border-top: 1px solid #eceef3;
  background: #fafbfc;
}
</style>
