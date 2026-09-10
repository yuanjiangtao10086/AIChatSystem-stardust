<template>
  <section class="storage-meter" aria-labelledby="storage-title">
    <div>
      <span class="orbit">◒</span>
      <div>
        <p>存储空间</p>
        <strong id="storage-title">{{ used }} / {{ quota }}</strong>
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
    <small>共 {{ usage?.fileCount || 0 }} 个文件 · 已使用 {{ percent }}%</small>
  </section>
</template>
<script lang="ts">
import { computed, defineComponent, PropType } from "vue";
import { formatBytes } from "@/api/files";
import { StorageUsage } from "@/types/file";
export default defineComponent({
  name: "StorageMeter",
  props: { usage: Object as PropType<StorageUsage | null> },
  setup(props) {
    const percent = computed(() =>
      props.usage?.quotaBytes
        ? Math.min(
            100,
            Math.round(
              ((props.usage.usedBytes + props.usage.reservedBytes) /
                props.usage.quotaBytes) *
                100
            )
          )
        : 0
    );
    const used = computed(() =>
      formatBytes(
        (props.usage?.usedBytes || 0) + (props.usage?.reservedBytes || 0)
      )
    );
    const quota = computed(() => formatBytes(props.usage?.quotaBytes || 0));
    return { percent, used, quota };
  },
});
</script>
<style scoped>
.storage-meter {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--canvas);
}
.storage-meter > div:first-child {
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
p,
strong,
small {
  display: block;
  margin: 0;
}
p {
  color: var(--ink-faint);
  font-size: 0.68rem;
}
strong {
  margin-top: 3px;
  color: var(--ink);
  font-size: 0.9rem;
}
.track {
  height: 7px;
  margin: 18px 0 9px;
  overflow: hidden;
  border-radius: 9px;
  background: var(--line);
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
}
@media (prefers-reduced-motion: reduce) {
  .track span {
    transition: none;
  }
}
</style>
