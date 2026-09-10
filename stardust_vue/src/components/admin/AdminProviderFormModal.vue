<template>
  <AdminModal
    :model-value="modelValue"
    :title="isEdit ? '编辑服务商' : '新建服务商'"
    :error="error"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <form @submit.prevent="submit">
      <label>
        服务商代码
        <input
          v-model.trim="code"
          type="text"
          placeholder="openai-prod"
          autocomplete="off"
          :disabled="busy || isEdit"
        />
        <small>小写字母、数字与连字符；创建后不可修改。</small>
      </label>
      <label>
        显示名称
        <input
          v-model.trim="displayName"
          type="text"
          placeholder="OpenAI 生产"
          :disabled="busy"
        />
      </label>
      <label>
        类型
        <select v-model="type" :disabled="busy">
          <option v-for="t in providerTypes" :key="t" :value="t">
            {{ PROVIDER_TYPE_LABELS[t] }}
          </option>
        </select>
      </label>
      <label>
        接入地址 Base URL
        <input
          v-model.trim="baseUrl"
          type="text"
          placeholder="https://api.openai.com/v1"
          :disabled="busy"
        />
      </label>
      <label>
        {{ isEdit ? "API Key 引用（留空保持原值）" : "API Key 引用" }}
        <input
          v-model.trim="credentialRef"
          type="text"
          autocomplete="off"
          placeholder="env:OPENAI_API_KEY"
          :disabled="busy"
        />
        <small>
          平台只保存密钥<strong>引用</strong>（env:名称 / vault:路径），
          不存储明文；粘贴真实 Key 会被服务端拒绝，且不会写入审计日志。
        </small>
      </label>
      <p v-if="isEdit" class="form-note">{{ credentialSummary }}</p>
      <div class="ai-form-row">
        <label>
          请求超时（秒）
          <input
            v-model.number="timeoutSeconds"
            type="number"
            min="1"
            max="300"
            :disabled="busy"
          />
        </label>
        <label>
          连接超时（秒）
          <input
            v-model.number="connectTimeoutSeconds"
            type="number"
            min="1"
            max="60"
            :disabled="busy"
          />
        </label>
      </div>
    </form>
    <template #footer>
      <button
        class="quiet-button"
        type="button"
        :disabled="busy"
        @click="close"
      >
        取消
      </button>
      <button
        class="primary-button"
        type="button"
        :disabled="busy || !valid"
        @click="submit"
      >
        {{ busy ? "提交中…" : isEdit ? "保存" : "创建" }}
      </button>
    </template>
  </AdminModal>
</template>
<script lang="ts">
import { computed, defineComponent, PropType, ref, watch } from "vue";
import AdminModal from "@/components/admin/AdminModal.vue";
import {
  AdminProvider,
  AdminProviderPayload,
  ProviderType,
} from "@/types/admin";
import { CREDENTIAL_REF_PATTERN, PROVIDER_TYPE_LABELS } from "@/utils/admin";

const PROVIDER_TYPES: ProviderType[] = [
  "OPENAI",
  "AZURE_OPENAI",
  "ANTHROPIC",
  "GEMINI",
  "DEEPSEEK",
  "OLLAMA",
  "OPENROUTER",
  "OPENAI_COMPATIBLE",
];

export default defineComponent({
  name: "AdminProviderFormModal",
  components: { AdminModal },
  props: {
    modelValue: { type: Boolean, default: false },
    provider: { type: Object as PropType<AdminProvider | null>, default: null },
    busy: { type: Boolean, default: false },
    error: { type: String, default: "" },
  },
  emits: ["update:modelValue", "submit"],
  setup(props, { emit }) {
    const code = ref("");
    const displayName = ref("");
    const type = ref<ProviderType>("OPENAI_COMPATIBLE");
    const baseUrl = ref("");
    const credentialRef = ref("");
    const timeoutSeconds = ref<number | "">("");
    const connectTimeoutSeconds = ref<number | "">("");

    const isEdit = computed(() => !!props.provider);

    watch(
      () => props.modelValue,
      (open) => {
        if (!open) return;
        code.value = props.provider?.code || "";
        displayName.value = props.provider?.displayName || "";
        type.value = props.provider?.type || "OPENAI_COMPATIBLE";
        baseUrl.value = props.provider?.baseUrl || "";
        credentialRef.value = "";
        timeoutSeconds.value = props.provider?.timeoutSeconds ?? "";
        connectTimeoutSeconds.value =
          props.provider?.connectTimeoutSeconds ?? "";
      }
    );

    const credentialSummary = computed(() => {
      const current = props.provider;
      if (!current?.hasApiKey)
        return "当前凭证：未配置（该服务商不接受密钥调用）。";
      if (current.maskedApiKey) {
        return `当前凭证：已配置（${current.maskedApiKey}），留空即保持不变。`;
      }
      return "当前凭证：已配置，但部署环境未提供可解析的密钥值，因此无法显示掩码。";
    });

    const valid = computed(() => {
      if (!/^[a-z0-9-]{2,64}$/.test(code.value)) return false;
      if (!displayName.value || displayName.value.length > 100) return false;
      if (!/^https?:\/\/.+/.test(baseUrl.value)) return false;
      if (!CREDENTIAL_REF_PATTERN.test(credentialRef.value)) return false;
      if (
        timeoutSeconds.value !== "" &&
        (timeoutSeconds.value < 1 || timeoutSeconds.value > 300)
      )
        return false;
      if (
        connectTimeoutSeconds.value !== "" &&
        (connectTimeoutSeconds.value < 1 || connectTimeoutSeconds.value > 60)
      )
        return false;
      return true;
    });

    const number = (value: number | ""): number | null =>
      value === "" ? null : Number(value);

    const close = () => emit("update:modelValue", false);
    const submit = () => {
      if (!valid.value) return;
      const payload: AdminProviderPayload = {
        code: code.value,
        displayName: displayName.value,
        type: type.value,
        baseUrl: baseUrl.value,
        credentialRef: credentialRef.value || null,
        timeoutSeconds: number(timeoutSeconds.value),
        connectTimeoutSeconds: number(connectTimeoutSeconds.value),
      };
      emit("submit", payload);
    };

    return {
      code,
      displayName,
      type,
      baseUrl,
      credentialRef,
      timeoutSeconds,
      connectTimeoutSeconds,
      isEdit,
      valid,
      credentialSummary,
      close,
      submit,
      providerTypes: PROVIDER_TYPES,
      PROVIDER_TYPE_LABELS,
    };
  },
});
</script>
<style scoped>
.ai-form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
@media (max-width: 520px) {
  .ai-form-row {
    grid-template-columns: 1fr;
  }
}
</style>
