<template>
  <AdminModal
    :model-value="modelValue"
    :title="isEdit ? '编辑模型' : '新建模型'"
    :error="error"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <form @submit.prevent="submit">
      <label>
        所属服务商
        <select v-model="providerId" :disabled="busy || isEdit">
          <option value="" disabled>请选择服务商</option>
          <option v-for="p in providers" :key="p.id" :value="p.id">
            {{ p.displayName }}（{{ p.code }}）
          </option>
        </select>
        <small>模型必须归属一个服务商；创建后不可更改归属。</small>
      </label>
      <label>
        模型代码
        <input
          v-model.trim="code"
          type="text"
          placeholder="gpt-4o-mini"
          autocomplete="off"
          :disabled="busy || isEdit"
        />
        <small>平台内部标识，创建后不可修改。</small>
      </label>
      <label>
        上游模型 ID
        <input
          v-model.trim="externalModelId"
          type="text"
          placeholder="gpt-4o-mini"
          :disabled="busy"
        />
        <small>调用上游接口时实际发送的 model 名称，同一服务商内唯一。</small>
      </label>
      <label>
        显示名称
        <input v-model.trim="displayName" type="text" :disabled="busy" />
      </label>
      <label>
        模型类型
        <select v-model="type" :disabled="busy">
          <option v-for="t in MODEL_TYPES" :key="t" :value="t">
            {{ MODEL_TYPE_LABELS[t] }}
          </option>
        </select>
      </label>

      <fieldset class="ai-fieldset">
        <legend>能力</legend>
        <label v-for="key in CAPABILITY_KEYS" :key="key" class="ai-check">
          <input v-model="capabilities[key]" type="checkbox" :disabled="busy" />
          {{ CAPABILITY_LABELS[key] }}
        </label>
      </fieldset>

      <div class="ai-form-row">
        <label>
          上下文窗口（tokens）
          <input
            v-model.number="contextWindow"
            type="number"
            min="1"
            :disabled="busy"
          />
        </label>
        <label>
          最大输入/输出上限
          <input
            v-model.number="maxOutputTokens"
            type="number"
            min="1"
            :disabled="busy"
          />
        </label>
      </div>

      <div class="ai-form-row">
        <label>
          temperature 默认值
          <input
            v-model.number="defaultTemperature"
            type="number"
            min="0"
            max="2"
            step="0.1"
            :disabled="busy"
          />
        </label>
        <label>
          top_p 默认值
          <input
            v-model.number="defaultTopP"
            type="number"
            min="0"
            max="1"
            step="0.05"
            :disabled="busy"
          />
        </label>
      </div>

      <div class="ai-form-row">
        <label>
          默认最大输出（tokens）
          <input
            v-model.number="defaultMaxOutputTokens"
            type="number"
            min="1"
            :disabled="busy"
          />
        </label>
        <label>
          排序值
          <input v-model.number="sortOrder" type="number" :disabled="busy" />
        </label>
      </div>

      <div class="ai-form-row">
        <label>
          输入价格 / 1K tokens
          <input
            v-model.number="inputPrice"
            type="number"
            min="0"
            step="0.0001"
            :disabled="busy"
          />
        </label>
        <label>
          输出价格 / 1K tokens
          <input
            v-model.number="outputPrice"
            type="number"
            min="0"
            step="0.0001"
            :disabled="busy"
          />
        </label>
      </div>

      <label>
        计价币种
        <input
          v-model.trim="currency"
          type="text"
          maxlength="3"
          placeholder="USD"
          :disabled="busy"
        />
        <small>三位币种代码，留空表示不参与计费。</small>
      </label>
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
  AdminModel,
  AdminModelPayload,
  AdminProvider,
  ModelCapabilities,
  ModelType,
} from "@/types/admin";
import { CAPABILITY_LABELS, MODEL_TYPE_LABELS } from "@/utils/admin";

const MODEL_TYPES: ModelType[] = ["CHAT", "MULTIMODAL", "EMBEDDING", "RERANK"];
const CAPABILITY_KEYS: Array<keyof ModelCapabilities> = [
  "streaming",
  "vision",
  "reasoning",
  "embedding",
];

type NumberInput = number | "";

