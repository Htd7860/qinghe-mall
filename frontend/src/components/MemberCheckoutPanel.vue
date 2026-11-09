<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import Empty from "./Empty.vue";
import Modal from "./Modal.vue";
import { ApiError } from "../api/http";
import { context } from "../api/session";
import {
  cartIdentity,
  readMemberCart,
  type MemberCart,
} from "../api/member-cart";
import { listMemberAddresses, type MemberAddress } from "../api/member-profile";
import { memberCouponPage, type MemberCoupon } from "../api/member-coupon";
import {
  createSettlement,
  getSettlement,
  newSettlementInput,
  lastSettlementInput,
  type Settlement,
  type SettlementInput,
} from "../api/member-checkout";
import {
  createMemberOrder,
  getOrderCreation,
  lastOrderAttempt,
  newOrderAttempt,
  stopOrderCreation,
  type OrderAttempt,
  type OrderCreation,
} from "../api/member-order";
const base = computed(() => `/shop/${context.tenant}`),
  cart = ref<MemberCart | null>(null),
  addresses = ref<MemberAddress[]>([]),
  addressId = ref<number | null>(null),
  couponId = ref<number | null>(null);
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  quote = ref<Settlement | null>(null),
  attempt = ref<SettlementInput | null>(null);
const orderAttempt = ref<OrderAttempt | null>(null),
  orderResult = ref<OrderCreation | null>(null),
  orderNotice = ref(""),
  storageBlocked = ref(false),
  orderConfirm = ref<"new" | "resume" | "stop" | null>(null);
const orderPending = computed(() => orderAttempt.value?.outcome === "pending");
const couponDialog = ref(false),
  couponRows = ref<MemberCoupon[]>([]),
  couponPage = ref(1),
  couponTotal = ref(0),
  couponLoading = ref(false),
  couponError = ref(""),
  couponName = ref("");
const selected = computed(
    () => cart.value?.lines.filter((x) => x.selected) || [],
  ),
  clock = ref(Date.now()),
  offset = ref(0);
const canQuote = computed(
  () =>
    !loading.value &&
    !busy.value &&
    !orderPending.value &&
    !storageBlocked.value &&
    !!cart.value?.canPurchase &&
    !!addressId.value &&
    selected.value.length > 0 &&
    selected.value.length <= (cart.value?.maxOrderLines || 0) &&
    selected.value.every((x) => x.valid),
);
const quoteValid = computed(
  () =>
    !!quote.value &&
    quote.value.snapshotPresent &&
    !quote.value.expired &&
    !quote.value.cartChanged &&
    !quote.value.bound &&
    quote.value.canPurchase &&
    clock.value + offset.value < quote.value.expiresAt &&
    quote.value.cartVersion === cart.value?.version &&
    quote.value.addressId === addressId.value &&
    quote.value.couponId === couponId.value &&
    addresses.value.some(
      (x) =>
        x.id === quote.value?.addressId &&
        x.authVersion === quote.value?.address?.authVersion,
    ),
);
const canSubmit = computed(
  () =>
    quoteValid.value &&
    !loading.value &&
    !busy.value &&
    !storageBlocked.value &&
    !orderPending.value &&
    orderAttempt.value?.quoteId !== quote.value?.quoteId,
);
let epoch = 0,
  reads = 0,
  couponReads = 0;
