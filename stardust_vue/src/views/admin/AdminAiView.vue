<template>
  <div class="admin-ai">
    <div class="ai-nav">
      <div class="ai-tabs">
        <button
          v-for="t in tabs"
          :key="t.id"
          type="button"
          :class="['ai-tab', { active: tab === t.id }]"
          @click="tab = t.id"
        >
          {{ t.label }}
        </button>
      </div>
      <router-link to="/admin/ai/requests" class="ai-link">
        AI 请求日志 →
      </router-link>
    </div>

    <p v-if="error" class="admin-error">{{ error }}</p>
    <p v-if="actionError" class="admin-error">{{ actionError }}</p>

    <!-- ------------------------------------------------ 服务商 -->
    <template v-if="tab === 'providers'">
      <div class="admin-toolbar">
        <button
          class="primary-button admin-create"
          type="button"
          @click="openProvider()"
        >
          + 新建服务商
        </button>
        <span class="ai-hint">
          API Key 只写不读：保存的是 <code>env:名称</code> 形式的密钥引用，
          列表与详情仅返回是否配置及掩码（如 <code>sk-****1234</code>）。
        </span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="providers.length === 0" class="admin-empty">
        还没有服务商。先创建一个上游服务商，再为其登记模型。
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>服务商</th>
              <th>类型</th>
              <th>接入地址</th>
              <th>API Key</th>
              <th>超时（请求 / 连接）</th>
              <th>模型数</th>
              <th>健康</th>
              <th>状态</th>
              <th class="admin-actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in providers" :key="p.id">
              <td>
                <div class="admin-user-cell">
                  <strong>{{ p.displayName }}</strong>
                  <small class="mono">{{ p.code }}</small>
                </div>
              </td>
              <td>{{ PROVIDER_TYPE_LABELS[p.type] || p.type }}</td>
              <td>
                <span class="mono">{{ p.baseUrl }}</span>
              </td>
              <td>
                <span v-if="p.maskedApiKey" class="mono">{{
                  p.maskedApiKey
                }}</span>
                <span v-else-if="p.hasApiKey" class="tag">已配置</span>
                <span v-else class="tag">未配置</span>
              </td>
              <td>
                <small>
                  {{ p.timeoutSeconds ?? "默认" }} s /
                  {{ p.connectTimeoutSeconds ?? "默认" }} s
                </small>
              </td>
              <td>{{ p.modelCount }}</td>
              <td>
                <span class="tag">{{
                  HEALTH_LABELS[p.health] || p.health
                }}</span>
              </td>
              <td>
                <span
                  class="status"
                  :class="
                    p.status === 'ENABLED' ? 'status-normal' : 'status-disabled'
                  "
                >
                  {{ p.status === "ENABLED" ? "已启用" : "已停用" }}
                </span>
              </td>
              <td class="admin-actions-col">
                <div class="admin-actions">
                  <button
                    class="row-btn"
                    type="button"
                    @click="openProvider(p)"
                  >
                    编辑
                  </button>
                  <button
                    class="row-btn"
                    type="button"
                    @click="askProviderStatus(p)"
                  >
                    {{ p.status === "ENABLED" ? "停用" : "启用" }}
                  </button>
                  <button
                    class="row-btn row-danger"
                    type="button"
                    :disabled="p.modelCount > 0"
                    :title="
                      p.modelCount > 0
                        ? '请先删除该服务商下的模型'
                        : '删除服务商'
                    "
                    @click="askProviderDelete(p)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- ------------------------------------------------ 模型 -->
    <template v-else-if="tab === 'models'">
      <div class="admin-toolbar">
        <button
          class="primary-button admin-create"
          type="button"
          :disabled="providers.length === 0"
          :title="providers.length === 0 ? '请先创建服务商' : '新建模型'"
          @click="openModel()"
        >
          + 新建模型
        </button>
        <select v-model="providerFilter" class="admin-select">
          <option value="">全部服务商</option>
          <option v-for="p in providers" :key="p.id" :value="p.id">
            {{ p.displayName }}
          </option>
        </select>
        <span class="ai-hint">
          新建的模型默认为<strong>停用</strong>；仅启用后可设为平台默认模型。
        </span>
      </div>

      <div v-if="loading" class="admin-loading">加载中…</div>
      <div v-else-if="filteredModels.length === 0" class="admin-empty">
        没有匹配的模型。模型必须归属一个服务商。
      </div>
      <div v-else class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>模型</th>
              <th>服务商</th>
              <th>类型</th>
              <th>能力</th>
              <th>上下文 / 输出</th>
              <th>默认参数</th>
              <th>排序</th>
              <th>状态</th>
              <th class="admin-actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in filteredModels" :key="m.id">
              <td>
                <div class="admin-user-cell">
                  <strong>
                    {{ m.displayName }}
                    <span v-if="m.defaultModel" class="ai-default-badge"
                      >默认</span
                    >
                  </strong>
                  <small class="mono"
                    >{{ m.code }} · {{ m.externalModelId }}</small
                  >
                </div>
              </td>
              <td>{{ m.providerName }}</td>
              <td>{{ MODEL_TYPE_LABELS[m.type] || m.type }}</td>
              <td>
                <span
                  v-for="tag in capabilityTags(m)"
                  :key="tag"
                  class="tag ai-tag"
                >
                  {{ tag }}
                </span>
              </td>
              <td>
                <small>
                  {{ m.contextWindow ?? "—" }} / {{ m.maxOutputTokens ?? "—" }}
                </small>
              </td>
              <td>
                <small>
                  t={{ m.defaultTemperature ?? "默认" }} · p={{
                    m.defaultTopP ?? "默认"
                  }}
                  · max={{ m.defaultMaxOutputTokens ?? "默认" }}
                </small>
              </td>
              <td>{{ m.sortOrder }}</td>
              <td>
                <span
                  class="status"
                  :class="
                    m.status === 'ENABLED' ? 'status-normal' : 'status-disabled'
                  "
                >
                  {{ m.status === "ENABLED" ? "已启用" : "已停用" }}
                </span>
              </td>
              <td class="admin-actions-col">
                <div class="admin-actions">
                  <button class="row-btn" type="button" @click="openModel(m)">
                    编辑
                  </button>
                  <button
                    class="row-btn"
                    type="button"
                    :disabled="m.status !== 'ENABLED'"
                    :title="
                      m.status !== 'ENABLED'
                        ? '仅启用的模型可设为默认'
                        : '设为默认模型'
                    "
                    @click="toggleDefault(m)"
                  >
                    {{ m.defaultModel ? "取消默认" : "设为默认" }}
                  </button>
                  <button
                    class="row-btn"
                    type="button"
                    :disabled="isFirst(m)"
                    title="上移"
                    @click="move(m, 'UP')"
                  >
                    ↑
                  </button>
                  <button
                    class="row-btn"
                    type="button"
                    :disabled="isLast(m)"
                    title="下移"
                    @click="move(m, 'DOWN')"
                  >
                    ↓
                  </button>
                  <button
                    class="row-btn"
                    type="button"
                    @click="askModelStatus(m)"
                  >
                    {{ m.status === "ENABLED" ? "停用" : "启用" }}
                  </button>
                  <button
                    class="row-btn row-danger"
                    type="button"
                    @click="askModelDelete(m)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <AdminProviderFormModal
      v-model="providerFormOpen"
      :provider="editingProvider"
      :busy="formBusy"
      :error="formError"
      @submit="submitProvider"
    />
    <AdminModelFormModal
      v-model="modelFormOpen"
      :model="editingModel"
      :providers="providers"
      :default-provider-id="providerFilter || providers[0]?.id || ''"
      :busy="formBusy"
      :error="formError"
      @submit="submitModel"
    />
    <AdminConfirmModal
      v-model="confirm.open"
      :title="confirm.title"
      :message="confirm.message"
      :busy="confirmBusy"
      :error="confirmError"
      @confirm="runConfirm"
    />
  </div>
