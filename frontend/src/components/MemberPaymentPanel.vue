<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import QRCode from "qrcode";
import Modal from "./Modal.vue";
import { ApiError } from "../api/http";
import { context } from "../api/session";
import { cartIdentity } from "../api/member-cart";
import { pendingOrderCancellation } from "../api/member-order";
import {
  readMemberPayment,
  queryMemberPayment,
  submitMemberPayment,
  recoverMemberPaymentQr,
  pendingMemberPayment,
  type MemberPayment,
} from "../api/member-payment";

const route = useRoute(),
  base = computed(() => `/shop/${context.tenant}`);
const value = ref<MemberPayment | null>(null),
  busy = ref(false),
  error = ref("");
const confirming = ref<"submit" | "recover" | null>(null),
  pending = ref(false),
  storageError = ref(""),
  cancelling = ref(false);
const qrImage = ref(""),
  qrError = ref(""),
  clock = ref(performance.now());
let epoch = 0,
  reads = 0,
  qrReads = 0,
  receivedAt = performance.now();
const id = computed(() => {
  const raw = String(route.params.id || "");
  return /^[1-9][0-9]*$/.test(raw) && Number.isSafeInteger(Number(raw))
    ? Number(raw)
    : null;
});
const serverNow = computed(
  () =>
    (value.value?.order.serverNow || 0) + Math.max(0, clock.value - receivedAt),
);
const expired = computed(
  () => !!value.value && serverNow.value >= value.value.order.payDeadline,
);
const canStart = computed(
  () =>
    !!value.value?.canSubmit &&
    value.value.paymentState === "NOT_SUBMITTED" &&
    !expired.value &&
    !busy.value &&
    !storageError.value &&
    !cancelling.value,
);
const canRecover = computed(
  () =>
    !!value.value?.canRecoverQr &&
    value.value.qrRecoveryAfter !== null &&
    serverNow.value >= value.value.qrRecoveryAfter &&
    !expired.value &&
    !busy.value &&
    !storageError.value &&
    !cancelling.value,
);
const showQr = computed(
  () =>
    !!value.value?.qrCode &&
    value.value.paymentState === "WAITING" &&
    value.value.canSubmit &&
    !expired.value &&
    !busy.value &&
    !error.value &&
    !cancelling.value,
);
const money = (cents: number) =>
  `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, "0")}`;
