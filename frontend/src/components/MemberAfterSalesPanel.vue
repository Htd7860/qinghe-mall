<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import Empty from "./Empty.vue";
import Modal from "./Modal.vue";
import { ApiError } from "../api/http";
import { context, credentials } from "../api/session";
import { memberAreas, type MemberArea } from "../api/member-profile";
import {
  readMemberOrder,
  type MemberOrder,
  type OrderItem,
} from "../api/member-order";
import {
  afterSaleStates,
  afterSaleActions,
  type AfterSaleSummary,
  type AfterSaleDetail,
  type AfterSaleLog,
  type ReturnAddress,
} from "../api/aftersale-contract";
import {
  afterSaleMemberIdentity,
  readMemberAfterSales,
  readMemberAfterSale,
  readMemberAfterSaleLogs,
  memberAfterSaleApplicationResult,
  memberAfterSaleDecisionResult,
  createMemberAfterSale,
  changeMemberAfterSale,
  memberReturnAddress,
  memberAfterSaleCarriers,
  readMemberAfterSalePending,
  saveMemberAfterSalePending,
  clearMemberAfterSalePending,
  type ApplyCommand,
  type MemberDecisionCommand,
  type MemberAfterSalePending,
  type ApplyPending,
  type DecisionPending,
  type Carrier,
} from "../api/member-aftersale";

type OriginalOrder = Pick<
  MemberOrder,
  | "id"
  | "no"
  | "status"
  | "payStatus"
  | "authVersion"
  | "serverNow"
  | "receivedAt"
  | "deliveredAt"
  | "items"
>;
type Draft =
  | { kind: "create"; body: ApplyCommand; orderNo: string }
  | { kind: "delivery" | "cancel"; body: MemberDecisionCommand; no: string };
const route = useRoute(),
  base = computed(() => `/shop/${context.tenant}`);
const rows = ref<AfterSaleSummary[]>([]),
  page = ref(1),
  total = ref(0),
  state = ref("");
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  modalError = ref(""),
  storageError = ref("");
const detail = ref<AfterSaleDetail | null>(null),
  order = ref<OriginalOrder | null>(null),
  mode = ref<"create" | "delivery" | "cancel" | null>(null);
const logs = ref<AfterSaleLog[]>([]),
  logPage = ref(1),
  logTotal = ref(0),
  logError = ref("");
const address = ref<ReturnAddress | null>(null),
  areas = ref<MemberArea[]>([]),
  carriers = ref<Carrier[]>([]);
const draft = ref<Draft | null>(null),
  pending = ref<MemberAfterSalePending | null>(null);
const applyForm = reactive({
  itemId: 0,
  count: "1",
  way: "10",
  reason: "",
  description: "",
});
const actionForm = reactive({ carrierId: 0, tracking: "", reason: "" });
const locked = computed(() => loading.value || busy.value),
  mayWrite = computed(() => !pending.value && !storageError.value);
const title = computed(() =>
  mode.value === "create"
    ? "申请售后"
    : mode.value === "delivery"
      ? "填写寄回物流"
      : mode.value === "cancel"
        ? "撤销售后申请"
        : "售后详情",
);
const selectedItem = computed(() =>
  order.value?.items.find((x) => x.id === applyForm.itemId),
);
const draftCarrierName = computed(() => {
  const value = draft.value;
  if (!value || value.kind !== "delivery") return "";
  const id = value.body.logisticsId;
  return carriers.value.find((x) => x.id === id)?.name || "";
});
const areaMap = computed(() => new Map(areas.value.map((x) => [x.id, x])));
const name = (n: number) => afterSaleStates[n] || "状态待确认";
const money = (n: number) =>
  `${Math.floor(n / 100)}.${String(n % 100).padStart(2, "0")}`;
const time = (n: number | null) =>
  n === null
    ? "—"
    : new Intl.DateTimeFormat("zh-CN", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      }).format(n);
const message = (e: unknown) =>
  e instanceof Error ? e.message : "操作未完成，请重新查询";
