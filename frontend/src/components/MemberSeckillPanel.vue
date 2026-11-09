<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import Empty from "./Empty.vue";
import Modal from "./Modal.vue";
import { ApiError } from "../api/http";
import { context, credentials, identities } from "../api/session";
import {
  listMemberAddresses,
  memberIdentity,
  type MemberAddress,
} from "../api/member-profile";
import {
  memberSeckillPage,
  memberSeckillGet,
  memberSeckillHistory,
  memberSeckillRequest,
  readSeckillAttempt,
  beginSeckillQuote,
  fetchSeckillQuote,
  beginSeckillAdmission,
  admitSeckill,
  readSeckillReceipt,
  clearSeckillAttempt,
  type SeckillActivity,
  type SeckillAttempt,
  type SeckillReceipt,
} from "../api/member-seckill";

const route = useRoute();
const base = computed(() => `/shop/${context.tenant}`);
const signedIn = computed(
  () =>
    context.realm === "member" &&
    !!identities.member &&
    identities.member.tenantCode === context.tenant,
);
const login = computed(() => ({
  path: `${base.value}/login`,
  query: { redirect: route.fullPath },
}));
const rows = ref<SeckillActivity[]>([]),
  total = ref(0),
  page = ref(1);
const activity = ref<SeckillActivity | null>(null),
  addresses = ref<MemberAddress[]>([]);
const history = ref<SeckillReceipt[]>([]),
  historyDetail = ref<SeckillReceipt | null>(null);
const isHistory = computed(
  () => String(route.params.page) === "seckill-records",
);
const isResult = computed(() => String(route.params.page) === "seckill-result");
const skuId = ref<number | null>(null),
  addressId = ref<number | null>(null),
  quantity = ref(1);
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  storageBlocked = ref(false);
const attempt = ref<SeckillAttempt | null>(null),
  receipt = ref<SeckillReceipt | null>(null);
const rejectedClosed = ref(false),
  confirmation = ref(false),
  quoteVerified = ref(false),
  quoteRejected = ref(false);
const clock = ref(Date.now()),
  offset = ref(0);
