<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import Empty from "./Empty.vue";
import Icon from "./Icon.vue";
import Modal from "./Modal.vue";
import { ApiError } from "../api/http";
import { context } from "../api/session";
import { cartIdentity } from "../api/member-cart";
import {
  readMemberOrders,
  readMemberOrder,
  cancelMemberOrder,
  receiveMemberOrder,
  pendingOrderCancellation,
  reconcileOrderCancellation,
  type MemberOrder,
  type MemberOrderDetail,
  type OrderItem,
} from "../api/member-order";

const route = useRoute();
const base = computed(() => `/shop/${context.tenant}`);
const isDetail = computed(() => route.params.page === "order");
const pageNo = ref(1),
  status = ref<number | null>(null),
  total = ref(0);
const rows = ref<MemberOrder[]>([]),
  detail = ref<MemberOrderDetail | null>(null);
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const pending = ref<number | null>(null),
  storageError = ref(""),
  recoveryError = ref("");
const recoveryOrder = ref<MemberOrderDetail | null>(null);
const confirming = ref<MemberOrderDetail | null>(null);
const receiving = ref<MemberOrderDetail | null>(null);
const filters = [
  { value: null, label: "全部" },
  { value: 0, label: "待付款" },
  { value: 10, label: "待发货" },
  { value: 20, label: "已发货" },
  { value: 30, label: "已完成" },
  { value: 40, label: "已关闭" },
];
const visible = computed(() =>
  isDetail.value ? (detail.value ? [detail.value] : []) : rows.value,
);
const locked = computed(() => loading.value || busy.value);
const money = (cents: number) =>
  `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, "0")}`;
const time = (value: number) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(value);
const state = (order: MemberOrder) =>
  order.status === 0 && order.payPrice === 0
    ? "待确认"
    : filters.find((x) => x.value === order.status)?.label || "状态待确认";
const spec = (item: OrderItem) =>
  item.properties
    ?.map((p) => `${p.propertyName}：${p.valueName}`)
    .join(" / ") || "";
const cancellable = (order: MemberOrder) =>
  !order.payStatus &&
  (order.status === 0 ||
    (order.status === 40 && !order.cancelResourcesReleased));
let epoch = 0,
  reads = 0;