const timer = setInterval(() => {
  clock.value = Date.now();
}, 1000);
function message(e: unknown) {
  return e instanceof ApiError && e.code === 409
    ? "购物车、地址或优惠券状态已变化，请刷新后重新报价。"
    : e instanceof Error
      ? e.message
      : "报价暂不可用，请稍后重试";
}
function fail(e: unknown) {
  error.value = message(e);
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
function money(cents: number) {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, "0")}`;
}
function time(value: number) {
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(value);
}
function invalidate() {
  quote.value = null;
  notice.value = "选项已变化，请重新计算金额。";
}
function apply(value: Settlement) {
  quote.value = value;
  offset.value = value.serverNow - Date.now();
  clock.value = Date.now();
  if (addresses.value.some((x) => x.id === value.addressId))
    addressId.value = value.addressId;
  couponId.value = value.couponId;
  couponName.value = value.couponName || "";
}
function applyOrder(value: OrderCreation | null) {
  orderResult.value = value;
  orderAttempt.value = lastOrderAttempt();
  if (!value) {
    orderNotice.value =
      orderAttempt.value?.outcome === "stopped"
        ? "这次未受理的下单请求已结束，可重新报价。"
        : "暂未查到原订单，仍可能在处理。请稍后查询，或继续同一请求。";
    return;
  }
  if (value.stage === "CREATED") {
    orderNotice.value = value.order?.payStatus
      ? value.order.payPrice === 0
        ? "零元订单已确认，请查看后续履约进度。"
        : "订单已付款。"
      : value.order?.status === 40
        ? "原订单已取消。"
        : "订单已创建，尚未付款。";
    if (quote.value?.quoteId === orderAttempt.value?.quoteId) {
      quote.value!.bound = true;
      cart.value = null;
    }
  } else if (value.stage === "FAILED") {
    orderNotice.value =
      "原订单未创建，资源处理已结束。可刷新购物袋后重新报价。";
  } else if (value.stage === "COMPENSATING" || value.stage === "MANUAL") {
    orderNotice.value =
      "原订单正在核查或退回占用，请查询处理结果，暂勿重复下单。";
  } else {
    orderNotice.value =
      "原订单仍在处理中，请查询结果或继续同一请求，暂勿另下新单。";
  }
}
async function queryOrder() {
  if (!orderAttempt.value || loading.value || busy.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  try {
    const value = await getOrderCreation(orderAttempt.value);
    if (ticket === epoch) applyOrder(value);
  } catch (e) {
    if (ticket === epoch) {
      error.value =
        e instanceof Error ? e.message : "原订单查询失败，请稍后再查";
      if (e instanceof ApiError && e.code === 401)
        window.dispatchEvent(new Event("commerce:unauthorized"));
    }
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
function confirmNewOrder() {
  if (canSubmit.value) orderConfirm.value = "new";
}
async function submitOrder() {
  const mode = orderConfirm.value;
  if (!mode || busy.value || loading.value || storageBlocked.value) return;
  if (mode === "new" && !canSubmit.value) {
    orderConfirm.value = null;
    error.value = "报价或选项已变化，请重新确认金额。";
    return;
  }
  if (mode === "resume" && (!orderPending.value || !orderAttempt.value)) return;
  if (mode === "stop" && (!orderPending.value || !orderAttempt.value)) return;
  const ticket = epoch;
  busy.value = true;
  orderConfirm.value = null;
  error.value = "";
  try {
    if (mode === "stop") {
      const value = await stopOrderCreation(orderAttempt.value!);
      if (ticket !== epoch) return;
      applyOrder(value.result);
      if (value.stopped) quote.value = null;
      return;
    }
    const original =
      mode === "new" ? newOrderAttempt(quote.value!) : orderAttempt.value!;
    orderAttempt.value = original;
    orderNotice.value = "正在创建原订单，请勿重复提交。";
    const value = await createMemberOrder(original);
    if (ticket === epoch) applyOrder(value);
  } catch (e) {
    if (ticket !== epoch) return;
    // 所有后续步骤的拒绝都可能发生在准备或主单提交之后，先查原请求，不擅自清除它。
    error.value = e instanceof Error ? e.message : "创建结果待确认";
    orderNotice.value =
      "请先查询原订单结果。需要继续时仍使用同一请求，不会另建新单。";
    try {
      orderAttempt.value = lastOrderAttempt();
    } catch {
      storageBlocked.value = true;
    }
    if (e instanceof ApiError && e.code === 401)
      window.dispatchEvent(new Event("commerce:unauthorized"));
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function load() {
  if (busy.value || loading.value) return;
  const ticket = epoch,
    serial = ++reads;
  loading.value = true;
  error.value = "";
  quote.value = null;
  cart.value = null;
  addresses.value = [];
  orderConfirm.value = null;
  try {
    orderAttempt.value = lastOrderAttempt();
    storageBlocked.value = false;
  } catch (e) {
    storageBlocked.value = true;
    orderNotice.value = e instanceof Error ? e.message : "请先核对原订单记录";
  }
  // 恢复原订单与当前购物袋分开读取；创建后购物车可能已清空，券也可能已经核销。
  if (orderAttempt.value) {
    try {
      const value = await getOrderCreation(orderAttempt.value);
      if (ticket !== epoch || serial !== reads) return;
      applyOrder(value);
    } catch (e) {
      if (ticket !== epoch || serial !== reads) return;
      orderNotice.value =
        e instanceof Error ? e.message : "原订单查询失败，请稍后再查";
      if (e instanceof ApiError && e.code === 401)
        window.dispatchEvent(new Event("commerce:unauthorized"));
    }
  }
  try {
    const [c, a] = await Promise.all([readMemberCart(), listMemberAddresses()]);
    if (ticket !== epoch || serial !== reads) return;
    cart.value = c;
    addresses.value = a;
    addressId.value = a.find((x) => x.defaultStatus)?.id || a[0]?.id || null;
    try {
      attempt.value = lastSettlementInput();
    } catch (e) {
      notice.value = message(e);
      attempt.value = null;
    }
    if (attempt.value) {
      const data = await getSettlement(attempt.value);
      if (ticket !== epoch || serial !== reads) return;
      if (data) apply(data);
      else notice.value = "上次报价暂未查到成功记录，可稍后查询或重新报价。";
    }
  } catch (e) {
    if (ticket === epoch && serial === reads) fail(e);
  } finally {
    if (ticket === epoch && serial === reads) loading.value = false;
  }
}
watch(
  cartIdentity,
  () => {
    epoch++;
    reads++;
    couponReads++;
    busy.value = false;
    cart.value = null;
    addresses.value = [];
    quote.value = null;
    attempt.value = null;
    orderAttempt.value = null;
    orderResult.value = null;
    orderConfirm.value = null;
    orderNotice.value = "";
    storageBlocked.value = false;
    loading.value = false;
    couponRows.value = [];
    couponDialog.value = false;
    couponId.value = null;
    couponName.value = "";
    notice.value = "";
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  epoch++;
  reads++;
  couponReads++;
  clearInterval(timer);
  cart.value = null;
  addresses.value = [];
  quote.value = null;
  orderResult.value = null;
  orderConfirm.value = null;
  couponRows.value = [];
});
async function calculate() {
  if (!canQuote.value || !cart.value || !addressId.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  quote.value = null;
  try {
    const input = newSettlementInput(
      cart.value.version,
      addressId.value,
      couponId.value,
    );
    attempt.value = input;
    const data = await createSettlement(input);
    if (ticket !== epoch) return;
    apply(data);
    notice.value = "报价已生成，请核对商品、地址和金额。";
  } catch (e) {
    if (ticket !== epoch) return;
    fail(e);
    notice.value =
      e instanceof ApiError && e.outcomeUnknown
        ? "报价结果待确认，可查询上次报价。重新报价也不会创建订单或扣款。"
        : "未生成新报价，请检查选项后重试。";
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function recover() {
  if (
    !attempt.value ||
    busy.value ||
    loading.value ||
    orderPending.value ||
    storageBlocked.value
  )
    return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const data = await getSettlement(attempt.value);
    if (ticket !== epoch) return;
    if (data) {
      apply(data);
      notice.value = "已读取上次报价，请核对有效期和明细。";
    } else
      notice.value =
        "暂未查到成功报价，原请求仍可能在处理。可稍后再查或重新报价。";
  } catch (e) {
    if (ticket === epoch) fail(e);
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function readCoupons() {
  const ticket = epoch,
    serial = ++couponReads;
  couponLoading.value = true;
  couponError.value = "";
  couponRows.value = [];
  try {
    const value = await memberCouponPage(couponPage.value, 1);
    if (ticket !== epoch || serial !== couponReads) return;
    couponRows.value = value.list;
    couponTotal.value = value.total;
  } catch (e) {
    if (ticket === epoch && serial === couponReads)
      couponError.value = message(e);
  } finally {
    if (ticket === epoch && serial === couponReads) couponLoading.value = false;
  }
}
function openCoupons() {
  couponDialog.value = true;
  couponPage.value = 1;
  void readCoupons();
}
function chooseCoupon(row: MemberCoupon | null) {
  couponId.value = row?.id || null;
  couponName.value = row?.name || "";
  couponDialog.value = false;
  couponReads++;
  invalidate();
}
function nextCoupon(delta: number) {
  if (couponLoading.value) return;
  couponPage.value += delta;
  void readCoupons();
}
</script>
<template>
  <main class="shop-container checkout-real">
    <div class="shop-page-heading inline">
      <h1>确认商品与金额</h1>
      <RouterLink class="text-link" :to="`${base}/bag`">返回购物袋</RouterLink>
    </div>
    <section
      v-if="orderAttempt || storageBlocked"
      class="checkout-order-result"
      aria-label="上次下单结果"
    >
      <h2>
        {{ orderResult?.stage === "CREATED" ? "原订单结果" : "确认上次下单" }}
      </h2>
      <p role="status">
        {{ orderNotice || "请查询上次下单结果，确认后再继续购买。" }}
      </p>
      <template v-if="orderResult?.order">
        <p>
          订单号：<span class="checkout-order-number">{{
            orderResult.order.no
          }}</span>
        </p>
        <p>
          订单金额 ¥{{ money(orderResult.order.payPrice) }} ·
          {{ orderResult.order.productCount }} 件
        </p>
        <p v-if="orderResult.order.status === 40">
          {{
            orderResult.order.cancelResourcesReleased
              ? "订单已取消，库存和优惠券处理已完成。"
              : "订单已取消，库存和优惠券仍在处理中。"
          }}
        </p>
        <p v-else-if="orderResult.order.requiresPayment">
          付款截止
          {{
            time(orderResult.order.payDeadline)
          }}（北京时间）。请进入原订单付款页继续。
        </p>
      </template>
      <div class="checkout-actions">
        <RouterLink
          v-if="
            orderResult?.order?.status === 0 && !orderResult.order.payStatus
          "
          class="button primary"
          :to="`${base}/payment/${orderResult.order.id}`"
          >{{
            orderResult.order.payPrice === 0 ? "确认订单" : "去付款"
          }}</RouterLink
        >
        <RouterLink
          v-if="orderResult?.order"
          class="button"
          :to="`${base}/order/${orderResult.order.id}`"
          >查看订单详情</RouterLink
        >
        <RouterLink class="button" :to="`${base}/orders`">我的订单</RouterLink>
        <button
          v-if="orderAttempt"
          class="button"
          :disabled="busy || loading"
          @click="queryOrder"
        >
          {{ busy ? "正在处理…" : "查询原订单结果" }}
        </button>
        <button
          v-if="
            orderPending &&
            (!orderResult ||
              ['PREPARED', 'PROCESSING'].includes(orderResult.stage))
          "
          class="button"
          :disabled="busy || loading || storageBlocked"
          @click="orderConfirm = 'resume'"
        >
          继续原订单
        </button>
        <button
          v-if="orderPending && !orderResult"
          class="button"
          :disabled="busy || loading || storageBlocked"
          @click="orderConfirm = 'stop'"
        >
          结束这次未受理请求
        </button>
      </div>
    </section>
    <p v-if="notice" class="checkout-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <div class="checkout-actions">
      <button class="button" :disabled="loading || busy" @click="load">
        刷新购物车与地址</button
      ><button
        v-if="attempt"
        class="button"
        :disabled="loading || busy || orderPending || storageBlocked"
        @click="recover"
      >
        查询上次报价
      </button>
    </div>
    <Empty v-if="loading" title="正在读取结算资料…" icon="refresh" />
    <template v-else-if="cart">
      <p v-if="!cart.canPurchase" class="form-error">店铺暂不支持新购买。</p>
      <Empty v-if="!selected.length" title="请先在购物袋选择商品" icon="bag"
        ><RouterLink class="button primary" :to="`${base}/bag`"
          >前往购物袋</RouterLink
        ></Empty
      >
      <div v-else class="shopping-layout">
        <section>
          <div class="checkout-block">
            <div class="shop-section-heading">
              <h2>收货地址</h2>
              <RouterLink class="text-link" :to="`${base}/addresses`"
                >管理地址</RouterLink
              >
            </div>
            <label
              v-for="address in addresses"
              :key="address.id"
              class="address-option"
              :class="{ selected: addressId === address.id }"
              ><input
                v-model="addressId"
                :value="address.id"
                type="radio"
                name="settlement-address"
                :disabled="busy || orderPending || storageBlocked"
                @change="invalidate"
              />
              <span
                ><strong>{{ address.name }} · {{ address.mobile }}</strong
                ><small
                  >{{ address.areaName }} {{ address.detailAddress }}</small
                ></span
              ></label
            >
            <Empty v-if="!addresses.length" title="请先添加收货地址" icon="pin"
              ><RouterLink class="button" :to="`${base}/addresses`"
                >添加地址</RouterLink
              ></Empty
            >
          </div>
          <div class="checkout-block">
            <div class="shop-section-heading">
              <h2>商品明细</h2>
              <span>{{ selected.length }} 种商品</span>
            </div>
            <article
              v-for="line in selected"
              :key="line.id"
              class="checkout-product"
            >
              <div>
                <strong>{{ line.product?.name || "商品或规格已失效" }}</strong>
                <p v-if="line.product">
                  {{
                    line.product.properties
                      .map((x) => `${x.propertyName}：${x.valueName}`)
                      .join(" / ") || "默认规格"
                  }}
                </p>
                <p v-if="!line.valid" class="form-error">
                  {{ line.invalidReason }}
                </p>
              </div>
              <div>
                <span>数量 {{ line.count }}</span>
                <p v-if="line.product">
                  当前单价 ¥{{ money(line.product.price) }}
                </p>
              </div>
            </article>
            <p v-if="selected.length > cart.maxOrderLines" class="form-error">
              单次最多结算 {{ cart.maxOrderLines }} 种商品，请调整购物袋。
            </p>
          </div>
          <div class="checkout-block">
            <div class="shop-section-heading">
              <h2>优惠券</h2>
              <button
                class="text-link"
                :disabled="busy || orderPending || storageBlocked"
                @click="openCoupons"
              >
                选择优惠券
              </button>
            </div>
            <p>
              {{
                couponId ? couponName || `优惠券 #${couponId}` : "不使用优惠券"
              }}
            </p>
            <button
              v-if="couponId"
              class="text-link"
              :disabled="busy || orderPending || storageBlocked"
              @click="chooseCoupon(null)"
            >
              不使用优惠券
            </button>
          </div>
        </section>
        <aside class="order-summary">
          <h2>结算报价</h2>
          <p class="muted small">
            报价不预留库存或优惠券，下单时仍须确认当前金额与资格。
          </p>
          <button
            class="shop-button full-width"
            :disabled="!canQuote"
            @click="calculate"
          >
            {{ busy ? "正在处理…" : quote ? "重新报价" : "计算金额" }}
          </button>
          <template v-if="quote">
            <p v-if="!quoteValid" class="form-error">
              此报价已失效、选项变化或已关联订单，请重新报价。
            </p>
            <template v-if="quote.snapshotPresent"
              ><div>
                <span>商品金额</span
                ><span>¥{{ money(quote.goodsPrice!) }}</span>
              </div>
              <div>
                <span>优惠券</span
                ><span>− ¥{{ money(quote.discountPrice!) }}</span>
              </div>
              <div>
                <span>运费</span><span>¥{{ money(quote.deliveryPrice!) }}</span>
              </div>
              <div class="summary-total">
                <strong>应付金额</strong
                ><strong>¥{{ money(quote.payPrice!) }}</strong>
              </div>
              <p>报价有效至 {{ time(quote.expiresAt) }}（北京时间）</p>
              <p v-if="quote.address" class="small">
                本次报价收货地址：{{ quote.address.name }}，{{
                  quote.address.mobile
                }}，{{ quote.address.areaName }}
                {{ quote.address.detailAddress }}
              </p>
              <details>
                <summary>查看本次报价分摊</summary>
                <div
                  v-for="line in quote.items"
                  :key="line.cartId"
                  class="checkout-allocation"
                >
                  <strong>{{ line.product.name }} × {{ line.count }}</strong>
                  <p>
                    商品 ¥{{ money(line.goodsPrice) }} − 优惠 ¥{{
                      money(line.discountPrice)
                    }}
                    + 运费 ¥{{ money(line.deliveryPrice) }} = ¥{{
                      money(line.payPrice)
                    }}
                  </p>
                </div>
              </details>
            </template>
            <p v-else>本次报价明细已失效，请重新计算金额。</p>
          </template>
          <button
            class="shop-button full-width"
            :disabled="!canSubmit"
            @click="confirmNewOrder"
          >
            {{ orderPending ? "请先确认原订单" : "确认并创建订单" }}
          </button>
          <p class="small muted">创建订单会占用库存和优惠券，不会直接扣款。</p>
        </aside>
      </div>
    </template>
    <Modal
      v-if="orderConfirm"
      :title="
        orderConfirm === 'stop'
          ? '结束未受理请求'
          : orderConfirm === 'resume'
            ? '继续原订单'
            : '确认创建订单'
      "
      @close="orderConfirm = null"
    >
      <template v-if="orderConfirm === 'new' && quote">
        <p>
          应付金额 <strong>¥{{ money(quote.payPrice!) }}</strong>
        </p>
        <p v-if="quote.address">
          收货人：{{ quote.address.name }} · {{ quote.address.mobile }}
        </p>
        <p v-if="quote.address">
          {{ quote.address.areaName }} {{ quote.address.detailAddress }}
        </p>
        <p class="muted">
          创建后将占用本次商品库存及所选优惠券；本次操作不会发起付款。
        </p>
      </template>
      <template v-else-if="orderConfirm === 'stop'">
        <p>确认未受理后，将关闭本次报价，后续需要重新计算金额。</p>
        <p>如果原订单已经受理，会返回原订单结果，不会取消已经创建的订单。</p>
      </template>
      <template v-else-if="orderAttempt">
        <p>
          原订单确认金额
          <strong>¥{{ money(orderAttempt.expectedPayPrice) }}</strong>
        </p>
        <p>
          按原报价、原请求继续处理，原订单若已成功会返回同一结果。不要修改购物袋后另下新单。
        </p>
      </template>
      <template #footer>
        <button class="button" @click="orderConfirm = null">返回核对</button>
        <button
          class="button primary"
          :disabled="busy || loading || (orderConfirm === 'new' && !canSubmit)"
          @click="submitOrder"
        >
          {{
            orderConfirm === "stop"
              ? "确认结束未受理请求"
              : orderConfirm === "resume"
                ? "继续原请求"
                : "创建订单"
          }}
        </button>
      </template>
    </Modal>
    <Modal
      v-if="couponDialog"
      title="选择优惠券"
      @close="
        couponDialog = false;
        couponReads++;
      "
    >
      <p class="muted small">
        可用条件以本次商品、门槛和有效期的报价结果为准。
      </p>
      <button class="button" @click="chooseCoupon(null)">不使用优惠券</button>
      <p v-if="couponError" class="form-error" role="alert">
        {{ couponError }}
      </p>
      <Empty v-if="couponLoading" title="正在读取优惠券…" icon="refresh" />
      <template v-else
        ><article
          v-for="row in couponRows"
          :key="row.id"
          class="checkout-coupon"
        >
          <strong>{{ row.name }}</strong>
          <p>
            {{
              row.discountType === 1
                ? `减 ¥${money(row.discountPrice!)}`
                : `${row.discountPercent! / 10} 折，最多减 ¥${money(row.discountLimitPrice!)}`
            }}
            ·
            {{
              row.usePrice ? `满 ¥${money(row.usePrice)} 可用` : "无金额门槛"
            }}
          </p>
          <p>{{ row.validStartTime }} 至 {{ row.validEndTime }}（北京时间）</p>
          <button class="button" @click="chooseCoupon(row)">使用此券</button>
        </article>
        <Empty
          v-if="!couponRows.length && !couponError"
          title="暂无未使用优惠券"
          icon="coupon"
      /></template>
      <div v-if="couponTotal > 12" class="checkout-actions">
        <button
          class="button"
          :disabled="couponLoading || couponPage <= 1"
          @click="nextCoupon(-1)"
        >
          上一页</button
        ><span>第 {{ couponPage }} 页</span
        ><button
          class="button"
          :disabled="couponLoading || couponPage * 12 >= couponTotal"
          @click="nextCoupon(1)"
        >
          下一页
        </button>
      </div>
      <template #footer
        ><button
          class="button"
          @click="
            couponDialog = false;
            couponReads++;
          "
        >
          关闭
        </button></template
      >
    </Modal>
  </main>