const canCancel = (x: AfterSaleDetail) =>
  ([10, 20].includes(x.status) || (x.status === 40 && x.way === 10)) &&
  x.deliveredAt === null &&
  x.inspectedAt === null;
const canDeliver = (x: AfterSaleDetail) =>
  x.way === 20 &&
  x.status === 20 &&
  x.returnDeadline !== null &&
  x.serverNow < x.returnDeadline;
let epoch = 0,
  sequence = 0,
  modalSequence = 0;
const live = (ticket: number, serial: number) =>
  ticket === epoch && serial === modalSequence;
function original(x: MemberOrder): OriginalOrder {
  return {
    id: x.id,
    no: x.no,
    status: x.status,
    payStatus: x.payStatus,
    authVersion: x.authVersion,
    serverNow: x.serverNow,
    receivedAt: x.receivedAt,
    deliveredAt: x.deliveredAt,
    items: x.items,
  };
}
function eligible(x: OriginalOrder) {
  if (!x.payStatus || ![10, 20, 30].includes(x.status))
    throw new ApiError("此订单当前不能申请售后，请查看最新订单进度", 409);
  if (
    x.status === 30 &&
    (x.receivedAt === null || x.serverNow >= x.receivedAt + 7 * 86400000)
  )
    throw new ApiError("收货后七天申请期限已过，请联系商家核查", 409);
}
function region(id: number) {
  const values: string[] = [],
    seen = new Set<number>();
  let item = areaMap.value.get(id);
  while (item && !seen.has(item.id)) {
    seen.add(item.id);
    values.unshift(item.name);
    item = areaMap.value.get(item.parentId);
  }
  return values.join(" / ");
}
function resetForm() {
  draft.value = null;
  mode.value = null;
  address.value = null;
  carriers.value = [];
  modalError.value = "";
  Object.assign(applyForm, {
    itemId: 0,
    count: "1",
    way: "10",
    reason: "",
    description: "",
  });
  Object.assign(actionForm, { carrierId: 0, tracking: "", reason: "" });
}
function closeModal() {
  modalSequence++;
  detail.value = null;
  order.value = null;
  logs.value = [];
  logTotal.value = 0;
  logError.value = "";
  busy.value = false;
  resetForm();
}
function accessError(e: unknown) {
  if (e instanceof ApiError && [401, 403].includes(e.code)) {
    epoch++;
    sequence++;
    loading.value = false;
    rows.value = [];
    total.value = 0;
    closeModal();
    areas.value = [];
  }
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
async function load() {
  if (locked.value) return;
  const ticket = epoch,
    serial = ++sequence;
  loading.value = true;
  rows.value = [];
  total.value = 0;
  error.value = "";
  try {
    const value = await readMemberAfterSales(page.value, state.value);
    if (ticket !== epoch || serial !== sequence) return;
    rows.value = value.list;
    total.value = value.total;
  } catch (e) {
    if (ticket === epoch && serial === sequence) {
      error.value = message(e);
      accessError(e);
    }
  } finally {
    if (ticket === epoch && serial === sequence) loading.value = false;
  }
}
async function readLogs() {
  if (!detail.value || locked.value) return;
  const id = detail.value.id,
    ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  logs.value = [];
  logTotal.value = 0;
  logError.value = "";
  try {
    const value = await readMemberAfterSaleLogs(id, logPage.value);
    if (!live(ticket, serial)) return;
    logs.value = value.list;
    logTotal.value = value.total;
  } catch (e) {
    if (live(ticket, serial)) {
      logError.value = message(e);
      accessError(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
}
async function openCase(id: number) {
  if (locked.value) return;
  closeModal();
  const ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  error.value = "";
  try {
    const value = await readMemberAfterSale(id);
    if (!live(ticket, serial)) return;
    detail.value = value;
    logPage.value = 1;
  } catch (e) {
    if (live(ticket, serial)) {
      error.value = message(e);
      accessError(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
  if (live(ticket, serial) && detail.value) await readLogs();
}
async function openApply(id: number) {
  if (locked.value || !mayWrite.value) return;
  closeModal();
  const ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  error.value = "";
  try {
    const value = original(await readMemberOrder(id));
    if (!live(ticket, serial)) return;
    eligible(value);
    if (!value.items.some((x) => x.count > x.refundCount))
      throw new ApiError("原订单已无可申请的商品数量", 409);
    order.value = value;
    mode.value = "create";
    applyForm.itemId = value.items.find((x) => x.count > x.refundCount)!.id;
  } catch (e) {
    if (live(ticket, serial)) {
      error.value = message(e);
      accessError(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
}
async function openAction(kind: "delivery" | "cancel") {
  if (locked.value || !detail.value || !mayWrite.value) return;
  const id = detail.value.id,
    ticket = epoch,
    serial = ++modalSequence;
  busy.value = true;
  modalError.value = "";
  try {
    const value = await readMemberAfterSale(id);
    if (!live(ticket, serial)) return;
    detail.value = value;
    if (kind === "cancel" ? !canCancel(value) : !canDeliver(value))
      throw new ApiError("当前阶段不能执行此操作，请核对最新进度", 409);
    resetForm();
    if (kind === "delivery") {
      const [destination, options, regions] = await Promise.all([
        memberReturnAddress(id),
        memberAfterSaleCarriers(),
        areas.value.length ? Promise.resolve(areas.value) : memberAreas(),
      ]);
      if (!live(ticket, serial)) return;
      if (!regions.some((x) => x.id === destination.areaId))
        throw new ApiError("寄回地区信息不完整，请联系商家核对", 503);
      address.value = destination;
      carriers.value = options;
      areas.value = regions;
    }
    mode.value = kind;
    logs.value = [];
  } catch (e) {
    if (live(ticket, serial)) {
      modalError.value = message(e);
      accessError(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
}
function preview() {
  if (locked.value || !mayWrite.value || !mode.value) return;
  modalError.value = "";
  try {
    if (mode.value === "create") {
      if (!order.value || !selectedItem.value)
        throw new Error("请选择原订单商品");
      eligible(order.value);
      if (order.value.authVersion >= Number.MAX_SAFE_INTEGER)
        throw new Error("原订单记录需核查，请联系商家");
      if (!/^[1-9][0-9]{0,3}$/.test(applyForm.count))
        throw new Error("申请数量请填写1至9999的整数");
      const count = Number(applyForm.count),
        item = selectedItem.value,
        reason = applyForm.reason.trim(),
        description = applyForm.description.trim();
      if (count > item.count - item.refundCount)
        throw new Error("申请数量不能超过购买数量扣除已退数量");
      const way = Number(applyForm.way);
      if (
        ![10, 20].includes(way) ||
        (way === 20 && order.value.deliveredAt === null)
      )
        throw new Error("未发货订单仅支持退款");
      if (
        !reason ||
        reason.length > 200 ||
        /[\x00-\x1f\x7f]/.test(reason) ||
        description.length > 500 ||
        /[\x00-\x09\x0b\x0c\x0e-\x1f\x7f]/.test(description)
      )
        throw new Error("请填写有效的申请原因和说明");
      draft.value = {
        kind: "create",
        orderNo: order.value.no,
        body: {
          requestId: crypto.randomUUID(),
          orderId: order.value.id,
          orderItemId: item.id,
          expectedVersion: order.value.authVersion,
          count,
          way,
          applyReason: reason,
          applyDescription: description,
        },
      };
    } else {
      if (!detail.value) throw new Error("请重新查询本人售后");
      if (detail.value.authVersion >= Number.MAX_SAFE_INTEGER)
        throw new Error("原售后记录需核查，请联系商家");
      const body: MemberDecisionCommand = {
        id: detail.value.id,
        expectedVersion: detail.value.authVersion,
        requestId: crypto.randomUUID(),
        qualityApproved: false,
        reason:
          mode.value === "delivery"
            ? "买家提交退货物流"
            : actionForm.reason.trim(),
      };
      if (mode.value === "delivery") {
        if (
          !address.value ||
          !carriers.value.some((x) => x.id === actionForm.carrierId) ||
          !/^[A-Za-z0-9][A-Za-z0-9-]{3,63}$/.test(actionForm.tracking)
        )
          throw new Error("请选择承运商并填写有效物流单号");
        body.logisticsId = actionForm.carrierId;
        body.logisticsNo = actionForm.tracking;
      } else if (
        !body.reason ||
        body.reason.length > 500 ||
        /[\x00-\x1f\x7f]/.test(body.reason)
      )
        throw new Error("请填写不超过500字、无换行的撤销原因");
      draft.value = { kind: mode.value, no: detail.value.no, body };
    }
  } catch (e) {
    modalError.value = message(e);
  }
}
async function submit() {
  if (locked.value || !mayWrite.value || !draft.value) return;
  const value = draft.value,
    ticket = epoch,
    serial = modalSequence;
  const marker: MemberAfterSalePending =
    value.kind === "create"
      ? {
          kind: "create",
          orderId: value.body.orderId,
          orderNo: value.orderNo,
          orderItemId: value.body.orderItemId,
          expectedVersion: value.body.expectedVersion,
          requestId: value.body.requestId,
          count: value.body.count,
          way: value.body.way,
        }
      : {
          kind: value.kind,
          id: value.body.id,
          no: value.no,
          expectedVersion: value.body.expectedVersion,
          requestId: value.body.requestId,
          toStatus: value.kind === "delivery" ? 30 : 61,
        };
  busy.value = true;
  modalError.value = "";
  let persisted = false,
    acknowledged = false,
    completed = false,
    caseId: number | null = null;
  try {
    if (value.kind === "create") {
      const current = original(await readMemberOrder(value.body.orderId));
      if (!live(ticket, serial)) return;
      order.value = current;
      eligible(current);
      if (current.authVersion !== value.body.expectedVersion) {
        draft.value = null;
        throw new ApiError("原订单已变化，请重新核对申请", 409);
      }
    } else {
      const current = await readMemberAfterSale(value.body.id);
      if (!live(ticket, serial)) return;
      detail.value = current;
      if (
        value.kind === "delivery" ? !canDeliver(current) : !canCancel(current)
      )
        resetForm();
      if (
        current.authVersion !== value.body.expectedVersion ||
        (value.kind === "delivery" ? !canDeliver(current) : !canCancel(current))
      ) {
        draft.value = null;
        throw new ApiError("售后阶段或期限已变化，请重新查询", 409);
      }
    }
    saveMemberAfterSalePending(marker);
    persisted = true;
    pending.value = marker;
    if (value.kind === "create") {
      const result = await createMemberAfterSale(value.body);
      acknowledged = true;
      if (!live(ticket, serial)) return;
      const found = await memberAfterSaleApplicationResult(
        marker as ApplyPending,
      );
      if (!live(ticket, serial)) return;
      if (!found || found.id !== result.id)
        throw new ApiError("原申请仍待确认，请查询处理结果", -2, true);
      caseId = found.id;
    } else {
      const result = await changeMemberAfterSale(value.kind, value.body);
      acknowledged = true;
      if (!live(ticket, serial)) return;
      caseId = result.id;
    }
    clearMemberAfterSalePending(marker);
    pending.value = null;
    completed = true;
    resetForm();
    notice.value =
      value.kind === "create"
        ? "申请已受理，请查看处理进度。"
        : "售后操作已记录，请查看最新进度。";
  } catch (e) {
    if (!live(ticket, serial)) return;
    if (!persisted) {
      try {
        pending.value = readMemberAfterSalePending();
      } catch (storage) {
        storageError.value = message(storage);
      }
    }
    if (
      persisted &&
      !acknowledged &&
      e instanceof ApiError &&
      !e.outcomeUnknown &&
      [400, 401, 403, 404, 409, 429].includes(e.code)
    ) {
      try {
        clearMemberAfterSalePending(marker);
        pending.value = null;
      } catch (storage) {
        storageError.value = message(storage);
      }
    }
    modalError.value = message(e);
    draft.value = null;
    accessError(e);
    if (pending.value) {
      resetForm();
      order.value = null;
      error.value = "处理结果待确认，请查询原请求后再继续。";
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
  if (completed && live(ticket, serial)) {
    if (caseId !== null) await openCase(caseId);
    if (ticket === epoch) await load();
  }
}
async function resolvePending() {
  const marker = pending.value;
  if (!marker || locked.value) return;
  closeModal();
  const ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  error.value = "";
  let resolved = false,
    caseId: number | null = null,
    foundReceipt = false;
  try {
    if (marker.kind === "create") {
      let found = await memberAfterSaleApplicationResult(marker);
      if (!live(ticket, serial)) return;
      if (!found) {
        const current = await readMemberOrder(marker.orderId);
        if (!live(ticket, serial)) return;
        if (current.authVersion <= marker.expectedVersion) {
          error.value = "原申请仍待确认，请稍后再查询。";
          return;
        }
        found = await memberAfterSaleApplicationResult(marker);
        if (!live(ticket, serial)) return;
      }
      caseId = found?.id ?? null;
      foundReceipt = found !== null;
    } else {
      let receipt = await memberAfterSaleDecisionResult(marker);
      if (!live(ticket, serial)) return;
      const current = await readMemberAfterSale(marker.id);
      if (!live(ticket, serial)) return;
      if (!receipt && current.authVersion > marker.expectedVersion) {
        receipt = await memberAfterSaleDecisionResult(marker);
        if (!live(ticket, serial)) return;
      }
      if (!receipt && current.authVersion <= marker.expectedVersion) {
        error.value = "原操作仍待确认，请稍后再查询。";
        return;
      }
      if (receipt && current.authVersion < receipt.authVersion)
        throw new ApiError("当前进度尚未确认，请保留原操作记录", 503);
      caseId = current.id;
      foundReceipt = receipt !== null;
    }
    clearMemberAfterSalePending(marker);
    pending.value = null;
    storageError.value = "";
    resolved = true;
    notice.value = foundReceipt
      ? "已查到原处理记录，请核对最新进度。"
      : "原记录已推进，请核对订单与售后历史后再继续。";
  } catch (e) {
    if (live(ticket, serial)) {
      error.value = message(e);
      accessError(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
  if (resolved && live(ticket, serial)) {
    if (caseId !== null) await openCase(caseId);
    if (ticket === epoch) await load();
  }
}
function clear() {
  epoch++;
  sequence++;
  closeModal();
  rows.value = [];
  total.value = 0;
  areas.value = [];
  loading.value = false;
  pending.value = null;
  error.value = "";
  notice.value = "";
  storageError.value = "";
}
watch(
  [
    afterSaleMemberIdentity,
    () => credentials().accessToken,
    () => route.query.order,
  ],
  async () => {
    clear();
    page.value = 1;
    try {
      pending.value = readMemberAfterSalePending();
    } catch (e) {
      storageError.value = message(e);
    }
    const ticket = epoch;
    await load();
    if (ticket !== epoch || !mayWrite.value) return;
    const id = route.query.order;
    if (id === undefined) return;
    if (
      typeof id !== "string" ||
      !/^[1-9][0-9]*$/.test(id) ||
      !Number.isSafeInteger(Number(id))
    ) {
      error.value = "订单编号不正确，请从本人订单进入。";
      return;
    }
    await openApply(Number(id));
  },
  { immediate: true },
);
onBeforeUnmount(clear);
</script>

<template>
  <main class="shop-container member-aftersales">
    <div class="shop-page-heading">
      <h1>退货退款</h1>
      <RouterLink class="button" :to="`${base}/orders`"
        >从我的订单申请</RouterLink
      ><button class="button" :disabled="locked" @click="load">刷新</button>
    </div>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="storageError" class="form-error" role="alert">
      {{ storageError }}
    </p>
    <section v-if="pending" class="notice" aria-label="待确认售后">
      <h2>上次处理结果待确认</h2>
      <p>{{ pending.kind === "create" ? pending.orderNo : pending.no }}</p>
      <button class="button" :disabled="locked" @click="resolvePending">
        查询原请求
      </button>
    </section>
    <form
      class="filters"
      @submit.prevent="
        page = 1;
        load();
      "
    >
      <label
        >售后状态<select v-model="state" :disabled="locked">
          <option value="">全部</option>
          <option
            v-for="(label, id) in afterSaleStates"
            :key="id"
            :value="String(id)"
          >
            {{ label }}
          </option>
        </select></label
      ><button class="button" :disabled="locked">查询</button>
    </form>
    <p v-if="loading" role="status">正在查询本人售后…</p>
    <article v-for="item in rows" :key="item.id" class="consumer-order">
      <header>
        <span>{{ item.no }}</span
        ><strong>{{ name(item.status) }}</strong>
      </header>
      <div class="case-body">
        <h2>{{ item.spuName }}</h2>
        <p>
          {{ item.way === 20 ? "退货退款" : "仅退款" }} · {{ item.count }} 件
        </p>
        <p>商品申请金额（不含运费） ¥{{ money(item.goodsRefundPrice) }}</p>
        <small>申请于 {{ time(item.createdAt) }}</small>
      </div>
      <footer>
        <RouterLink class="button" :to="`${base}/order/${item.orderId}`"
          >原订单</RouterLink
        ><button class="button" :disabled="locked" @click="openCase(item.id)">
          查看进度
        </button>
      </footer>
    </article>
    <Empty v-if="!loading && !rows.length && !error" title="暂无售后申请"
      ><RouterLink class="button" :to="`${base}/orders`"
        >查看我的订单</RouterLink
      ></Empty
    >
    <nav class="pagination" aria-label="本人售后分页">
      <button
        class="button"
        :disabled="locked || page <= 1"
        @click="
          page--;
          load();
        "
      >
        上一页</button
      ><span>第 {{ page }} 页 · 共 {{ total }} 条</span
      ><button
        class="button"
        :disabled="locked || page * 20 >= total"
        @click="
          page++;
          load();
        "
      >
        下一页
      </button>
    </nav>

    <Modal v-if="detail || order" :title="title" wide @close="closeModal">
      <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
      <template v-if="detail && !mode">
        <h3>{{ detail.spuName }}</h3>
        <p class="number">{{ detail.no }} · {{ name(detail.status) }}</p>
        <p>
          申请 {{ detail.count }} 件 · 商品申请金额（不含运费） ¥{{
            money(detail.goodsRefundPrice)
          }}
        </p>
        <div v-if="detail.refund">
          <p>
            退款合计 ¥{{ money(detail.refund.totalPrice) }}，其中运费 ¥{{
              money(detail.refund.deliveryPrice)
            }}。
          </p>
          <p>
            {{
              detail.refund.status === "SUCCESS"
                ? "退款结果已确认"
                : detail.refund.status === "FAILED"
                  ? "退款未完成，待核查"
                  : "退款结果待确认"
            }}
          </p>
          <template v-if="detail.refund.status === 'SUCCESS'">
            <p>
              库存恢复：{{
                detail.refund.restoreCount === 0
                  ? "无需恢复"
                  : detail.refund.stockRecovered
                    ? "已恢复"
                    : "待处理"
              }}（{{ detail.refund.restoreCount }} 件）
            </p>
            <p>
              优惠券：{{
                !detail.refund.couponReturnRequired
                  ? "无需退回"
                  : detail.refund.couponRecovered
                    ? "已处理"
                    : "待处理"
              }}
            </p>
          </template>
        </div>
        <dl class="facts">
          <div>
            <dt>申请原因</dt>
            <dd>{{ detail.applyReason }}</dd>
          </div>
          <div v-if="detail.applyDescription">
            <dt>补充说明</dt>
            <dd>{{ detail.applyDescription }}</dd>
          </div>
          <div v-if="detail.auditReason">
            <dt>审核意见</dt>
            <dd>{{ detail.auditReason }}</dd>
          </div>
          <div v-if="detail.returnDeadline">
            <dt>提交寄回物流截止</dt>
            <dd>{{ time(detail.returnDeadline) }}（北京时间）</dd>
          </div>
          <div v-if="detail.deliveredAt">
            <dt>寄回物流</dt>
            <dd>{{ detail.logisticsName }} · {{ detail.logisticsNo }}</dd>
          </div>
          <div v-if="detail.inspectedAt">
            <dt>验货记录</dt>
            <dd>
              实收 {{ detail.receivedCount }} 件，可售
              {{ detail.salableCount }} 件，不可售
              {{ detail.unsalableCount }} 件<small>{{
                detail.receiveReason
              }}</small>
            </dd>
          </div>
        </dl>
        <div class="actions">
          <button
            v-if="canDeliver(detail)"
            class="button primary"
            :disabled="locked || !mayWrite"
            @click="openAction('delivery')"
          >
            填写寄回物流</button
          ><button
            v-if="canCancel(detail)"
            class="button"
            :disabled="locked || !mayWrite"
            @click="openAction('cancel')"
          >
            撤销申请</button
          ><RouterLink class="button" :to="`${base}/order/${detail.orderId}`"
            >查看原订单</RouterLink
          >
        </div>
        <h3>处理记录</h3>
        <p v-if="logError" class="form-error" role="alert">{{ logError }}</p>
        <ol v-if="logs.length" class="history">
          <li v-for="entry in logs" :key="entry.id">
            <strong>{{ afterSaleActions[entry.action] }}</strong
            ><small>{{ time(entry.createdAt) }}</small>
            <p v-if="entry.reason">{{ entry.reason }}</p>
            <p v-if="entry.inspection">
              实收 {{ entry.inspection.receivedCount }} 件，可售
              {{ entry.inspection.salableCount }} 件，不可售
              {{ entry.inspection.unsalableCount }} 件
            </p>
          </li>
        </ol>
        <p v-if="busy" role="status">正在读取…</p>
        <nav class="pagination" aria-label="售后历史分页">
          <button
            class="button"
            :disabled="locked || logPage <= 1"
            @click="
              logPage--;
              readLogs();
            "
          >
            上一页</button
          ><span>第 {{ logPage }} 页 · 共 {{ logTotal }} 条</span
          ><button
            class="button"
            :disabled="locked || logPage * 20 >= logTotal"
            @click="
              logPage++;
              readLogs();
            "
          >
            下一页
          </button>
        </nav>
      </template>
      <form v-else-if="!draft" @submit.prevent="preview">
        <fieldset class="fields" :disabled="locked">
          <template v-if="mode === 'create' && order">
            <p class="number">原订单 {{ order.no }}</p>
            <label
              >申请商品<select v-model.number="applyForm.itemId">
                <option
                  v-for="item in order.items.filter(
                    (x) => x.count > x.refundCount,
                  )"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.spuName }} · 购买 {{ item.count }} 件，已退
                  {{ item.refundCount }} 件
                </option>
              </select></label
            >
            <label
              >申请数量<input
                v-model="applyForm.count"
                inputmode="numeric"
                maxlength="4"
                required /></label
            ><label
              >售后方式<select v-model="applyForm.way">
                <option value="10">仅退款</option>
                <option v-if="order.deliveredAt !== null" value="20">
                  退货退款
                </option>
              </select></label
            >
            <label
              >申请原因<input
                v-model="applyForm.reason"
                maxlength="200"
                required /></label
            ><label
              >补充说明<textarea
                v-model="applyForm.description"
                maxlength="500"
                rows="3"
              />
            </label>
            <p>
              退款按原成交优惠分摊，提交后显示申请金额；已有进行中售后的商品需先处理原申请。
            </p>
          </template>
          <template v-else-if="mode === 'delivery' && address && detail">
            <h3>请寄回至商家确认的地址</h3>
            <p>{{ address.name }} · {{ address.mobile }}</p>
            <p>{{ region(address.areaId) }} {{ address.detail }}</p>
            <p>
              请在 {{ time(detail.returnDeadline) }}（北京时间）前提交物流。
            </p>
            <label
              >承运商<select v-model.number="actionForm.carrierId" required>
                <option :value="0">请选择</option>
                <option
                  v-for="item in carriers"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.name }}
                </option>
              </select></label
            ><label
              >物流单号<input
                v-model="actionForm.tracking"
                maxlength="64"
                autocomplete="off"
                required
            /></label>
          </template>
          <template v-else-if="mode === 'cancel'"
            ><p>撤销后本次申请将关闭。再次申请仍须满足原售后期限。</p>
            <label
              >撤销原因<input
                v-model="actionForm.reason"
                maxlength="500"
                required /></label
          ></template>
        </fieldset>
        <div class="actions">
          <button class="button primary" :disabled="locked || !mayWrite">
            核对本次操作</button
          ><button type="button" class="button" @click="closeModal">
            返回
          </button>
        </div>
      </form>
      <div v-else class="confirmation">
        <h3>确认本次操作</h3>
        <template v-if="draft.kind === 'create'"
          ><p>
            {{ selectedItem?.spuName }} · {{ draft.body.count }} 件 ·
            {{ draft.body.way === 20 ? "退货退款" : "仅退款" }}
          </p>
          <p>原因：{{ draft.body.applyReason }}</p>
          <p v-if="draft.body.applyDescription">
            说明：{{ draft.body.applyDescription }}
          </p></template
        ><template v-else-if="draft.kind === 'delivery'"
          ><p>{{ draft.no }}</p>
          <p>{{ draftCarrierName }} · {{ draft.body.logisticsNo }}</p></template
        ><template v-else
          ><p>确认撤销 {{ draft.no }}？</p>
          <p>原因：{{ draft.body.reason }}</p></template
        >
        <div class="actions">
          <button
            class="button primary"
            :disabled="locked || !mayWrite"
            @click="submit"
          >
            {{
              busy
                ? "正在处理…"
                : draft.kind === "create"
                  ? "确认申请"
                  : draft.kind === "delivery"
                    ? "确认寄回物流"
                    : "确认撤销申请"
            }}</button
          ><button class="button" :disabled="locked" @click="draft = null">
            返回修改
          </button>
        </div>
      </div>
    </Modal>
  </main>
</template>

<style scoped>
.member-aftersales {
  padding-bottom: 40px;
}
.shop-page-heading,
.filters,
.actions,
.pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.shop-page-heading h1 {
  margin-right: auto;
}
.filters {
  margin: 20px 0;
}
.filters label,
.fields label {
  display: grid;
  gap: 8px;
}
.fields {
  display: grid;
  gap: 16px;
  border: 0;
  padding: 0;
}
.notice,
.confirmation {
  background: var(--primary-soft);
  padding: 18px;
  margin: 20px 0;
}
.notice h2,
.case-body h2 {
  font-size: 16px;
}
.case-body {
  padding: 20px 24px;
}
.consumer-order header {
  gap: 12px;
  flex-wrap: wrap;
}
.consumer-order footer {
  gap: 12px;
  justify-content: flex-end;
}
.number,
p,
dd,
header {
  overflow-wrap: anywhere;
}
small {
  display: block;
  color: var(--muted);
  line-height: 1.7;
  margin-top: 6px;
}
.pagination {
  justify-content: center;
  margin: 20px 0;
}
.actions {
  margin: 20px 0;
}
.facts {
  display: grid;
  gap: 14px;
}
.facts > div {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  gap: 12px;
}
dt {
  color: var(--muted);
}
dd {
  margin: 0;
  white-space: pre-wrap;
}
.history {
  padding-left: 22px;
}
.history li {
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}
h3 {
  margin-top: 24px;
}
.confirmation p {
  white-space: pre-wrap;
}
@media (max-width: 600px) {
  .shop-page-heading {
    align-items: flex-start;
  }
  .facts > div {
    grid-template-columns: 1fr;
    gap: 5px;
  }
  .fields input,
  .fields textarea,
  .fields select {
    font-size: 16px;
  }
  .actions .button {
    flex: 1;
  }
}
</style>
