<template>
  <section class="admin-usage">
    <div class="usage-controls">
      <div class="seg">
        <button
          v-for="d in dimensions"
          :key="d.value"
          :class="['seg-btn', { active: by === d.value }]"
          type="button"
          @click="by = d.value"
        >
          {{ d.label }}
        </button>
      </div>
      <div class="seg">
        <button
          v-for="r in ranges"
          :key="r.value"
          :class="['seg-btn', { active: range === r.value }]"
          type="button"
          @click="range = r.value"
        >
          {{ r.label }}
        </button>
      </div>
    </div>

    <div v-if="error" class="usage-error">{{ error }}</div>

    <div class="usage-cards">
      <div class="stat">
        <small>请求数</small>
        <strong>{{ format(totalRequests) }}</strong>
      </div>
      <div class="stat">
        <small>总 Token</small>
        <strong>{{ format(totalTokens) }}</strong>
      </div>
      <div class="stat">
        <small>提示 Token</small>
        <strong>{{ format(totalPrompt) }}</strong>
      </div>
      <div class="stat">
        <small>生成 Token</small>
        <strong>{{ format(totalCompletion) }}</strong>
      </div>
    </div>

    <div class="chart-card">
      <p v-if="loading" class="chart-empty">加载中…</p>
      <p v-else-if="!items.length" class="chart-empty">
        所选区间暂无已结算用量
      </p>
      <div v-else class="bars" :class="{ wrap: by !== 'DAY' }">
        <div v-for="item in items" :key="item.key" class="bar-col">
          <div class="bar-track">
            <div
              class="bar"
              :style="{ height: pct(item.totalTokens) + '%' }"
              :title="`${label(item.key)} · 请求 ${format(
                item.requestCount
              )} · Token ${format(item.totalTokens)}`"
            ></div>
          </div>
          <span class="bar-label">{{ shortLabel(item.key) }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script lang="ts">
import { computed, defineComponent, onMounted, ref, watch } from "vue";
import { getAdminUsageBreakdown } from "@/api/usage";
import { BreakdownDimension, UsageBreakdownItem } from "@/types/usage";

const format = (value: number): string =>
  new Intl.NumberFormat().format(value || 0);

export default defineComponent({
  name: "AdminUsage",
  setup() {
    const by = ref<BreakdownDimension>("DAY");
    const range = ref<number>(30);
    const items = ref<UsageBreakdownItem[]>([]);
    const loading = ref(false);
    const error = ref("");

    const dimensions = [
      { value: "DAY" as const, label: "按日" },
      { value: "MODEL" as const, label: "按模型" },
      { value: "PROVIDER" as const, label: "按 Provider" },
    ];
    const ranges = [
      { value: 7, label: "近 7 天" },
      { value: 30, label: "近 30 天" },
      { value: 90, label: "近 90 天" },
    ];

    const maxTokens = computed(() =>
      items.value.reduce((m, i) => Math.max(m, i.totalTokens), 0)
    );
    const pct = (tokens: number) =>
      maxTokens.value > 0
        ? Math.max(2, Math.round((tokens / maxTokens.value) * 100))
        : 0;
    const totalRequests = computed(() =>
      items.value.reduce((s, i) => s + i.requestCount, 0)
    );
    const totalTokens = computed(() =>
      items.value.reduce((s, i) => s + i.totalTokens, 0)
    );
    const totalPrompt = computed(() =>
      items.value.reduce((s, i) => s + i.promptTokens, 0)
    );
    const totalCompletion = computed(() =>
      items.value.reduce((s, i) => s + i.completionTokens, 0)
    );

    const label = (key: string) => key;
    const shortLabel = (key: string) =>
      by.value === "DAY"
        ? key.slice(5)
        : key.length > 10
        ? key.slice(0, 9) + "…"
        : key;

    const load = async () => {
      loading.value = true;
      error.value = "";
      try {
        const to = new Date();
        const from = new Date(to.getTime() - range.value * 86400000);
        items.value = await getAdminUsageBreakdown(
          by.value,
          from.toISOString(),
          to.toISOString()
        );
      } catch (e) {
        error.value = e instanceof Error ? e.message : "用量加载失败";
      } finally {
        loading.value = false;
      }
    };

    onMounted(load);
    watch([by, range], load);

    return {
      by,
      range,
      items,
      loading,
      error,
      dimensions,
      ranges,
      maxTokens,
      pct,
      totalRequests,
      totalTokens,
      totalPrompt,
      totalCompletion,
      label,
      shortLabel,
      format,
    };
  },
});
</script>

<style scoped>
.admin-usage {
  display: grid;
  gap: 20px;
}
.usage-controls {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}
.seg {
  display: inline-flex;
  border: 1px solid var(--line);
  border-radius: 9px;
  overflow: hidden;
  background: var(--canvas);
}
.seg-btn {
  border: 0;
  background: transparent;
  color: var(--ink-faint);
  padding: 8px 14px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
}
.seg-btn.active {
  background: var(--blue);
  color: #fff;
}
.usage-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.stat {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--canvas);
  padding: 16px 18px;
}
.stat small {
  display: block;
  color: var(--ink-faint);
  font-size: 0.68rem;
}
.stat strong {
  font-size: 1.25rem;
  color: var(--ink);
}
.chart-card {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--canvas);
  padding: 22px;
  min-height: 260px;
}
.chart-empty {
  color: var(--ink-faint);
  text-align: center;
  padding: 80px 0;
}
.bars {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 240px;
  overflow-x: auto;
}
.bars.wrap {
  flex-wrap: wrap;
  align-items: flex-start;
  height: auto;
}
.bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 34px;
}
.bar-track {
  width: 26px;
  height: 200px;
  display: flex;
  align-items: flex-end;
}
.bar {
  width: 100%;
  border-radius: 5px 5px 0 0;
  background: linear-gradient(180deg, var(--blue), #7fa0ff);
  transition: height 0.3s ease;
}
.bar-label {
  font: 9px/1.2 monospace;
  color: var(--ink-faint);
  max-width: 60px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.usage-error {
  color: #b4232a;
  font-size: 0.8rem;
}
@media (max-width: 760px) {
  .usage-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