</template>
<style scoped>
.checkout-real {
  padding-top: 32px;
  padding-bottom: 48px;
}
.checkout-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin: 16px 0;
}
.checkout-notice {
  background: var(--primary-soft);
  padding: 12px 16px;
  border-radius: 6px;
}
.checkout-order-result {
  border-block: 1px solid var(--border);
  padding-block: 20px;
  margin-block: 24px;
}
.checkout-order-result h2 {
  margin-bottom: 12px;
}
.checkout-order-result p {
  line-height: 1.8;
}
.checkout-order-number {
  overflow-wrap: anywhere;
}
.checkout-product {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 0;
  border-bottom: 1px solid var(--border);
}
.checkout-product p {
  font-size: 13px;
  margin-top: 8px;
  color: var(--muted);
}
.checkout-product > div:last-child {
  text-align: right;
  white-space: nowrap;
}
.address-option small {
  display: block;
  line-height: 1.7;
  margin-top: 6px;
}
.order-summary > .shop-button,
.order-summary > .button {
  margin: 16px 0;
}
.order-summary > .small {
  overflow-wrap: anywhere;
}
.checkout-allocation {
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.checkout-allocation p {
  line-height: 1.8;
}
.checkout-coupon {
  padding: 16px 0;
  border-bottom: 1px solid var(--border);
}
.checkout-coupon p {
  line-height: 1.8;
  color: var(--muted);
  font-size: 13px;
}
.summary-total {
  padding-top: 16px;
  border-top: 1px solid var(--border);
}
@media (max-width: 600px) {
  .checkout-product {
    gap: 12px;
    flex-direction: column;
  }
  .checkout-product > div:last-child {
    text-align: left;
  }
  .checkout-actions > .button {
    flex: 1;
  }
}
</style>
