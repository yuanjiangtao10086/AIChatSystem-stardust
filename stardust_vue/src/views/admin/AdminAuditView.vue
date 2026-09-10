<template>
  <div class="admin-audit">
    <div class="admin-toolbar">
      <input
        v-model.trim="filters.adminId"
        class="admin-user-filter"
        type="text"
        placeholder="管理员 publicId"
        @keyup.enter="search"
      />
      <select v-model="filters.action" class="admin-select">
        <option value="">全部动作</option>
        <option
          v-for="(label, key) in AUDIT_ACTION_LABELS"
          :key="key"
          :value="key"
        >
          {{ label }}
        </option>
      </select>
      <select v-model="filters.targetType" class="admin-select">
        <option value="">全部目标</option>
        <option
          v-for="(label, key) in AUDIT_TARGET_LABELS"
          :key="key"
          :value="key"
        >
          {{ label }}
        </option>
      </select>
      <input v-model="fromInput" class="admin-date" type="datetime-local" />
      <span class="admin-date-sep">至</span>
      <input v-model="toInput" class="admin-date" type="datetime-local" />
      <button
        class="primary-button"
        type="button"
        :disabled="loading"
        @click="search"
      >
        筛选
      </button>
      <button
        class="quiet-button"
        type="button"
        :disabled="loading"
        @click="reset"
      >
        重置
      </button>
    </div>

    <p class="admin-note">
      审计日志只可读取：没有写入、修改或删除入口，也无法由前端伪造。
    </p>

    <p v-if="error" class="admin-error">{{ error }}</p>
    <div v-if="loading" class="admin-loading">加载中…</div>
    <div v-else-if="items.length === 0" class="admin-empty">
      没有匹配的审计记录。
    </div>
    <template v-else>
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>管理员</th>
              <th>动作</th>
              <th>目标</th>
              <th>目标用户</th>
              <th>来源</th>
              <th>请求</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in items" :key="log.id">
              <td>
                <small>{{ formatDateTime(log.createdAt) }}</small>
              </td>
              <td>
                <div class="admin-user-cell">
                  <strong>{{ log.adminEmail }}</strong>
                  <small class="mono">{{ log.adminId }}</small>
                </div>
              </td>
              <td>
                <span class="tag">{{
                  AUDIT_ACTION_LABELS[log.action] || log.action
                }}</span>
              </td>
              <td>
                <div class="admin-user-cell">
                  <strong>{{
                    AUDIT_TARGET_LABELS[log.targetResourceType] ||
                    log.targetResourceType
                  }}</strong>
                  <small class="mono">{{ log.targetResourceId || "—" }}</small>
                </div>
              </td>
              <td>
                <small class="mono">{{ log.targetUserId || "—" }}</small>
              </td>
              <td>
                <div class="admin-user-cell">
                  <strong>{{ log.ip }}</strong>
                  <small :title="log.userAgent || ''">
                    {{ shortAgent(log.userAgent) }}
                  </small>
                </div>
              </td>
              <td>
                <span class="mono" :title="log.metadataJson || ''">
                  {{ log.requestId }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <AdminPager
        :page="page"
        :size="PAGE_SIZE"
        :total-elements="totalElements"
        @change="goToPage"
      />
    </template>
  </div>
</template>
<script lang="ts">
import { defineComponent, onMounted, reactive, ref } from "vue";
import AdminPager from "@/components/admin/AdminPager.vue";
import { listAdminAuditLogs } from "@/api/admin";
import { AdminAudit } from "@/types/admin";
import {
  AUDIT_ACTION_LABELS,
  AUDIT_TARGET_LABELS,
  formatDateTime,
} from "@/utils/admin";

const PAGE_SIZE = 20;

export default defineComponent({
  name: "AdminAuditView",
  components: { AdminPager },
  setup() {
    const filters = reactive({
      adminId: "",
      action: "",
      targetType: "",
    });
    const fromInput = ref("");
    const toInput = ref("");
    const items = ref<AdminAudit[]>([]);
    const page = ref(0);
    const totalElements = ref(0);
    const loading = ref(false);
    const error = ref("");

    const toInstant = (value: string): string | undefined => {
      if (!value) return undefined;
      const date = new Date(value);
      return isNaN(date.getTime()) ? undefined : date.toISOString();
    };

    const load = async () => {
      loading.value = true;
      error.value = "";
      try {
        const result = await listAdminAuditLogs({
          page: page.value,
          size: PAGE_SIZE,
          adminId: filters.adminId || undefined,
          action: filters.action || undefined,
          targetType: filters.targetType || undefined,
          from: toInstant(fromInput.value),
          to: toInstant(toInput.value),
        });
        items.value = result.items;
        totalElements.value = result.totalElements;
      } catch (e) {
        error.value = e instanceof Error ? e.message : "审计日志加载失败";
      } finally {
        loading.value = false;
      }
    };

    const search = () => {
      page.value = 0;
      return load();
    };
    const goToPage = (next: number) => {
      page.value = next;
      return load();
    };
    const reset = () => {
      filters.adminId = "";
      filters.action = "";
      filters.targetType = "";
      fromInput.value = "";
      toInput.value = "";
      return search();
    };

    const shortAgent = (agent?: string) => {
      if (!agent) return "未知客户端";
      return agent.length > 40 ? `${agent.slice(0, 40)}…` : agent;
    };

    onMounted(load);

    return {
      filters,
      fromInput,
      toInput,
      items,
      page,
      totalElements,
      loading,
      error,
      search,
      reset,
      goToPage,
      shortAgent,
      formatDateTime,
      AUDIT_ACTION_LABELS,
      AUDIT_TARGET_LABELS,
      PAGE_SIZE,
    };
  },
});
</script>