export default defineComponent({
  name: "AdminModelFormModal",
  components: { AdminModal },
  props: {
    modelValue: { type: Boolean, default: false },
    model: { type: Object as PropType<AdminModel | null>, default: null },
    providers: {
      type: Array as PropType<AdminProvider[]>,
      default: () => [],
    },
    defaultProviderId: { type: String, default: "" },
    busy: { type: Boolean, default: false },
    error: { type: String, default: "" },
  },
  emits: ["update:modelValue", "submit"],
  setup(props, { emit }) {
    const providerId = ref("");
    const code = ref("");
    const externalModelId = ref("");
    const displayName = ref("");
    const type = ref<ModelType>("CHAT");
    const capabilities = ref<ModelCapabilities>({
      streaming: true,
      vision: false,
      reasoning: false,
      embedding: false,
    });
    const contextWindow = ref<NumberInput>("");
    const maxOutputTokens = ref<NumberInput>("");
    const defaultTemperature = ref<NumberInput>("");
    const defaultTopP = ref<NumberInput>("");
    const defaultMaxOutputTokens = ref<NumberInput>("");
    const inputPrice = ref<NumberInput>("");
    const outputPrice = ref<NumberInput>("");
    const currency = ref("");
    const sortOrder = ref(10);

    const isEdit = computed(() => !!props.model);

    watch(
      () => props.modelValue,
      (open) => {
        if (!open) return;
        const current = props.model;
        providerId.value = current?.providerId || props.defaultProviderId || "";
        code.value = current?.code || "";
        externalModelId.value = current?.externalModelId || "";
        displayName.value = current?.displayName || "";
        type.value = current?.type || "CHAT";
        capabilities.value = current?.capabilities
          ? { ...current.capabilities }
          : {
              streaming: true,
              vision: false,
              reasoning: false,
              embedding: false,
            };
        contextWindow.value = current?.contextWindow ?? "";
        maxOutputTokens.value = current?.maxOutputTokens ?? "";
        defaultTemperature.value = current?.defaultTemperature ?? "";
        defaultTopP.value = current?.defaultTopP ?? "";
        defaultMaxOutputTokens.value = current?.defaultMaxOutputTokens ?? "";
        inputPrice.value = current?.inputPrice ?? "";
        outputPrice.value = current?.outputPrice ?? "";
        currency.value = current?.currency || "";
        sortOrder.value = current?.sortOrder ?? 10;
      }
    );

    const within = (value: NumberInput, min: number, max?: number): boolean => {
      if (value === "") return true;
      const n = Number(value);
      if (Number.isNaN(n) || n < min) return false;
      return max === undefined || n <= max;
    };

    const valid = computed(() => {
      if (!providerId.value) return false;
      if (!/^[a-z0-9._-]{2,64}$/.test(code.value)) return false;
      if (!externalModelId.value || externalModelId.value.length > 128)
        return false;
      if (!displayName.value || displayName.value.length > 100) return false;
      if (!within(contextWindow.value, 1)) return false;
      if (!within(maxOutputTokens.value, 1)) return false;
      if (!within(defaultTemperature.value, 0, 2)) return false;
      if (!within(defaultTopP.value, 0, 1)) return false;
      if (!within(defaultMaxOutputTokens.value, 1)) return false;
      if (!within(inputPrice.value, 0)) return false;
      if (!within(outputPrice.value, 0)) return false;
      if (currency.value !== "" && !/^[A-Za-z]{3}$/.test(currency.value))
        return false;
      return true;
    });

    const number = (value: NumberInput): number | null =>
      value === "" ? null : Number(value);

    const close = () => emit("update:modelValue", false);
    const submit = () => {
      if (!valid.value) return;
      const payload: AdminModelPayload = {
        providerId: providerId.value,
        code: code.value,
        externalModelId: externalModelId.value,
        displayName: displayName.value,
        type: type.value,
        capabilities: { ...capabilities.value },
        contextWindow: number(contextWindow.value),
        maxOutputTokens: number(maxOutputTokens.value),
        defaultTemperature: number(defaultTemperature.value),
        defaultTopP: number(defaultTopP.value),
        defaultMaxOutputTokens: number(defaultMaxOutputTokens.value),
        inputPrice: number(inputPrice.value),
        outputPrice: number(outputPrice.value),
        currency: currency.value ? currency.value.toUpperCase() : null,
        sortOrder: Number(sortOrder.value) || 0,
      };
      emit("submit", payload);
    };

    return {
      providerId,
      code,
      externalModelId,
      displayName,
      type,
      capabilities,
      contextWindow,
      maxOutputTokens,
      defaultTemperature,
      defaultTopP,
      defaultMaxOutputTokens,
      inputPrice,
      outputPrice,
      currency,
      sortOrder,
      isEdit,
      valid,
      close,
      submit,
      MODEL_TYPES,
      MODEL_TYPE_LABELS,
      CAPABILITY_KEYS,
      CAPABILITY_LABELS,
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
.ai-fieldset {
  border: 0;
  padding: 0;
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}
.ai-fieldset legend {
  font-size: 0.88rem;
  font-weight: 600;
  margin-bottom: 10px;
  color: #344;
}
.ai-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: auto;
  font-weight: 500;
}
.ai-check input {
  width: auto;
  min-height: 0;
}
@media (max-width: 520px) {
  .ai-form-row {
    grid-template-columns: 1fr;
  }
}
</style>
