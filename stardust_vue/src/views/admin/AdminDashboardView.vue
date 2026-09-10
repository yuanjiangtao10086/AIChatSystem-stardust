<template>
  <div class="admin-dash">
    <div class="admin-toolbar">
      <button
        class="quiet-button"
        type="button"
        :disabled="loading"
        @click="load"
      >
        {{ loading ? "加载中…" : "刷新" }}
      </button>
      <span class="dash-hint">
        全部指标来自现有表的实时聚合（无预计算表）；趋势为最近 24 小时逐小时 UTC
        分桶。
      </span>
    </div>

    <p v-if="error" class="admin-error">{{ error }}</p>

    <div v-if="data" class="pulse">
      <div>
        <span>系统脉搏</span>
        <strong>{{ formatNumber(data.aiRequests) }}</strong>
        <small>累计 AI 请求</small>
      </div>
      <div class="pulse-chart">
        <div
          v-for="point in data.hourly"
          :key="point.bucketStart"
          class="pulse-bar"
          :title="barTitle(point)"
        >
          <i
            class="pulse-bar-fail"
            :style="{ height: failShare(point) + '%' }"
          ></i>
          <i
            class="pulse-bar-all"
            :style="{ height: barHeight(point) + '%' }"
          ></i>
        </div>
        <div class="pulse-axis">
          <small>{{ hourLabel(data.hourly[0]?.bucketStart) }}</small>
          <small>现在</small>
        </div>
      </div>
      <div>
        <b>{{ data.systemErrors }}</b>
        <small>今日失败（UTC）</small>
      </div>
    </div>

    <div v-if="data" class="metrics">
      <article>
        <span>用户总数</span>
        <strong>{{ formatNumber(data.totalUsers) }}</strong>
        <small>今日新增 {{ data.todayNewUsers }}</small>
      </article>
      <article>
        <span>活跃用户</span>
        <strong>{{ formatNumber(data.activeUsers) }}</strong>
        <small>近 30 天登录过</small>
      </article>
      <article>
        <span>对话总数</span>
        <strong>{{ formatNumber(data.conversations) }}</strong>
        <small>{{ formatNumber(data.messages) }} 条消息</small>
      </article>
      <article>
        <span>Token 用量</span>
        <strong>{{ formatNumber(data.totalTokens) }}</strong>
        <small>累计结算</small>
      </article>
      <article>
        <span>文件总数</span>
        <strong>{{ formatNumber(data.files) }}</strong>
        <small>占用 {{ formatBytes(data.storageBytes) }}</small>
      </article>
      <article>
        <span>RAG 文档</span>
        <strong>{{ formatNumber(data.ragDocuments) }}</strong>
        <small>知识库流水线</small>
      </article>
      <article>
        <span>启用服务商</span>
        <strong>{{ data.enabledProviders }}</strong>
        <small>AI 目录（已启用）</small>
      </article>
      <article>
        <span>启用模型</span>
        <strong>{{ data.enabledModels }}</strong>
        <small>停用模型不可调用</small>
      </article>
    </div>

    <div class="dash-feeds">
      <section class="admin-card">
        <h2>近期管理动作</h2>
        <p v-if="data && data.recentAudits.length === 0" class="dash-empty">
          暂无审计记录。
        </p>
        <table v-else-if="data" class="admin-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>管理员</th>
              <th>动作</th>
              <th>目标</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in data.recentAudits" :key="log.id">
              <td>
                <small>{{ formatDateTime(log.createdAt) }}</small>
              </td>
              <td>
                <span class="mono">{{ log.adminEmail }}</span>
              </td>
              <td>
                <span class="tag">{{
                  AUDIT_ACTION_LABELS[log.action] || log.action
                }}</span>
              </td>
              <td>
                <small class="mono">{{
                  log.targetResourceId || log.targetResourceType
                }}</small>
              </td>
            </tr>
          </tbody>
        </table>
        <router-link to="/admin/audit" class="dash-more"
          >查看完整审计日志 →</router-link
        >
      </section>

      <section class="admin-card">
        <h2>最近的失败调用</h2>
        <p v-if="data && data.recentFailures.length === 0" class="dash-empty">
          最近没有失败调用。
        </p>
        <table v-else-if="data" class="admin-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>服务商 / 模型</th>
              <th>错误码</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data.recentFailures" :key="row.requestId">
              <td>
                <small>{{ formatDateTime(row.createdAt) }}</small>
              </td>
              <td>
                <div class="admin-user-cell">
                  <strong>{{ row.provider }}</strong>
                  <small class="mono">{{ row.model }}</small>
                </div>
              </td>
              <td>
                <span class="tag">{{ row.errorCode || "—" }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <router-link to="/admin/ai/requests" class="dash-more">
          查看 AI 请求日志 →
        </router-link>
      </section>
    </div>
  </div>
</template>
<script lang="ts">
import { computed, defineComponent, onMounted, ref } from "vue";
import { getDashboard } from "@/api/admin";
import { AdminDashboard, AdminHourlyPoint } from "@/types/admin";
import {
  AUDIT_ACTION_LABELS,
  formatBytes,
  formatDateTime,
  formatNumber,
} from "@/utils/admin";

export default defineComponent({
  name: "AdminDashboardView",
  setup() {
    const data = ref<AdminDashboard | null>(null);
    const loading = ref(false);
    const error = ref("");

    const load = async () => {
      loading.value = true;
      error.value = "";
      try {
        data.value = await getDashboard();
      } catch (e) {
        error.value = e instanceof Error ? e.message : "总览加载失败";
      } finally {
        loading.value = false;
      }
    };

    const peak = computed(() =>
      Math.max(1, ...(data.value?.hourly ?? []).map((p) => p.requests))
    );
    const barHeight = (point: AdminHourlyPoint) =>
      Math.max(2, Math.round((point.requests / peak.value) * 100));
    const failShare = (point: AdminHourlyPoint) =>
      point.requests === 0
        ? 0
        : Math.round((point.failures / point.requests) * 100);
    const barTitle = (point: AdminHourlyPoint) =>
      `${formatDateTime(point.bucketStart)} · 请求 ${point.requests} · 失败 ${
        point.failures
      }`;
    const hourLabel = (value?: string) => {
      if (!value) return "";
      const date = new Date(value);
      return isNaN(date.getTime())
        ? ""
        : new Intl.DateTimeFormat("zh-CN", {
            hour: "2-digit",
            minute: "2-digit",
          }).format(date);
    };

    onMounted(load);

    return {
      data,
      loading,
      error,
      load,
      barHeight,
      failShare,
      barTitle,
      hourLabel,
      formatNumber,
      formatBytes,
      formatDateTime,
      AUDIT_ACTION_LABELS,
    };
  },
});
</script>
<style scoped>
.pulse {
  background: #172033;
  color: white;
  border-radius: 12px;
  padding: 28px;
  display: grid;
  grid-template-columns: 190px 1fr 120px;
  gap: 25px;
  align-items: end;
}
.pulse span {
  font: 10px monospace;
  letter-spacing: 0.14em;
  color: #91a0b9;
}
.pulse strong {
  display: block;
  font: 700 44px monospace;
  margin: 10px 0;
}
.pulse small {
  display: block;
  color: #8d9ab0;
}
.pulse-chart {
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  align-items: end;
  gap: 3px;
}
.pulse-bar {
  position: relative;
  height: 80px;
  display: flex;
  align-items: flex-end;
}
.pulse-bar i {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  border-radius: 2px;
}
.pulse-bar-all {
  background: #4f7cff;
  z-index: 1;
}
.pulse-bar-fail {
  background: #ff8d70;
  z-index: 2;
}
.pulse-axis {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  color: #8d9ab0;
  font-size: 0.7rem;
}
.pulse b {
  display: block;
  font-size: 28px;
  color: #ff8d70;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}
.metrics article {
  background: white;
  border: 1px solid #e1e6ed;
  border-radius: 10px;
  padding: 20px 22px;
}
.metrics span {
  font: 10px monospace;
  color: #6c788b;
  letter-spacing: 0.08em;
}
.metrics strong {
  display: block;
  font: 700 29px monospace;
  margin: 12px 0;
}
.metrics small {
  color: #7b8798;
}
.dash-hint {
  color: var(--ink-faint, #8e8ea0);
  font-size: 0.78rem;
}
.dash-feeds {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 18px;
}
.admin-card h2 {
  margin: 0 0 14px;
  font-size: 1rem;
}
.dash-empty {
  color: var(--ink-soft, #6e6e80);
  font-size: 0.88rem;
}
.dash-more {
  display: inline-block;
  margin-top: 14px;
  color: #4f7cff;
  font-size: 0.85rem;
  font-weight: 600;
  text-decoration: none;
}
@media (max-width: 1080px) {
  .metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dash-feeds {
    grid-template-columns: 1fr;
  }
  .pulse {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .metrics {
    grid-template-columns: 1fr;
  }
}
</style>