</template>
<script lang="ts">
import { computed, defineComponent, onMounted, ref } from "vue";
import AdminProviderFormModal from "@/components/admin/AdminProviderFormModal.vue";
import AdminModelFormModal from "@/components/admin/AdminModelFormModal.vue";
import AdminConfirmModal from "@/components/admin/AdminConfirmModal.vue";
import {
  deleteAdminModel,
  deleteAdminProvider,
  listAdminModels,
  listAdminProviders,
  moveAdminModel,
  saveAdminModel,
  saveAdminProvider,
  setAdminModelDefault,
  setAdminModelStatus,
  setAdminProviderStatus,
} from "@/api/admin";
import {
  AdminModel,
  AdminModelPayload,
  AdminProvider,
  AdminProviderPayload,
  ModelCapabilities,
} from "@/types/admin";
import {
  CAPABILITY_LABELS,
  HEALTH_LABELS,
  MODEL_TYPE_LABELS,
  PROVIDER_TYPE_LABELS,
} from "@/utils/admin";

type TabId = "providers" | "models";
type ConfirmKind =
  | "provider-enable"
  | "provider-disable"
  | "provider-delete"
  | "model-enable"
  | "model-disable"
  | "model-delete";

interface ConfirmState {
  open: boolean;
  title: string;
  message: string;
  kind: ConfirmKind;
  provider: AdminProvider | null;
  model: AdminModel | null;
}