const time = (stamp: number) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(stamp);
const statusText = computed(() => {
  const p = value.value;
  if (!p) return "正在读取原订单付款状态…";
  if (p.paymentState === "REVIEW") return "付款需要核查，请勿重复付款。";
  if (p.order.payStatus) return "订单已确认付款成功。";
  if (p.paymentState === "SUCCESS")
    return "渠道已确认付款，订单状态仍待同步，请查询付款结果。";
  if (p.paymentState === "CLOSED") return "支付单已关闭，请查看订单取消进度。";
  if (p.paymentState === "CLOSING") return "付款入口正在关闭，请查询确认结果。";
  if (expired.value) return "已到付款截止时间，请查询订单最新状态。";
  if (cancelling.value) return "原订单的取消结果待确认，请先查看订单。";
  if (p.paymentState === "SUBMITTING" || pending.value)
    return "原付款请求结果待确认，请查询同一订单。";
  if (error.value && p.qrCode) return "本次查询未完成，请先刷新原付款记录。";
  if (p.qrCode) return "请使用支付宝扫码付款，完成后点击查询付款结果。";
  if (p.paymentState === "WAITING")
    return "付款二维码尚未返回，请查询原付款结果。";
  if (!p.canSubmit) return "当前暂不能继续发起付款，已有付款仍可查询。";
  if (p.order.payPrice === 0) return "原订单金额为零，确认后无需扫码扣款。";
  return "确认订单金额后，可发起支付宝扫码付款。";
});
function localState() {
  if (id.value === null) return;
  try {
    pending.value = pendingMemberPayment(id.value);
    cancelling.value = pendingOrderCancellation() === id.value;
    storageError.value = "";
  } catch (e) {
    storageError.value = e instanceof Error ? e.message : "无法读取付款记录";
  }
}
async function run(mode: "read" | "submit" | "query" | "recover") {
  if (id.value === null) {
    error.value = "订单编号不正确，请返回我的订单";
    return;
  }
  if (busy.value) return;
  const ticket = epoch,
    serial = ++reads,
    orderId = id.value;
  busy.value = true;
  error.value = "";
  qrImage.value = "";
  try {
    const result = await (mode === "read"
      ? readMemberPayment(orderId)
      : mode === "query"
        ? queryMemberPayment(orderId)
        : mode === "recover"
          ? recoverMemberPaymentQr(orderId)
          : submitMemberPayment(orderId));
    if (ticket !== epoch || serial !== reads) return;
    receivedAt = performance.now();
    clock.value = receivedAt;
    value.value = result;
  } catch (e) {
    if (ticket !== epoch || serial !== reads) return;
    error.value =
      e instanceof Error ? e.message : "付款结果待确认，请查询原订单";
    if (e instanceof ApiError && e.code === 401)
      window.dispatchEvent(new Event("commerce:unauthorized"));
    else if (mode === "recover") {
      // Only a stored-result GET after an uncertain response, never another POST.
      try {
        const result = await readMemberPayment(orderId);
        if (ticket !== epoch || serial !== reads) return;
        receivedAt = performance.now();
        clock.value = receivedAt;
        value.value = result;
        if (
          result.qrCode !== null ||
          ["SUCCESS", "CLOSED", "REVIEW"].includes(result.paymentState)
        )
          error.value = "";
      } catch {
        /* Retain the original recovery error and original request metadata. */
      }
    }
  } finally {
    if (ticket === epoch && serial === reads) {
      busy.value = false;
      localState();
    }
  }
}
function start() {
  const mode = confirming.value;
  if (
    mode === null ||
    (mode === "recover" ? !canRecover.value : !canStart.value)
  ) {
    confirming.value = null;
    return;
  }
  confirming.value = null;
  void run(mode);
}
// Selected from upstream Qrcode.initQrcode: the established qrcode renderer,
// with no logo/download/network encoder or raw channel HTML rendering.
watch([showQr, () => value.value?.qrCode], async () => {
  const ticket = epoch,
    serial = ++qrReads;
  qrImage.value = "";
  qrError.value = "";
  if (!showQr.value || !value.value?.qrCode) return;
  try {
    const image = await QRCode.toDataURL(value.value.qrCode, {
      width: 256,
      margin: 4,
      errorCorrectionLevel: "M",
    });
    if (ticket === epoch && serial === qrReads) qrImage.value = image;
  } catch {
    if (ticket === epoch && serial === qrReads)
      qrError.value = "二维码显示失败，请刷新读取原二维码。";
  }
});
watch(
  [cartIdentity, () => route.params.id],
  () => {
    epoch++;
    reads++;
    qrReads++;
    value.value = null;
    qrImage.value = "";
    qrError.value = "";
    error.value = "";
    busy.value = false;
    confirming.value = null;
    pending.value = false;
    cancelling.value = false;
    localState();
    void run("read");
  },
  { immediate: true },
);
const timer = window.setInterval(() => {
  clock.value = performance.now();
}, 1000);
onBeforeUnmount(() => {
  epoch++;
  reads++;
  qrReads++;
  window.clearInterval(timer);
  value.value = null;
  qrImage.value = "";
  confirming.value = null;
});
</script>

