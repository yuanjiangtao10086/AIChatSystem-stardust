<template>
  <section
    class="usage-panel"
    :class="{ compact }"
    aria-labelledby="usage-title"
  >
    <div class="usage-head">
      <span class="orbit" aria-hidden="true">✦</span>
      <div>
        <p>AI 用量</p>
        <strong id="usage-title">{{ used }} / {{ quota }} tokens</strong>
      </div>
    </div>
    <div
      class="track"
      role="meter"
      :aria-valuenow="percent"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <span :style="{ width: `${percent}%` }"></span>
    </div>
    <small v-if="!compact">
      已用 {{ format(usage?.usedTokens) }} · 生成中预留
      {{ format(usage?.reservedTokens) }} · 剩余
      {{ format(usage?.availableTokens) }}<br />费用 {{ cost }} · 周期至
      {{ periodEnd }}
    </small>
    <small v-else
      >剩余 {{ format(usage?.availableTokens) }} · {{ percent }}%</small
    >
    <div
      v-if="!compact && trend.length"
      class="usage-trend"
      aria-label="近 14 天用量趋势"
    >
      <span
        v-for="(item, i) in trend"
        :key="i"
        class="trend-bar"
        :style="{ height: trendPct(item.totalTokens) + '%' }"
        :title="`${item.key} · ${format(item.totalTokens)} tokens`"
      ></span>
    </div>
    <p v-if="error" class="usage-error">{{ error }}</p>
  </section>
</template>
<script lang="ts">
import { computed, defineComponent, onMounted, ref } from "vue";
import { getUsage, getUsageBreakdown } from "@/api/usage";
import { UsageBreakdownItem, UsageView } from "@/types/usage";

const format = (value?: number): string =>
  new Intl.NumberFormat().format(value || 0);

export default defineComponent({
  name: "UsagePanel",
  props: { compact: Boolean },
  setup(props) {
    const usage = ref<UsageView | null>(null);
    const error = ref("");
    const trend = ref<UsageBreakdownItem[]>([]);
    const load = async () => {
      try {
        usage.value = await getUsage();
        error.value = "";
        if (!props.compact) {
          const to = new Date();
          const from = new Date(to.getTime() - 14 * 86400000);
          trend.value = await getUsageBreakdown(
            "DAY",
            from.toISOString(),
            to.toISOString()
          );
        }
      } catch (e) {
        error.value = e instanceof Error ? e.message : "用量加载失败";
      }
    };
    const percent = computed(() =>
      usage.value?.quotaTokens
        ? Math.min(
            100,
            Math.round(
              ((usage.value.usedTokens + usage.value.reservedTokens) /
                usage.value.quotaTokens) *
                100
            )
          )
        : 0
    );
    const used = computed(() =>
      format(
        (usage.value?.usedTokens || 0) + (usage.value?.reservedTokens || 0)
      )
    );
    const quota = computed(() => format(usage.value?.quotaTokens));
    const cost = computed(() =>
      usage.value
        ? `${Number(usage.value.usedCost).toFixed(4)} ${usage.value.currency}`
        : "-"
    );
    const periodEnd = computed(() =>
      usage.value
        ? new Date(usage.value.periodEnd).toLocaleDateString("zh-CN")
        : "-"
    );
    const trendMax = computed(() =>
      trend.value.reduce((m, i) => Math.max(m, i.totalTokens), 0)
    );
    const trendPct = (tokens: number) =>
      trendMax.value > 0
        ? Math.max(6, Math.round((tokens / trendMax.value) * 100))
        : 0;
    onMounted(load);
    return {
      usage,
      error,
      trend,
      percent,
      used,
      quota,
      cost,
      periodEnd,
      trendPct,
      format,
    };
  },
});
</script>
<style scoped>
.usage-panel {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--canvas);
}
.usage-panel.compact {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.usage-head {
  display: flex;
  gap: 11px;
  align-items: center;
}
.orbit {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #fff;
  background: var(--ink);
  font-size: 1rem;
}
.compact .orbit {
  width: 26px;
  height: 26px;
  font-size: 0.75rem;
}
p,
strong,
small {
  display: block;
  margin: 0;
}
.usage-head p {
  color: var(--ink-faint);
  font-size: 0.68rem;
}
.compact .usage-head p {
  display: none;
}
strong {
  margin-top: 3px;
  color: var(--ink);
  font-size: 0.9rem;
}
.compact strong {
  margin-top: 0;
  font-size: 0.76rem;
}
.track {
  height: 7px;
  margin: 18px 0 9px;
  overflow: hidden;
  border-radius: 9px;
  background: var(--line);
}
.compact .track {
  margin: 8px 0 6px;
}
.track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--ink);
  transition: width 0.3s ease;
}
small {
  color: var(--ink-faint);
  font-size: 0.66rem;
  line-height: 1.6;
}
.usage-error {
  margin-top: 8px;
  color: #b4232a;
  font-size: 0.66rem;
}
.usage-trend {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 38px;
  margin-top: 12px;
}
.trend-bar {
  flex: 1;
  min-width: 2px;
  border-radius: 2px 2px 0 0;
  background: var(--blue, #4f7cff);
  opacity: 0.65;
}
.trend-bar:hover {
  opacity: 1;
}
@media (prefers-reduced-motion: reduce) {
  .track span {
    transition: none;
  }
}
</style>