const CAPABILITY_KEYS: Array<keyof ModelCapabilities> = [
  "streaming",
  "vision",
  "reasoning",
  "embedding",
];

export default defineComponent({
  name: "AdminAiView",
  components: {
    AdminProviderFormModal,
    AdminModelFormModal,
    AdminConfirmModal,
  },
  setup() {
    const tabs: Array<{ id: TabId; label: string }> = [
      { id: "providers", label: "服务商" },
      { id: "models", label: "模型" },
    ];
    const tab = ref<TabId>("providers");
    const providers = ref<AdminProvider[]>([]);
    const models = ref<AdminModel[]>([]);
    const providerFilter = ref("");
    const loading = ref(false);
    const error = ref("");
    const actionError = ref("");

    const load = async () => {
      loading.value = true;
      error.value = "";
      try {
        const [p, m] = await Promise.all([
          listAdminProviders(),
          listAdminModels(),
        ]);
        providers.value = p;
        models.value = m;
      } catch (e) {
        error.value = e instanceof Error ? e.message : "AI 运营数据加载失败";
      } finally {
        loading.value = false;
      }
    };

    /* ------------------------------------------------ 服务商 */

    const providerFormOpen = ref(false);
    const editingProvider = ref<AdminProvider | null>(null);
    const openProvider = (p?: AdminProvider) => {
      editingProvider.value = p || null;
      formError.value = "";
      providerFormOpen.value = true;
    };
    const submitProvider = async (payload: AdminProviderPayload) => {
      formBusy.value = true;
      formError.value = "";
      try {
        await saveAdminProvider(payload, editingProvider.value?.id);
        providerFormOpen.value = false;
        await load();
      } catch (e) {
        formError.value = e instanceof Error ? e.message : "服务商保存失败";
      } finally {
        formBusy.value = false;
      }
    };

    const askProviderStatus = (p: AdminProvider) => {
      const enabled = p.status !== "ENABLED";
      confirm.value = {
        open: true,
        title: enabled ? "启用服务商" : "停用服务商",
        message: enabled
          ? `确认启用「${p.displayName}」？启用后其模型可被调用。`
          : `确认停用「${p.displayName}」？停用后该服务商下的模型将不再对用户可见。`,
        kind: enabled ? "provider-enable" : "provider-disable",
        provider: p,
        model: null,
      };
      confirmError.value = "";
    };
    const askProviderDelete = (p: AdminProvider) => {
      confirm.value = {
        open: true,
        title: "删除服务商",
        message: `确认删除「${p.displayName}」？该操作不可撤销，且会在审计日志中留下记录。`,
        kind: "provider-delete",
        provider: p,
        model: null,
      };
      confirmError.value = "";
    };

    /* ------------------------------------------------ 模型 */

    const modelFormOpen = ref(false);
    const editingModel = ref<AdminModel | null>(null);
    const openModel = (m?: AdminModel) => {
      editingModel.value = m || null;
      formError.value = "";
      modelFormOpen.value = true;
    };
    const submitModel = async (payload: AdminModelPayload) => {
      formBusy.value = true;
      formError.value = "";
      try {
        await saveAdminModel(payload, editingModel.value?.id);
        modelFormOpen.value = false;
        await load();
      } catch (e) {
        formError.value = e instanceof Error ? e.message : "模型保存失败";
      } finally {
        formBusy.value = false;
      }
    };

    const filteredModels = computed(() =>
      providerFilter.value
        ? models.value.filter((m) => m.providerId === providerFilter.value)
        : models.value
    );
    const siblings = (m: AdminModel) =>
      filteredModels.value.filter((s) => s.providerId === m.providerId);
    const isFirst = (m: AdminModel) => siblings(m).indexOf(m) <= 0;
    const isLast = (m: AdminModel) =>
      siblings(m).indexOf(m) === siblings(m).length - 1;

    const move = async (m: AdminModel, direction: "UP" | "DOWN") => {
      actionError.value = "";
      try {
        models.value = await moveAdminModel(m.id, direction);
      } catch (e) {
        actionError.value = e instanceof Error ? e.message : "排序失败";
      }
    };

    const toggleDefault = async (m: AdminModel) => {
      actionError.value = "";
      try {
        await setAdminModelDefault(m.id, !m.defaultModel);
        await load();
      } catch (e) {
        actionError.value = e instanceof Error ? e.message : "默认模型设置失败";
      }
    };

    const askModelStatus = (m: AdminModel) => {
      const enabled = m.status !== "ENABLED";
      confirm.value = {
        open: true,
        title: enabled ? "启用模型" : "停用模型",
        message: enabled
          ? `确认启用「${m.displayName}」？启用后用户可选择该模型。`
          : `确认停用「${m.displayName}」？停用会同时取消其默认模型标记。`,
        kind: enabled ? "model-enable" : "model-disable",
        provider: null,
        model: m,
      };
      confirmError.value = "";
    };
    const askModelDelete = (m: AdminModel) => {
      confirm.value = {
        open: true,
        title: "删除模型",
        message: `确认删除「${m.displayName}」？该操作不可撤销，且会在审计日志中留下记录。`,
        kind: "model-delete",
        provider: null,
        model: m,
      };
      confirmError.value = "";
    };

    /* ------------------------------------------------ 确认与提交 */

    const formBusy = ref(false);
    const formError = ref("");
    const confirm = ref<ConfirmState>({
      open: false,
      title: "",
      message: "",
      kind: "provider-disable",
      provider: null,
      model: null,
    });
    const confirmBusy = ref(false);
    const confirmError = ref("");

    const runConfirm = async () => {
      confirmBusy.value = true;
      confirmError.value = "";
      const { kind, provider, model } = confirm.value;
      try {
        switch (kind) {
          case "provider-enable":
          case "provider-disable":
            if (provider)
              await setAdminProviderStatus(
                provider.id,
                kind === "provider-enable"
              );
            break;
          case "provider-delete":
            if (provider) await deleteAdminProvider(provider.id);
            break;
          case "model-enable":
          case "model-disable":
            if (model)
              await setAdminModelStatus(model.id, kind === "model-enable");
            break;
          case "model-delete":
            if (model) await deleteAdminModel(model.id);
            break;
        }
        confirm.value.open = false;
        await load();
      } catch (e) {
        confirmError.value = e instanceof Error ? e.message : "操作失败";
      } finally {
        confirmBusy.value = false;
      }
    };

    const capabilityTags = (m: AdminModel) =>
      CAPABILITY_KEYS.filter((key) => m.capabilities?.[key]).map(
        (key) => CAPABILITY_LABELS[key]
      );

    onMounted(load);

    return {
      tabs,
      tab,
      providers,
      models,
      providerFilter,
      filteredModels,
      loading,
      error,
      actionError,
      providerFormOpen,
      editingProvider,
      openProvider,
      submitProvider,
      askProviderStatus,
      askProviderDelete,
      modelFormOpen,
      editingModel,
      openModel,
      submitModel,
      toggleDefault,
      move,
      isFirst,
      isLast,
      askModelStatus,
      askModelDelete,
      formBusy,
      formError,
      confirm,
      confirmBusy,
      confirmError,
      runConfirm,
      capabilityTags,
      PROVIDER_TYPE_LABELS,
      MODEL_TYPE_LABELS,
      HEALTH_LABELS,
    };
  },
});
</script>
<style scoped>
.ai-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.ai-tabs {
  display: flex;
  gap: 8px;
}
.ai-link {
  color: #4f7cff;
  font-size: 0.85rem;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
}
.ai-tab {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink-soft);
  border-radius: 999px;
  padding: 8px 16px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}
.ai-tab.active {
  color: #fff;
  background: #4f7cff;
  border-color: #4f7cff;
}
.ai-hint {
  color: var(--ink-faint);
  font-size: 0.78rem;
  line-height: 1.5;
  flex: 1 1 320px;
}
.ai-hint code {
  font-family: "Cascadia Mono", monospace;
}
.ai-tag {
  margin: 0 4px 4px 0;
}
.ai-default-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e8f7f2;
  border: 1px solid #bfe9dd;
  color: #0c6b57;
  font-size: 0.7rem;
  font-weight: 650;
}
.row-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
