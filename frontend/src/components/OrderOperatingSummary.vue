<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { allowed } from "../api/management";
import { credentials } from "../api/session";
import { ApiError } from "../api/http";
import {
  merchantOperatingSummary,
  merchantOrderIdentity,
  operatingMoney,
  type OperatingSummary,
} from "../api/merchant-order";

const canRead = computed(
  () => allowed("trade:order:query") && allowed("trade:after-sale:query"),
);
function today() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const value = (type: string) => parts.find((p) => p.type === type)!.value;
  return `${value("year")}-${value("month")}-${value("day")}`;
}
const end = today();
const form = reactive({
  beginDate: new Date(Date.parse(`${end}T00:00:00Z`) - 6 * 86_400_000)
    .toISOString()
    .slice(0, 10),
  endDate: end,
  status: "",
});
const summary = ref<OperatingSummary | null>(null),
  loading = ref(false),
  error = ref("");
let sequence = 0;
function invalidate() {
  sequence++;
  summary.value = null;
  error.value = "";
  loading.value = false;
}
async function load() {
  if (!canRead.value || loading.value) return;
  const serial = ++sequence;
  loading.value = true;
  error.value = "";
  summary.value = null;
  try {
    const result = await merchantOperatingSummary(
      form.beginDate,
      form.endDate,
      form.status,
    );
    if (serial !== sequence || !canRead.value) return;
    summary.value = result;
  } catch (ex) {
    if (serial !== sequence) return;
    error.value = ex instanceof Error ? ex.message : "经营汇总未加载，请重试";
    if (ex instanceof ApiError && ex.code === 401)
      window.dispatchEvent(new Event("commerce:unauthorized"));
  } finally {
    if (serial === sequence) loading.value = false;
  }
}
watch(
  [merchantOrderIdentity, () => credentials().accessToken, canRead],
  () => {
    invalidate();
    if (canRead.value) void load();
  },
  { immediate: true },
);
watch(() => [form.beginDate, form.endDate, form.status], invalidate);
onBeforeUnmount(invalidate);
const time = (n: number) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    dateStyle: "short",
    timeStyle: "medium",
    hour12: false,
  }).format(n);
</script>

<template>
  <section
    v-if="canRead"
    class="panel operating-summary"
    aria-labelledby="operating-summary-title"
    :aria-busy="loading"
  >
    <h2 id="operating-summary-title">成交与退款汇总</h2>
    <form class="management-filters" @submit.prevent="load">
      <label
        >开始日期<input
          v-model="form.beginDate"
          type="date"
          min="2000-01-01"
          max="2099-12-31"
          required
      /></label>
      <label
        >结束日期（含）<input
          v-model="form.endDate"
          type="date"
          min="2000-01-01"
          max="2099-12-31"
          required
      /></label>
      <label
        >订单当前状态<select v-model="form.status">
          <option value="">全部</option>
          <option value="0">待付款</option>
          <option value="10">待发货</option>
          <option value="20">已发货</option>
          <option value="30">已完成</option>
          <option value="40">已关闭</option>
        </select></label
      >
      <button class="button" :disabled="loading">
        {{ loading ? "正在汇总…" : "查询汇总" }}
      </button>
    </form>
    <p class="summary-note">
      按北京时间统计，汇总范围独立于下方订单号筛选。成交、退款金额均含运费。
    </p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-else-if="loading" role="status">正在读取成交与退款数据…</p>
    <template v-else-if="summary">
      <dl class="summary-values">
        <div>
          <dt>创建订单</dt>
          <dd>{{ summary.createdOrderCount }} 单</dd>
        </div>
        <div>
          <dt>付款成功</dt>
          <dd>{{ summary.paidOrderCount }} 单</dd>
        </div>
        <div>
          <dt>本期成交额</dt>
          <dd>¥{{ operatingMoney(summary.paidAmount) }}</dd>
        </div>
        <div>
          <dt>本期成功退款</dt>
          <dd>¥{{ operatingMoney(summary.refundAmount) }}</dd>
          <small
            >{{ summary.successfulRefundCount }} 笔 · 运费 ¥{{
              operatingMoney(summary.refundDeliveryAmount)
            }}</small
          >
        </div>
        <div>
          <dt>本期资金净流</dt>
          <dd>¥{{ operatingMoney(summary.periodNetAmount) }}</dd>
        </div>
      </dl>
      <p class="summary-note">
        成交按付款成功时间计，退款按退款成功时间计；跨期退款可能使本期净流为负。
      </p>
      <p>
        本期付款订单截至查询时：累计退款 ¥{{
          operatingMoney(summary.paidCohortAccumulatedRefundAmount)
        }}，剩余净额 ¥{{ operatingMoney(summary.paidCohortCurrentNetAmount) }}。
      </p>
      <small
        >查询时间：{{
          time(summary.queriedAt)
        }}。不包含尚未确认的付款或退款结果。</small
      >
    </template>
    <p v-else class="summary-note">筛选条件已修改，请查询汇总。</p>
  </section>
</template>

<style scoped>
.operating-summary {
  margin-bottom: 20px;
}
h2 {
  margin: 0 0 16px;
  font-size: 18px;
}
.summary-values {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 20px;
  padding: 16px 0;
  margin: 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}
.summary-values div {
  min-width: 0;
}
dt,
.summary-note,
small {
  color: var(--text-secondary, #6a7872);
  line-height: 1.7;
}
dd {
  margin: 6px 0 0;
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
p {
  line-height: 1.8;
  overflow-wrap: anywhere;
}
small {
  display: block;
}
@media (max-width: 600px) {
  .management-filters label {
    width: 100%;
  }
  .management-filters input,
  .management-filters select {
    width: 100%;
    min-height: 44px;
  }
}
</style>