<template>
  <main class="shop-container member-payment" :aria-busy="busy">
    <div class="shop-page-heading">
      <h1>订单付款</h1>
      <RouterLink
        class="text-link"
        :to="id === null ? `${base}/orders` : `${base}/order/${id}`"
        >返回订单</RouterLink
      >
    </div>
    <section class="consumer-order payment-content" aria-label="原订单付款">
      <template v-if="value">
        <p class="payment-order-no">订单 {{ value.order.no }}</p>
        <p class="payment-amount">
          {{ value.order.payStatus ? "实付" : "订单金额" }}
          <strong>¥{{ money(value.order.payPrice) }}</strong>
        </p>
        <p>
          共 {{ value.order.productCount }} 件 · 含运费 ¥{{
            money(value.order.deliveryPrice)
          }}
        </p>
        <p v-if="!value.order.payStatus">
          付款截止 {{ time(value.order.payDeadline) }}（北京时间）
        </p>
      </template>
      <p role="status" class="payment-state">{{ statusText }}</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="storageError" class="form-error" role="alert">
        {{ storageError }}
      </p>
      <p v-if="qrError" class="form-error" role="alert">{{ qrError }}</p>
      <p
        v-if="
          value &&
          !value.qrCode &&
          ['SUBMITTING', 'WAITING'].includes(value.paymentState)
        "
      >
        {{
          value.qrRecoveryCount >= 3
            ? "二维码恢复次数已用完，仍可查询付款结果。"
            : "可恢复原订单的付款二维码，金额和付款截止时间保持不变。"
        }}
        <template
          v-if="
            value.qrRecoveryAfter !== null &&
            serverNow < value.qrRecoveryAfter &&
            value.qrRecoveryCount < 3
          "
        >
          请在 {{ time(value.qrRecoveryAfter) }}（北京时间）后再试。
        </template>
      </p>
      <img
        v-if="showQr && qrImage"
        :src="qrImage"
        width="256"
        height="256"
        alt="当前原订单的支付宝付款二维码"
        class="payment-qr"
      />
      <p v-else-if="showQr && !qrError" role="status">正在显示原付款二维码…</p>
      <div class="payment-actions">
        <button
          class="button"
          :disabled="busy || !!confirming"
          @click="run('read')"
        >
          {{ busy ? "正在处理…" : "刷新原付款记录" }}
        </button>
        <button
          class="button"
          :disabled="busy || !!confirming || id === null"
          @click="run('query')"
        >
          查询付款结果
        </button>
        <button
          v-if="
            value &&
            value.paymentState === 'NOT_SUBMITTED' &&
            !value.order.payStatus &&
            !value.qrCode
          "
          class="button primary"
          :disabled="!canStart || !!confirming"
          @click="confirming = 'submit'"
        >
          {{
            pending
              ? "继续原付款请求"
              : value.order.payPrice === 0
                ? "确认零元订单"
                : "支付宝扫码付款"
          }}
        </button>
        <button
          v-if="
            value &&
            !value.qrCode &&
            ['SUBMITTING', 'WAITING'].includes(value.paymentState)
          "
          class="button primary"
          :disabled="!canRecover || !!confirming"
          @click="confirming = 'recover'"
        >
          恢复原付款二维码
        </button>
        <RouterLink
          v-if="value?.order.payStatus"
          class="button primary"
          :to="`${base}/order/${value.order.id}`"
          >查看已付订单</RouterLink
        >
      </div>
    </section>
    <Modal
      v-if="confirming && value"
      :title="
        confirming === 'recover'
          ? '恢复原付款二维码'
          : value.order.payPrice === 0
            ? '确认零元订单'
            : '确认扫码付款'
      "
      @close="confirming = null"
    >
      <p class="payment-order-no">订单 {{ value.order.no }}</p>
      <p>
        应付 ¥{{ money(value.order.payPrice) }}，共
        {{ value.order.productCount }} 件。
      </p>
      <p>
        付款截止
        {{ time(value.order.payDeadline) }}（北京时间）。将继续处理这一订单。
      </p>
      <template #footer
        ><button class="button" @click="confirming = null">返回</button
        ><button
          class="button primary"
          :disabled="confirming === 'recover' ? !canRecover : !canStart"
          @click="start"
        >
          {{
            confirming === "recover"
              ? "确认恢复原付款二维码"
              : value.order.payPrice === 0
                ? "确认零元订单"
                : "确认发起扫码付款"
          }}
        </button></template
      >
    </Modal>
  </main>
</template>

<style scoped>
.member-payment {
  padding-bottom: 40px;
}
.shop-page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.payment-content {
  padding: 28px;
  line-height: 1.9;
}
.payment-order-no {
  overflow-wrap: anywhere;
  color: #6a7872;
}
.payment-amount strong {
  font-size: 30px;
  color: #17634c;
  font-variant-numeric: tabular-nums;
}
.payment-state {
  font-weight: 600;
  margin: 20px 0;
}
.payment-qr {
  display: block;
  width: 256px;
  max-width: 100%;
  height: auto;
  background: #fff;
}
.payment-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}
.button {
  min-height: 44px;
}
@media (max-width: 600px) {
  .payment-content {
    padding: 18px;
  }
}
</style>