const isList = computed(
  () => String(route.params.page) === "seckill" && !route.params.id,
);
const detailId = computed(() =>
  String(route.params.page) === "seckill" &&
  /^[1-9][0-9]*$/.test(String(route.params.id || ""))
    ? Number(route.params.id)
    : null,
);
const selectedSku = computed(() =>
  activity.value?.skus?.find((s) => s.skuId === skuId.value),
);
const canQuote = computed(
  () =>
    !loading.value &&
    !busy.value &&
    !storageBlocked.value &&
    !attempt.value &&
    signedIn.value &&
    !!activity.value?.quoteAvailable &&
    !!selectedSku.value &&
    !!addressId.value &&
    addresses.value.some((a) => a.id === addressId.value) &&
    Number.isSafeInteger(quantity.value) &&
    quantity.value > 0 &&
    quantity.value <=
      Math.min(
        activity.value.singleLimit,
        activity.value.memberLimit,
        selectedSku.value.availableQuantity,
      ),
);
const quoteUsable = computed(
  () =>
    !!attempt.value?.quote &&
    quoteVerified.value &&
    !attempt.value.clientId &&
    !attempt.value.quote.expired &&
    !attempt.value.quote.bound &&
    attempt.value.quote.snapshotPresent &&
    clock.value + offset.value < Date.parse(attempt.value.quote.expiresAt),
);
const canFinish = computed(
  () =>
    !!attempt.value &&
    (rejectedClosed.value ||
      (!!receipt.value?.found &&
        (!!receipt.value.orderId || receipt.value.status === 50)) ||
      (!attempt.value.clientId &&
        (quoteRejected.value ||
          (!!attempt.value.quote && !attempt.value.quote.bound)))),
);
let epoch = 0;
const timer = setInterval(() => (clock.value = Date.now()), 1000);
function money(cents: number) {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, "0")}`;
}
function time(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(Date.parse(value));
}
function status(value: number) {
  return (
    (
      { 20: "已发布", 30: "已停止", 40: "清算中", 50: "已结束" } as Record<
        number,
        string
      >
    )[value] || "待确认"
  );
}
function applyClock(server: number) {
  offset.value = server - Date.now();
  clock.value = Date.now();
}
function fail(value: unknown) {
  error.value =
    value instanceof Error ? value.message : "暂时无法处理，请稍后查询原结果";
  if (value instanceof ApiError && value.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
function ownRun(n: number) {
  return n === epoch;
}
async function readCatalog() {
  const n = epoch;
  loading.value = true;
  // List reads never erase a pending purchase or its last write error.
  try {
    if (isHistory.value) {
      const value = await memberSeckillHistory(page.value);
      if (!ownRun(n)) return;
      history.value = value.list;
      total.value = value.total;
    } else if (isResult.value) {
      const value = await memberSeckillRequest(String(route.params.id || ""));
      if (!ownRun(n)) return;
      historyDetail.value = value;
    } else if (isList.value) {
      const result = await memberSeckillPage(page.value);
      if (!ownRun(n)) return;
      rows.value = result.list;
      total.value = result.total;
    } else if (detailId.value && Number.isSafeInteger(detailId.value)) {
      const value = await memberSeckillGet(detailId.value);
      if (!ownRun(n)) return;
      activity.value = value;
      applyClock(value.serverTime);
      skuId.value = value.skus?.some((s) => s.skuId === skuId.value)
        ? skuId.value
        : value.skus?.[0]?.skuId || null;
      if (signedIn.value) {
        const values = await listMemberAddresses();
        if (!ownRun(n)) return;
        addresses.value = values;
        addressId.value = values.some((a) => a.id === addressId.value)
          ? addressId.value
          : (values.find((a) => a.defaultStatus) || values[0])?.id || null;
      }
    }
  } catch (e) {
    if (ownRun(n)) fail(e);
  } finally {
    if (ownRun(n)) loading.value = false;
  }
}
async function changePage(delta: number) {
  if (loading.value || busy.value) return;
  page.value += delta;
  await readCatalog();
}
async function purchase(action: () => Promise<void>) {
  if (busy.value || storageBlocked.value || !signedIn.value) return;
  const n = epoch;
  busy.value = true;
  error.value = "";
  try {
    await action();
  } catch (e) {
    if (ownRun(n)) fail(e);
  } finally {
    if (ownRun(n)) busy.value = false;
  }
}
async function quote(retry = false) {
  if (!attempt.value && !canQuote.value) return;
  await purchase(async () => {
    const n = epoch;
    let firstSend = false;
    if (!attempt.value) {
      // Persist the original inputs before the first POST.
      attempt.value = beginSeckillQuote({
        activityId: activity.value!.id,
        generation: activity.value!.generation,
        skuId: skuId.value!,
        quantity: quantity.value,
        addressId: addressId.value!,
      });
      retry = true;
      firstSend = true;
    }
    const original = attempt.value;
    quoteRejected.value = false;
    let value: SeckillAttempt | null;
    try {
      if (retry && !firstSend) {
        value = await fetchSeckillQuote(original, false);
        if (!ownRun(n)) return;
        if (value === null) value = await fetchSeckillQuote(original, true);
      } else value = await fetchSeckillQuote(original, retry);
    } catch (e) {
      if (
        ownRun(n) &&
        !original.clientId &&
        !original.quote &&
        e instanceof ApiError &&
        !e.outcomeUnknown &&
        [400, 404, 409].includes(e.code)
      )
        quoteRejected.value = true;
      throw e;
    }
    if (!ownRun(n)) return;
    if (value) {
      attempt.value = value;
      quoteVerified.value = true;
      quoteRejected.value = false;
      applyClock(value.quote!.serverTime);
      notice.value = value.quote!.bound
        ? "原报价已绑定申请，请保留记录并查询原购买结果。"
        : "请核对规格、收货地址和实际金额，再确认提交。";
    } else
      notice.value =
        "暂未查到原报价，仍可能在处理。请稍后查询，或继续原报价请求。";
  });
}
async function submit() {
  if (!attempt.value || !quoteUsable.value || busy.value) return;
  confirmation.value = false;
  await purchase(async () => {
    attempt.value = beginSeckillAdmission(attempt.value!);
    await sendOriginal();
  });
}
async function sendOriginal() {
  const n = epoch,
    original = attempt.value!;
  const value = await admitSeckill(original);
  if (!ownRun(n)) return;
  rejectedClosed.value =
    value.decision === 20 &&
    value.quoteClosed === true &&
    value.redisSynchronized;
  if (value.decision === 10) {
    receipt.value = value.request;
    applyClock(value.request!.serverTime);
    notice.value = value.request!.orderId
      ? "已查到原订单，请进入订单查看付款及履约状态。"
      : "申请已受理，正在确认订单。受理成功尚不代表订单已创建。";
  } else
    notice.value = rejectedClosed.value
      ? "本次未受理，清理已确认，可结束查看后重新选择。"
      : "本次未受理，清理仍待确认。请保留原请求并稍后继续查询。";
}
async function queryReceipt() {
  if (!attempt.value?.clientId) return;
  await purchase(async () => {
    const n = epoch,
      value = await readSeckillReceipt(attempt.value!);
    if (!ownRun(n)) return;
    receipt.value = value;
    applyClock(value.serverTime);
    notice.value = !value.found
      ? "暂未观察到原申请记录，仍可能在处理。请保留原请求，稍后再查或继续原请求。"
      : value.orderId
        ? "已查到原订单，请进入订单查看付款及履约状态。"
        : value.status === 50
          ? "原申请已结束，未创建订单。"
          : "原申请仍在处理，请稍后查询。";
  });
}
async function retryAdmission() {
  if (!attempt.value?.clientId) return;
  // Always read first. Absence is not a failure proof and never changes the request ID.
  await purchase(async () => {
    const n = epoch,
      value = await readSeckillReceipt(attempt.value!);
    if (!ownRun(n)) return;
    receipt.value = value;
    applyClock(value.serverTime);
    if (value.found) {
      notice.value = value.orderId
        ? "原订单已创建。"
        : "原申请已有记录，请稍后查询进度。";
      return;
    }
    await sendOriginal();
  });
}
async function finish() {
  if (!canFinish.value || busy.value || !attempt.value) return;
  try {
    clearSeckillAttempt(attempt.value);
    attempt.value = null;
    receipt.value = null;
    rejectedClosed.value = false;
    quoteRejected.value = false;
    quoteVerified.value = false;
    notice.value = "本次查看已结束，已有订单可在我的订单中继续查看。";
    error.value = "";
  } catch (e) {
    fail(e);
  }
}
watch(
  [() => route.fullPath, memberIdentity, () => credentials().accessToken],
  () => {
    epoch++;
    loading.value = false;
    busy.value = false;
    confirmation.value = false;
    activity.value = null;
    rows.value = [];
    history.value = [];
    historyDetail.value = null;
    total.value = 0;
    page.value = 1;
    addresses.value = [];
    addressId.value = null;
    skuId.value = null;
    quantity.value = 1;
    attempt.value = null;
    receipt.value = null;
    error.value = "";
    notice.value = "";
    storageBlocked.value = false;
    rejectedClosed.value = false;
    quoteRejected.value = false;
    quoteVerified.value = false;
    if (signedIn.value) {
      try {
        attempt.value = readSeckillAttempt();
      } catch (e) {
        storageBlocked.value = true;
        fail(e);
      }
    }
    void readCatalog();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  epoch++;
  clearInterval(timer);
});
</script>

<template>
  <main class="member-seckill" :aria-busy="loading || busy">
    <header class="seckill-heading">
      <div>
        <RouterLink v-if="!isList" :to="`${base}/seckill`" class="text-link"
          >返回秒杀活动</RouterLink
        >
        <h1>
          {{
            isHistory
              ? "我的秒杀申请"
              : isResult
                ? "申请详情"
                : isList
                  ? "限时秒杀"
                  : "活动详情"
          }}
        </h1>
        <p>选择活动、核对金额，再确认购买。</p>
      </div>
      <div class="seckill-actions">
        <RouterLink
          v-if="signedIn && !isHistory"
          class="text-link"
          :to="`${base}/seckill-records`"
          >我的秒杀申请</RouterLink
        >
        <button class="button" :disabled="busy || loading" @click="readCatalog">
          {{
            loading
              ? "读取中…"
              : isHistory || isResult
                ? "刷新申请"
                : "刷新活动"
          }}
        </button>
      </div>
    </header>
    <div class="seckill-content">
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="seckill-notice" role="status">{{ notice }}</p>
      <section
        v-if="attempt"
        class="pending-purchase"
        aria-labelledby="pending-title"
      >
        <h2 id="pending-title">本次购买进度</h2>
        <p>
          活动 {{ attempt.input.activityId }} · 规格 {{ attempt.input.skuId }} ·
          {{ attempt.input.quantity }} 件 · 地址 {{ attempt.input.addressId }}
        </p>
        <RouterLink
          v-if="detailId !== attempt.input.activityId"
          class="text-link"
          :to="`${base}/seckill/${attempt.input.activityId}`"
          >查看原活动</RouterLink
        >
        <p class="request-reference">
          报价请求号：{{ attempt.input.requestId
          }}<span v-if="attempt.clientId"
            >购买请求号：{{ attempt.clientId }}</span
          >
        </p>
        <template v-if="attempt.quote">
          <dl class="quote-amounts">
            <div>
              <dt>商品金额</dt>
              <dd>¥{{ money(attempt.quote.goodsAmount) }}</dd>
            </div>
            <div>
              <dt>运费</dt>
              <dd>¥{{ money(attempt.quote.shippingAmount) }}</dd>
            </div>
            <div>
              <dt>合计应付</dt>
              <dd>
                <strong>¥{{ money(attempt.quote.payAmount) }}</strong>
              </dd>
            </div>
          </dl>
          <p v-if="!attempt.clientId">
            报价有效至 {{ time(attempt.quote.expiresAt)
            }}{{ quoteUsable ? "" : "，当前需重新核对" }}。报价不预留库存。
          </p>
        </template>
        <p v-if="receipt?.found && !receipt.orderId">
          {{
            receipt.status === 50
              ? "原申请已结束，未创建订单。"
              : "原申请处理中，请稍后查询。"
          }}
        </p>
        <div class="seckill-actions">
          <template v-if="!attempt.clientId">
            <button class="button" :disabled="busy" @click="quote(false)">
              查询原报价
            </button>
            <button
              v-if="!attempt.quote"
              class="button"
              :disabled="busy"
              @click="quote(true)"
            >
              继续原报价请求
            </button>
            <button
              v-if="quoteUsable"
              class="shop-button"
              :disabled="busy"
              @click="confirmation = true"
            >
              确认购买
            </button>
          </template>
          <template v-else>
            <button class="button" :disabled="busy" @click="queryReceipt">
              {{ busy ? "处理中…" : "查询原购买结果" }}
            </button>
            <button
              v-if="!receipt?.found && !rejectedClosed"
              class="button"
              :disabled="busy"
              @click="retryAdmission"
            >
              继续原请求
            </button>
          </template>
          <RouterLink
            v-if="receipt?.orderId"
            class="shop-button"
            :to="`${base}/order/${receipt.orderId}`"
            >查看原订单</RouterLink
          >
          <button
            v-if="canFinish"
            class="button"
            :disabled="busy"
            @click="finish"
          >
            {{
              receipt?.orderId || rejectedClosed || receipt?.status === 50
                ? "结束本次查看"
                : "放弃此报价，重新选择"
            }}
          </button>
        </div>
        <p v-if="!canFinish && attempt.clientId" class="seckill-help">
          页面或网络异常时请保留原请求。暂未查到结果不代表购买失败。
        </p>
      </section>

      <template v-if="isHistory">
        <p v-if="loading && !history.length" role="status">正在读取本人申请…</p>
        <Empty
          v-else-if="!history.length && !error"
          title="还没有已受理的秒杀申请"
          ><p>未提交或仍未完成受理的请求，请在上方保留原号继续查询。</p></Empty
        >
        <ul v-else-if="history.length" class="activity-list">
          <li v-for="item in history" :key="item.requestNo!">
            <div>
              <h2>活动 {{ item.activityId }} · {{ item.quantity }} 件</h2>
              <p>
                {{ time(item.acceptedAt!) }} ·
                {{
                  item.orderId
                    ? "已创建订单"
                    : item.status === 50
                      ? "申请已结束"
                      : "处理中"
                }}
              </p>
              <p class="request-reference">申请号：{{ item.requestNo }}</p>
            </div>
            <div class="activity-price">
              <strong>¥{{ money(item.payAmount!) }}</strong
              ><RouterLink
                class="text-link"
                :to="`${base}/seckill-result/${item.requestNo}`"
                >查看原申请</RouterLink
              ><RouterLink
                v-if="item.orderId"
                class="text-link"
                :to="`${base}/order/${item.orderId}`"
                >查看原订单</RouterLink
              >
            </div>
          </li>
        </ul>
        <nav
          v-if="total > 20"
          class="seckill-actions"
          aria-label="本人申请分页"
        >
          <button
            class="button"
            :disabled="loading || busy || page <= 1"
            @click="changePage(-1)"
          >
            上一页</button
          ><span>第 {{ page }} 页 · 共 {{ total }} 项</span
          ><button
            class="button"
            :disabled="loading || busy || page * 20 >= total || page >= 10000"
            @click="changePage(1)"
          >
            下一页
          </button>
        </nav>
      </template>
      <section v-else-if="isResult && historyDetail" class="activity-detail">
        <template v-if="historyDetail.found"
          ><h2>
            活动 {{ historyDetail.activityId }} ·
            {{ historyDetail.quantity }} 件
          </h2>
          <p>
            {{
              historyDetail.orderId
                ? "原订单已创建，请到订单查看付款和履约状态。"
                : historyDetail.status === 50
                  ? "原申请已结束，未创建订单。"
                  : "原申请仍在处理，请稍后刷新。"
            }}
          </p>
          <p>
            应付金额 ¥{{ money(historyDetail.payAmount!) }}，含运费 ¥{{
              money(historyDetail.shippingAmount!)
            }}。
          </p>
          <p class="request-reference">申请号：{{ historyDetail.requestNo }}</p>
          <p>
            受理时间 {{ time(historyDetail.acceptedAt!) }}，原付款期限
            {{ time(historyDetail.payDeadline!) }}。
          </p>
          <RouterLink
            v-if="historyDetail.orderId"
            class="shop-button"
            :to="`${base}/order/${historyDetail.orderId}`"
            >查看原订单</RouterLink
          ></template
        >
        <Empty v-else title="尚未观察到本人申请记录"
          ><p>这不代表购买失败；请保留原请求，稍后再查。</p></Empty
        >
        <p>
          <RouterLink class="text-link" :to="`${base}/seckill-records`"
            >返回我的秒杀申请</RouterLink
          >
        </p>
      </section>
      <template v-else-if="isList">
        <p v-if="loading && !rows.length" role="status">正在读取活动…</p>
        <Empty v-else-if="!rows.length && !error" title="当前没有已发布活动"
          ><p>可以稍后再来看看。</p></Empty
        >
        <ul v-else-if="rows.length" class="activity-list">
          <li v-for="row in rows" :key="row.id">
            <div>
              <span class="activity-state">{{ status(row.status) }}</span>
              <h2>
                <RouterLink :to="`${base}/seckill/${row.id}`">{{
                  row.name
                }}</RouterLink>
              </h2>
              <p>{{ row.productName }}</p>
              <p>{{ time(row.startTime) }} — {{ time(row.endTime) }}</p>
            </div>
            <div class="activity-price">
              <strong>¥{{ money(row.minPriceCent) }}<small> 起</small></strong
              ><RouterLink class="text-link" :to="`${base}/seckill/${row.id}`"
                >查看活动</RouterLink
              >
            </div>
          </li>
        </ul>
        <nav v-if="total > 20" class="seckill-actions" aria-label="活动分页">
          <button
            class="button"
            :disabled="loading || busy || page <= 1"
            @click="changePage(-1)"
          >
            上一页</button
          ><span>第 {{ page }} 页 · 共 {{ total }} 项</span
          ><button
            class="button"
            :disabled="loading || busy || page * 20 >= total || page >= 10000"
            @click="changePage(1)"
          >
            下一页
          </button>
        </nav>
      </template>
      <section v-else-if="activity" class="activity-detail">
        <span class="activity-state">{{ status(activity.status) }}</span>
        <h2>{{ activity.name }}</h2>
        <p>{{ activity.productName }}</p>
        <p>{{ time(activity.startTime) }} — {{ time(activity.endTime) }}</p>
        <p>
          单次限购 {{ activity.singleLimit }} 件，每人累计限购
          {{ activity.memberLimit }} 件。
        </p>
        <p v-if="!activity.quoteAvailable" class="seckill-help">
          当前活动暂不可购买。已有申请请在上方查询原结果。
        </p>
        <form v-if="signedIn" @submit.prevent="quote(true)">
          <fieldset
            :disabled="
              busy ||
              loading ||
              !!attempt ||
              storageBlocked ||
              !activity.quoteAvailable
            "
          >
            <label for="seckill-sku"
              >规格<select id="seckill-sku" v-model="skuId" required>
                <option
                  v-for="sku in activity.skus"
                  :key="sku.skuId"
                  :value="sku.skuId"
                >
                  {{
                    sku.properties
                      .map((p) => `${p.propertyName}：${p.valueName}`)
                      .join(" / ") || `规格 ${sku.skuId}`
                  }}
                  · ¥{{ money(sku.priceCent) }}
                </option>
              </select></label
            >
            <label for="seckill-quantity"
              >数量<input
                id="seckill-quantity"
                v-model.number="quantity"
                type="number"
                min="1"
                :max="
                  Math.min(
                    activity.singleLimit,
                    activity.memberLimit,
                    selectedSku?.availableQuantity || 0,
                  )
                "
                step="1"
                required
            /></label>
            <label for="seckill-address"
              >收货地址<select
                id="seckill-address"
                v-model="addressId"
                required
              >
                <option :value="null" disabled>请选择地址</option>
                <option
                  v-for="address in addresses"
                  :key="address.id"
                  :value="address.id"
                >
                  {{ address.name }} · {{ address.areaName }}
                  {{ address.detailAddress }}
                </option>
              </select></label
            >
            <p v-if="!addresses.length && !loading">
              <RouterLink class="text-link" :to="`${base}/addresses`"
                >先添加收货地址</RouterLink
              >
            </p>
            <button class="shop-button" :disabled="!canQuote" type="submit">
              获取实际报价
            </button>
          </fieldset>
        </form>
        <p v-else>
          <RouterLink :to="login" class="shop-button"
            >登录后选择地址并购买</RouterLink
          >
        </p>
      </section>
      <Empty
        v-else-if="!isList && !loading && !error"
        title="请从活动列表选择活动"
        ><RouterLink class="text-link" :to="`${base}/seckill`"
          >查看秒杀活动</RouterLink
        ></Empty
      >
    </div>
    <Modal
      v-if="confirmation && attempt?.quote"
      title="确认本次购买"
      @close="confirmation = false"
    >
      <p>
        购买 {{ attempt.input.quantity }} 件，收货地址编号
        {{ attempt.input.addressId }}。
      </p>
      <p>
        合计应付 <strong>¥{{ money(attempt.quote.payAmount) }}</strong
        >（含运费 ¥{{ money(attempt.quote.shippingAmount) }}）。
      </p>
      <p>提交后请等待原申请结果。订单创建后，再进入订单完成付款。</p>
      <template #footer
        ><button class="button" @click="confirmation = false">返回核对</button
        ><button
          class="shop-button"
          :disabled="busy || !quoteUsable"
          @click="submit"
        >
          确认提交购买
        </button></template
      >
    </Modal>
  </main>
</template>

<style scoped>
.member-seckill {
  max-width: 1120px;
  margin: 0 auto;
  padding: 36px 24px 72px;
  color: var(--text);
}
.seckill-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 28px;
}
h1 {
  font-size: clamp(26px, 4vw, 36px);
  margin: 8px 0;
}
h2 {
  font-size: 21px;
  margin: 12px 0;
}
p {
  line-height: 1.7;
  margin: 10px 0;
  overflow-wrap: anywhere;
}
.seckill-heading p,
.seckill-help,
.request-reference,
.activity-list p {
  color: var(--muted);
}
.pending-purchase,
.activity-detail {
  border-top: 1px solid var(--border);
  padding: 24px 0;
  margin-bottom: 24px;
}
.seckill-notice {
  padding: 14px 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.request-reference {
  font-size: 13px;
}
.request-reference span {
  display: block;
}
.quote-amounts {
  max-width: 400px;
  margin: 20px 0;
  font-variant-numeric: tabular-nums;
}
.quote-amounts div {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 7px 0;
}
.quote-amounts dd {
  margin: 0;
}
.seckill-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 18px 0;
}
.activity-list {
  padding: 0;
  margin: 0;
  list-style: none;
}
.activity-list li {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 0;
  border-top: 1px solid var(--border);
}
.activity-list a {
  color: inherit;
}
.activity-state {
  font-size: 13px;
  color: var(--primary);
}
.activity-price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 16px;
  flex-shrink: 0;
}
.activity-price strong {
  font-size: 24px;
  color: var(--primary);
  font-variant-numeric: tabular-nums;
}
.activity-price small {
  font-size: 13px;
  font-weight: 400;
}
fieldset {
  border: 0;
  margin: 24px 0 0;
  padding: 0;
  max-width: 640px;
}
label {
  display: block;
  margin: 18px 0;
}
select,
input {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text);
  font: inherit;
}
input[type="number"] {
  max-width: 140px;
}
:is(button, a, input, select):focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}
button:disabled,
fieldset:disabled {
  opacity: 0.6;
}
button:disabled {
  cursor: not-allowed;
}
@media (max-width: 600px) {
  .member-seckill {
    padding: 24px 16px 48px;
  }
  .activity-list li {
    flex-direction: column;
    gap: 12px;
  }
  .activity-price {
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
  }
  .seckill-heading {
    align-items: flex-start;
  }
  .seckill-actions > :is(a, button) {
    min-height: 44px;
  }
}
</style>
