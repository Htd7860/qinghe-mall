<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { ApiError } from "../api/http";
import { allowed } from "../api/management";
import { credentials, identities } from "../api/session";
import {
  seckillClearingProgress,
  seckillIdentity,
  seckillPermission,
  seckillStates,
  type ClearingProgress,
} from "../api/merchant-seckill";

const props = defineProps<{
  activityId: number;
  generation: number;
  disabled: boolean;
  refreshKey: number;
}>();
const result = ref<ClearingProgress | null>(null);
const loading = ref(false),
  error = ref("");
const canRead = computed(() => allowed(seckillPermission + "query"));
let sequence = 0;
const labels: Record<keyof ClearingProgress["pending"], string> = {
  admission: "购买请求待确认",
  requests: "订单处理待确认",
  outbox: "订单消息待送达",
  resultCache: "订单结果待同步",
  refunds: "退款库存待处理",
  poolReturns: "库存归还待确认",
  memberHeld: "会员购买占额待释放",
};
const waiting = computed(() =>
  result.value
    ? (Object.keys(labels) as (keyof typeof labels)[])
        .filter((key) => result.value!.pending[key])
        .map((key) => labels[key])
    : [],
);
const receiptPending = computed(() =>
  result.value?.skus.some((s) => !s.receiptConsistent),
);
const heading = computed(() =>
  !result.value
    ? ""
    : result.value.clearingCompleted
      ? "活动清算已完成"
      : result.value.baselineSaved
        ? "清算记录已保存，待结束核对"
        : result.value.runtimeClosed
          ? "等待保存清算记录"
          : "尚未完成停止接单确认",
);
const time = (value: number) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    dateStyle: "short",
    timeStyle: "medium",
    hour12: false,
  }).format(value);

async function load() {
  if (!canRead.value || props.disabled || loading.value) return;
  const ticket = ++sequence;
  loading.value = true;
  error.value = "";
  result.value = null;
  try {
    const response = await seckillClearingProgress(
      props.activityId,
      props.generation,
    );
    if (ticket !== sequence) return;
    result.value = response;
  } catch (e) {
    if (ticket !== sequence) return;
    error.value = e instanceof Error ? e.message : "清算进度读取失败，请重试";
    if (e instanceof ApiError && e.code === 401)
      window.dispatchEvent(new Event("commerce:unauthorized"));
  } finally {
    if (ticket === sequence) loading.value = false;
  }
}
watch(
  [
    () => props.activityId,
    () => props.generation,
    () => props.disabled,
    () => props.refreshKey,
    seckillIdentity,
    () => credentials().accessToken,
    () => identities.merchant?.permissions.join("|"),
  ],
  () => {
    sequence++;
    result.value = null;
    error.value = "";
    loading.value = false;
    if (canRead.value && !props.disabled) void load();
  },
  { immediate: true, flush: "sync" },
);
onBeforeUnmount(() => {
  sequence++;
});
</script>