function message(e: unknown) {
  return e instanceof Error ? e.message : "读取失败，请重试";
}
function unauthorized(e: unknown) {
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
function syncPending(order?: MemberOrder) {
  try {
    const previous = pendingOrderCancellation();
    if (order) reconcileOrderCancellation(order);
    pending.value = pendingOrderCancellation();
    storageError.value = "";
    if (order && previous === order.id && pending.value === null)
      notice.value = order.payStatus
        ? "最新查询确认原订单已付款，不能按未付款订单取消。"
        : "最新查询确认订单已取消，库存与优惠券处理已完成。";
  } catch (e) {
    storageError.value = message(e);
  }
}
async function load(clearMessages = true) {
  const ticket = epoch,
    serial = ++reads;
  loading.value = true;
  rows.value = [];
  detail.value = null;
  recoveryOrder.value = null;
  total.value = 0;
  recoveryError.value = "";
  if (clearMessages) {
    error.value = "";
    notice.value = "";
  }
  syncPending();
  try {
    if (isDetail.value) {
      const raw = String(route.params.id || "");
      if (!/^[1-9][0-9]*$/.test(raw) || !Number.isSafeInteger(Number(raw)))
        throw new ApiError("订单编号不正确，请返回我的订单", 400);
      const value = await readMemberOrder(Number(raw));
      if (ticket !== epoch || serial !== reads) return;
      detail.value = value;
      syncPending(value);
    } else {
      const value = await readMemberOrders(pageNo.value, status.value);
      if (ticket !== epoch || serial !== reads) return;
      rows.value = value.list;
      total.value = value.total;
      for (const order of value.list) syncPending(order);
    }
  } catch (e) {
    if (ticket !== epoch || serial !== reads) return;
    error.value = message(e);
    unauthorized(e);
  }
  // A previous cancellation can be outside this page/filter; query its own ID.
  if (ticket === epoch && serial === reads && pending.value !== null) {
    try {
      const value = await readMemberOrder(pending.value);
      if (ticket !== epoch || serial !== reads) return;
      recoveryOrder.value = value;
      syncPending(value);
    } catch (e) {
      if (ticket !== epoch || serial !== reads) return;
      recoveryError.value = message(e);
      unauthorized(e);
    }
  }
  if (ticket === epoch && serial === reads) loading.value = false;
}
function filter(value: number | null) {
  status.value = value;
  pageNo.value = 1;
}
async function openCancel(id: number) {
  if (locked.value || storageError.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const order = await readMemberOrder(id);
    if (ticket !== epoch) return;
    syncPending(order);
    if (order.payStatus) notice.value = "原订单已付款，不能按未付款订单取消。";
    else if (order.status === 40 && order.cancelResourcesReleased)
      notice.value = "订单已取消，库存与优惠券处理已完成。";
    else if (!cancellable(order))
      notice.value = "订单状态已变化，请刷新后查看。";
    else if (!storageError.value) confirming.value = order;
  } catch (e) {
    if (ticket === epoch) {
      error.value = message(e);
      unauthorized(e);
    }
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function confirmCancel() {
  if (!confirming.value || locked.value || storageError.value) return;
  const ticket = epoch,
    id = confirming.value.id;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await cancelMemberOrder(id);
    if (ticket !== epoch) return;
    notice.value = "订单已取消，库存与优惠券处理已确认完成。";
  } catch (e) {
    if (ticket !== epoch) return;
    error.value = `取消请求未能返回完整结果：${message(e)}`;
    unauthorized(e);
    notice.value =
      "请核对原订单的最新状态；若仍有待确认的取消记录，可继续处理同一订单。";
  } finally {
    if (ticket === epoch) {
      confirming.value = null;
      busy.value = false;
      await load(false);
    }
  }
}
async function openReceive(id: number) {
  if (locked.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const order = await readMemberOrder(id);
    if (ticket !== epoch) return;
    if (order.status === 30) notice.value = "原订单已确认收货。";
    else if (order.status !== 20)
      notice.value = "订单状态已变化，请刷新后查看。";
    else if (order.afterSalePendingCount > 0)
      notice.value = "订单有进行中售后，暂不能确认收货。";
    else receiving.value = order;
  } catch (e) {
    if (ticket === epoch) {
      error.value = message(e);
      unauthorized(e);
    }
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function confirmReceive() {
  if (!receiving.value || locked.value) return;
  const ticket = epoch,
    order = receiving.value;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await receiveMemberOrder(order.id, order.authVersion);
    if (ticket !== epoch) return;
    notice.value = "原订单已确认收货。";
  } catch (e) {
    if (ticket !== epoch) return;
    error.value = message(e);
    unauthorized(e);
    notice.value =
      "收货结果尚未确认，请查看原订单最新状态；页面不会自动重复提交。";
  } finally {
    if (ticket === epoch) {
      receiving.value = null;
      busy.value = false;
      await load(false);
      if (ticket !== epoch) return;
      const latest = isDetail.value
        ? detail.value
        : rows.value.find((row) => row.id === order.id);
      if (latest?.id === order.id && latest.status === 30) {
        notice.value = "最新查询确认原订单已收货。";
        error.value = "";
      }
    }
  }
}
watch(
  [
    cartIdentity,
    () => route.params.page,
    () => route.params.id,
    pageNo,
    status,
  ],
  () => {
    epoch++;
    reads++;
    confirming.value = null;
    receiving.value = null;
    busy.value = false;
    pending.value = null;
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  epoch++;
  reads++;
  rows.value = [];
  detail.value = null;
  recoveryOrder.value = null;
  confirming.value = null;
  receiving.value = null;
});
</script>

<template>
  <main class="shop-container member-orders" :aria-busy="locked">
    <div class="shop-page-heading">
      <h1>{{ isDetail ? "订单详情" : "我的订单" }}</h1>
      <RouterLink class="text-link" :to="`${base}/aftersales`"
        >退货退款</RouterLink
      >
      <RouterLink v-if="isDetail" class="text-link" :to="`${base}/orders`"
        >返回我的订单</RouterLink
      >
      <button class="button" :disabled="locked" @click="load()">
        {{ loading ? "正在查询…" : "刷新订单" }}
      </button>
    </div>
    <p v-if="notice" class="order-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <section
      v-if="pending !== null || storageError"
      class="order-notice"
      aria-label="上次取消结果"
    >
      <h2>确认上次取消结果</h2>
      <p v-if="storageError" role="alert">{{ storageError }}</p>
      <template v-if="pending !== null">
        <p>订单 {{ recoveryOrder?.no || pending }}：</p>
        <p v-if="recoveryOrder?.status === 40">
          订单已取消，库存与优惠券仍在处理中。
        </p>
        <p v-else>取消结果尚未确认，请查询原订单后再继续。</p>
        <p v-if="recoveryError" class="form-error" role="alert">
          {{ recoveryError }}
        </p>
        <div class="order-actions">
          <RouterLink class="button" :to="`${base}/order/${pending}`"
            >查看原订单</RouterLink
          >
          <button class="button" :disabled="locked" @click="load()">
            查询取消结果
          </button>
          <button
            class="button"
            :disabled="locked || !!storageError"
            @click="pending !== null && openCancel(pending)"
          >
            继续取消原订单
          </button>
        </div>
      </template>
      <button
        v-if="storageError"
        class="button"
        :disabled="locked"
        @click="load()"
      >
        恢复存储后重新读取
      </button>
    </section>
    <nav v-if="!isDetail" class="shop-order-tabs" aria-label="按订单状态筛选">
      <button
        v-for="tab in filters"
        :key="tab.label"
        :class="{ active: status === tab.value }"
        :aria-pressed="status === tab.value"
        :disabled="locked"
        @click="filter(tab.value)"
      >
        {{ tab.label }}
      </button>
    </nav>
    <p v-if="loading" role="status">正在查询本人订单…</p>
    <article v-for="order in visible" :key="order.id" class="consumer-order">
      <header>
        <div>
          <time>{{ time(order.createdAt) }}</time
          ><span class="order-number">订单 {{ order.no }}</span>
        </div>
        <strong class="green-text">{{ state(order) }}</strong>
      </header>
      <div
        v-for="item in order.items"
        :key="item.id"
        class="consumer-order-item"
      >
        <div class="order-product-mark" aria-hidden="true">
          <Icon name="bag" :size="24" />
        </div>
        <div>
          <h3>{{ item.spuName }}</h3>
          <p v-if="spec(item)">{{ spec(item) }}</p>
          <p>单价 ¥{{ money(item.price) }} × {{ item.count }} 件</p>
          <p v-if="item.discountPrice">优惠 ¥{{ money(item.discountPrice) }}</p>
          <p v-if="item.deliveryCount">已发 {{ item.deliveryCount }} 件</p>
          <p v-if="item.refundCount">已退款 {{ item.refundCount }} 件</p>
        </div>
        <strong
          >¥{{ money(item.payPrice)
          }}<small v-if="item.deliveryPrice"
            >含运费 ¥{{ money(item.deliveryPrice) }}</small
          ></strong
        >
      </div>
      <p
        v-if="!isDetail && order.itemCount > order.items.length"
        class="order-subnote"
      >
        另有 {{ order.itemCount - order.items.length }} 项商品，请查看订单详情。
      </p>
      <section
        v-if="isDetail && detail"
        class="consumer-order-detail"
        aria-label="订单收件与金额详情"
      >
        <div>
          <Icon name="pin" /><span
            >{{ detail.receiverName }} · {{ detail.receiverMobile }}<br />{{
              detail.receiverAreaName
            }}
            {{ detail.receiverDetailAddress }}</span
          >
        </div>
        <p v-if="detail.userRemark">买家备注：{{ detail.userRemark }}</p>
        <dl class="order-amounts">
          <div>
            <dt>商品金额</dt>
            <dd>¥{{ money(order.totalPrice) }}</dd>
          </div>
          <div>
            <dt>优惠</dt>
            <dd>−¥{{ money(order.discountPrice) }}</dd>
          </div>
          <div>
            <dt>运费</dt>
            <dd>¥{{ money(order.deliveryPrice) }}</dd>
          </div>
          <div v-if="order.refundPrice">
            <dt>已退款</dt>
            <dd>¥{{ money(order.refundPrice) }}</dd>
          </div>
        </dl>
        <p>时间均为北京时间。</p>
        <p v-if="order.paidAt">付款时间：{{ time(order.paidAt) }}</p>
        <p v-if="order.cancelledAt">
          取消时间：{{ time(order.cancelledAt) }} ·
          {{ order.cancelType === 10 ? "超时取消" : "主动取消" }}
        </p>
      </section>
      <p v-if="order.requiresPayment" class="order-subnote">
        尚未付款，付款截止
        {{ time(order.payDeadline) }}（北京时间）。
      </p>
      <p v-else-if="order.status === 0" class="order-subnote">
        订单尚待确认，请刷新查询最新状态。
      </p>
      <p v-if="order.status === 40 && !order.payStatus" class="order-subnote">
        {{
          order.cancelResourcesReleased
            ? "库存与优惠券处理已完成。"
            : "库存与优惠券仍在处理中，请稍后查询。"
        }}
      </p>
      <section
        v-if="order.deliveredAt"
        class="order-subnote"
        aria-label="快递与收货信息"
      >
        <p>{{ order.logisticsName }} · {{ order.logisticsNo }}</p>
        <p>发货时间：{{ time(order.deliveredAt) }}（北京时间）</p>
        <p v-if="order.receivedAt">
          收货时间：{{ time(order.receivedAt) }}（北京时间）
        </p>
        <p v-else-if="order.afterSalePendingCount > 0">
          有进行中售后，确认收货与自动收货暂时暂停。
        </p>
        <p v-else-if="order.status === 20 && order.autoReceiveAt">
          自动收货日期：{{
            time(order.autoReceiveAt)
          }}（北京时间），实际处理以订单最新状态为准。
        </p>
      </section>
      <footer>
        <span
          >{{ order.payStatus ? "实付" : "订单金额" }}
          <strong>¥{{ money(order.payPrice) }}</strong
          ><small
            >共 {{ order.productCount }} 件 · 含运费 ¥{{
              money(order.deliveryPrice)
            }}</small
          ></span
        >
        <div>
          <RouterLink
            v-if="order.payStatus && [10, 20, 30].includes(order.status)"
            class="button"
            :to="`${base}/aftersales?order=${order.id}`"
            >申请售后</RouterLink
          >
          <button
            v-if="order.status === 20"
            class="button primary"
            :disabled="locked || order.afterSalePendingCount > 0"
            @click="openReceive(order.id)"
          >
            确认收货
          </button>
          <RouterLink
            v-if="order.status === 0 && !order.payStatus"
            class="button primary"
            :to="`${base}/payment/${order.id}`"
            >{{ order.payPrice === 0 ? "确认订单" : "去付款" }}</RouterLink
          >
          <RouterLink
            v-if="!isDetail"
            class="button"
            :to="`${base}/order/${order.id}`"
            >订单详情</RouterLink
          >
          <button
            v-if="
              cancellable(order) && (pending === null || pending === order.id)
            "
            class="button"
            :disabled="locked || !!storageError"
            @click="openCancel(order.id)"
          >
            {{ order.status === 40 ? "继续处理取消" : "取消订单" }}
          </button>
        </div>
      </footer>
    </article>
    <Empty
      v-if="!loading && !error && !visible.length"
      :title="
        isDetail
          ? '未找到本人订单'
          : status === null
            ? '暂无订单'
            : '该状态暂无订单'
      "
      icon="order"
    >
      <RouterLink v-if="isDetail" class="shop-button" :to="`${base}/orders`"
        >返回我的订单</RouterLink
      >
      <button
        v-else-if="status !== null"
        class="shop-button"
        @click="filter(null)"
      >
        查看全部订单
      </button>
      <RouterLink v-else class="shop-button" :to="`${base}/bag`"
        >查看购物袋</RouterLink
      >
    </Empty>
    <nav
      v-if="!isDetail && !error && total > 0"
      class="order-pagination"
      aria-label="订单分页"
    >
      <button
        class="button"
        :disabled="locked || pageNo === 1"
        @click="pageNo--"
      >
        上一页
      </button>
      <span>第 {{ pageNo }} 页 · 共 {{ total }} 单</span>
      <button
        class="button"
        :disabled="locked || pageNo * 10 >= total || pageNo >= 10001"
        @click="pageNo++"
      >
        下一页
      </button>
    </nav>
    <p
      v-if="!isDetail && pageNo >= 10001 && pageNo * 10 < total"
      class="order-subnote"
    >
      已到查询范围上限，请按订单状态缩小范围。
    </p>
    <Modal
      v-if="receiving"
      title="确认收货"
      @close="!busy && (receiving = null)"
    >
      <p class="order-number">订单 {{ receiving.no }}</p>
      <p>{{ receiving.logisticsName }} · {{ receiving.logisticsNo }}</p>
      <p>
        请确认已收到本次快递中的全部商品。确认后订单将完成，售后申请期限按收货时间计算。
      </p>
      <template #footer>
        <button class="button" :disabled="busy" @click="receiving = null">
          返回
        </button>
        <button
          class="button primary"
          :disabled="locked"
          @click="confirmReceive"
        >
          {{ busy ? "正在确认…" : "已收到商品，确认收货" }}
        </button>
      </template>
    </Modal>
    <Modal
      v-if="confirming"
      :title="confirming.status === 40 ? '继续处理取消订单' : '取消订单'"
      @close="!busy && (confirming = null)"
    >
      <p class="order-number">订单 {{ confirming.no }}</p>
      <p>
        订单金额 ¥{{ money(confirming.payPrice) }}，共
        {{ confirming.productCount }} 件。
      </p>
      <p>
        确认取消后将关闭付款入口，并处理库存与优惠券；已过期的优惠券不会延期。
      </p>
      <p v-if="pending === confirming.id">
        将继续处理同一订单，请勿重新下单代替结果查询。
      </p>
      <template #footer
        ><button class="button" :disabled="busy" @click="confirming = null">
          返回
        </button>
        <button
          class="button primary"
          :disabled="locked"
          @click="confirmCancel"
        >
          {{ busy ? "正在确认…" : "确认取消原订单" }}
        </button></template
      >
    </Modal>
  </main>
</template>

<style scoped>
.member-orders {
  padding-bottom: 40px;
}
.shop-page-heading {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.shop-page-heading h1 {
  margin-right: auto;
}
.order-notice {
  padding: 18px;
  border: 1px solid var(--border, #e4e9e6);
  background: #f4f8f5;
  margin-bottom: 24px;
}
.order-notice h2 {
  font-size: 16px;
  margin: 0 0 12px;
}
.order-notice p {
  line-height: 1.8;
}
.order-actions,
.order-pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.order-pagination {
  justify-content: center;
  margin-top: 24px;
  font-size: 13px;
}
.order-number {
  overflow-wrap: anywhere;
}
.consumer-order > header > div {
  flex-wrap: wrap;
  gap: 8px 20px;
}
.consumer-order-item {
  align-items: flex-start;
}
.consumer-order-item > .order-product-mark {
  flex: 0 0 56px;
  display: grid;
  place-items: center;
  height: 70px;
  background: #f4f6f5;
  color: #6a7872;
}
.consumer-order-item > div:not(.order-product-mark) {
  min-width: 0;
  overflow-wrap: anywhere;
}
.consumer-order-item h3 {
  font-size: 14px;
  line-height: 1.6;
}
.consumer-order-item p,
.order-subnote,
.consumer-order-detail {
  font-size: 13px;
  color: #6a7872;
}
.consumer-order-item > strong {
  margin-right: 0;
  white-space: nowrap;
}
.consumer-order-item > strong small,
.consumer-order > footer small {
  display: block;
  margin-top: 8px;
  font-size: 12px;
}
.order-subnote {
  padding: 0 24px;
  line-height: 1.8;
}
.consumer-order-detail {
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.consumer-order-detail > div {
  color: #182b25;
  font-size: 14px;
}
.order-amounts {
  max-width: 420px;
}
.order-amounts > div {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}
.order-amounts dd {
  margin: 0;
}
.consumer-order > footer > div {
  flex-wrap: wrap;
}
.button,
.shop-order-tabs > button {
  min-height: 44px;
}
@media (max-width: 600px) {
  .consumer-order > header,
  .consumer-order > footer {
    flex-direction: column;
    align-items: flex-start;
    padding: 16px;
  }
  .consumer-order-item {
    padding: 16px;
    gap: 12px;
    flex-wrap: wrap;
  }
  .consumer-order-item > .order-product-mark {
    flex-basis: 40px;
    height: 56px;
  }
  .consumer-order-item > strong {
    margin-left: 52px;
  }
  .consumer-order-detail {
    padding: 12px 16px;
  }
  .order-subnote {
    padding: 0 16px;
  }
}
</style>