<template>
  <section
    class="clearing-progress"
    aria-label="本轮清算进度"
    :aria-busy="loading"
  >
    <div class="clearing-heading">
      <h3>第 {{ generation }} 轮清算</h3>
      <button
        class="button"
        :disabled="loading || disabled || !canRead"
        @click="load"
      >
        {{ loading ? "正在读取…" : "刷新清算进度" }}
      </button>
    </div>
    <p v-if="!canRead" class="muted">当前没有活动查询权限。</p>
    <p v-else-if="disabled" class="muted">当前活动操作完成后将读取清算进度。</p>
    <p v-else-if="loading" role="status">正在读取本轮库存与处理记录…</p>
    <p v-else-if="error" class="form-error" role="alert">{{ error }}</p>
    <template v-else-if="result">
      <p class="clearing-state">{{ heading }}</p>
      <p v-if="result.draftReopened" class="muted">
        本轮已恢复过草稿，下面保留的是本轮历史记录。
      </p>
      <dl class="clearing-facts">
        <div>
          <dt>停止接单确认</dt>
          <dd>{{ result.runtimeClosed ? "已确认" : "待确认" }}</dd>
        </div>
        <div>
          <dt>清算记录</dt>
          <dd>{{ result.baselineSaved ? "已保存" : "尚未保存" }}</dd>
        </div>
        <div>
          <dt>当前活动</dt>
          <dd>
            第 {{ result.currentGeneration }} 轮 ·
            {{ seckillStates[result.currentStatus] }}
          </dd>
        </div>
        <div>
          <dt>查询时间（北京）</dt>
          <dd>{{ time(result.serverTime) }}</dd>
        </div>
      </dl>
      <div class="clearing-waiting">
        <h4>
          {{ result.clearingCompleted ? "后续待处理事项" : "待处理事项" }}
        </h4>
        <ul
          v-if="waiting.length || !result.paidQuotaConsistent || receiptPending"
        >
          <li v-for="item in waiting" :key="item">{{ item }}</li>
          <li v-if="!result.paidQuotaConsistent">会员累计已购数量待核对</li>
          <li v-if="receiptPending">部分规格库存记录待核对</li>
        </ul>
        <p v-else class="muted">当前没有待处理记录，可刷新查看后续进度。</p>
      </div>
      <p class="muted">
        {{
          result.basis === "SEALED_BASELINE"
            ? "本轮清算记录及后续已确认归还"
            : "本轮当前库存"
        }}。
        {{
          result.clearingCompleted
            ? "活动结束后的退款仍会继续记入本轮。"
            : "已售库存会保留，用于处理原订单及后续售后。"
        }}
      </p>
      <div
        class="table-wrap clearing-table"
        role="region"
        aria-label="本轮规格库存明细，可横向滚动"
        tabindex="0"
      >
        <table>
          <caption>
            第
            {{
              generation
            }}
            轮规格库存明细
          </caption>
          <thead>
            <tr>
              <th scope="col">规格</th>
              <th scope="col">已划入</th>
              <th scope="col">可用</th>
              <th scope="col">未付占用</th>
              <th scope="col">剩余已售</th>
              <th scope="col">待归还</th>
              <th scope="col">累计归还</th>
              <th scope="col">清算后售后归还</th>
              <th scope="col">库存核对</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in result.skus" :key="s.skuId">
              <th scope="row">SKU {{ s.skuId }}</th>
              <td>{{ s.allocatedQuantity }}</td>
              <td>{{ s.availableQuantity }}</td>
              <td>{{ s.heldQuantity }}</td>
              <td>{{ s.soldQuantity }}</td>
              <td>{{ s.returnPendingQuantity }}</td>
              <td>{{ s.returnedQuantity }}</td>
              <td>{{ s.historicalReturnedQuantity }}</td>
              <td>{{ s.receiptConsistent ? "一致" : "待核对" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.clearing-progress {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.clearing-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.clearing-heading h3 {
  margin: 0;
}
.clearing-state {
  font-weight: 600;
  margin: 18px 0 12px;
}
.clearing-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin: 0 0 24px;
}
.clearing-facts div {
  min-width: 0;
}
.clearing-facts dt {
  color: var(--muted);
  margin-bottom: 4px;
}
.clearing-facts dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.clearing-waiting h4 {
  margin: 0 0 8px;
}
.clearing-waiting ul {
  padding-left: 20px;
  margin: 0 0 20px;
}
.clearing-waiting li {
  margin: 6px 0;
}
.clearing-table {
  overflow-x: auto;
}
.clearing-table:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}
.clearing-table table {
  min-width: 780px;
  font-variant-numeric: tabular-nums;
}
.clearing-table caption {
  text-align: left;
  margin-bottom: 10px;
}
.clearing-table th {
  white-space: nowrap;
}
@media (max-width: 640px) {
  .clearing-facts {
    grid-template-columns: 1fr;
  }
}
</style>
