<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import OrderOperatingSummary from "../components/OrderOperatingSummary.vue";
import Empty from "../components/Empty.vue";
import Modal from "../components/Modal.vue";
import { allowed } from "../api/management";
import { ApiError } from "../api/http";
import { identities } from "../api/session";
import {
  merchantOrderIdentity,
  merchantOrderPage,
  merchantOrderDetail,
  merchantDeliveryDetail,
  merchantCarriers,
  merchantParcel,
  merchantOrderLogs,
  type MerchantOrder,
  type MerchantDetail,
  type DeliveryDetail,
  type Carrier,
  type OrderLog,
  type ParcelCommand,
} from "../api/merchant-order";

const route = useRoute();
const rows = ref<MerchantOrder[]>([]),
  total = ref(0),
  pageNo = ref(1),
  search = ref(""),
  state = ref("");
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  modalError = ref("");
const detail = ref<MerchantDetail | null>(null),
  delivery = ref<DeliveryDetail | null>(null),
  carriers = ref<Carrier[]>([]);
const mode = ref<"delivery" | "correction" | null>(null),
  confirming = ref(false);
const form = reactive({ logisticsId: 0, logisticsNo: "", reason: "" });
const logs = ref<OrderLog[]>([]),
  logPage = ref(1),
  logTotal = ref(0),
  logError = ref("");
const unresolved = ref<{ id: number; no: string } | null>(null);
const locked = computed(() => loading.value || busy.value);
const can = (p: string) => allowed(`trade:order:${p}`);
const states: Record<number, string> = {
  0: "待付款",
  10: "待发货",
  20: "已发货",
  30: "已完成",
  40: "已关闭",
};
const statusName = (s: number) => states[s] || "状态待确认";
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
        second: "2-digit",
        hour12: false,
      }).format(n);
const spec = (
  item: MerchantOrder["items"][number] | DeliveryDetail["items"][number],
) =>
  item.properties
    ?.map((p) => `${p.propertyName}：${p.valueName}`)
    .join(" / ") || "";
const actions: Record<string, string> = {
  AFTERSALE_APPLY: "买家申请售后",
  AFTERSALE_APPROVE: "同意售后申请",
  AFTERSALE_REJECT: "拒绝售后申请",
  AFTERSALE_CANCEL: "买家撤销售后",
  AFTERSALE_DELIVERY: "买家提交退货物流",
  AFTERSALE_INSPECT: "记录退货验收",
  AFTERSALE_REFUND_PREPARE: "进入退款处理",
  AFTERSALE_REFUND_EXECUTE: "确认执行退款",
  AFTERSALE_REFUND_RETRY: "申请原号重试",
  AFTERSALE_REFUND_CONTINUE: "申请增加原号提交机会",
  AFTERSALE_REFUND_SUCCESS: "确认退款结果",
  AFTERSALE_STOCK_RETURN: "售后库存恢复",
  AFTERSALE_COUPON_RETURN: "售后原券处理",
  AFTERSALE_EXPIRE: "寄回超期关闭",
  CREATE: "创建订单",
  PAY_BIND: "关联付款",
  FREE_PAY: "零元确认",
  PAY_CONFIRM: "确认付款",
  MEMBER_CANCEL: "买家取消",
  TIMEOUT_CANCEL: "超时取消",
  CANCEL_RELEASED: "完成资源处理",
  ADMIN_DELIVERY: "商家发货",
  ADMIN_LOGISTICS_UPDATE: "更正物流",
  MEMBER_RECEIVE: "买家收货",
  SYSTEM_RECEIVE: "自动收货",
};
const actionName = (s: string) => actions[s] || s;
let epoch = 0,
  readSequence = 0,
  modalSequence = 0;
function message(e: unknown) {
  return e instanceof Error ? e.message : "操作未完成，请查询原订单";
}
function fail(e: unknown) {
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
async function load() {
  const ticket = epoch,
    serial = ++readSequence;
  loading.value = true;
  error.value = "";
  rows.value = [];
  total.value = 0;
  try {
    const result = await merchantOrderPage(
      pageNo.value,
      search.value.trim(),
      state.value,
    );
    if (ticket !== epoch || serial !== readSequence) return;
    rows.value = result.list;
    total.value = result.total;
  } catch (e) {
    if (ticket === epoch && serial === readSequence) {
      error.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === readSequence) loading.value = false;
  }
}
async function readLogs() {
  if (!detail.value) return;
  const ticket = epoch,
    serial = modalSequence,
    id = detail.value.id;
  logs.value = [];
  logError.value = "";
  busy.value = true;
  try {
    const result = await merchantOrderLogs(id, logPage.value);
    if (ticket !== epoch || serial !== modalSequence) return;
    logs.value = result.list;
    logTotal.value = result.total;
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      logError.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) busy.value = false;
  }
}
async function openDetail(id: number) {
  if (locked.value) return;
  const ticket = epoch,
    serial = ++modalSequence;
  busy.value = true;
  error.value = "";
  try {
    const result = await merchantOrderDetail(id);
    if (ticket !== epoch || serial !== modalSequence) return;
    detail.value = result;
    logPage.value = 1;
    logTotal.value = 0;
    logs.value = [];
    if (unresolved.value?.id === id) {
      unresolved.value = null;
      notice.value = "已读取原订单最新状态，请核对物流与操作历史后再继续。";
    }
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      error.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) busy.value = false;
  }
  if (ticket === epoch && serial === modalSequence && detail.value?.id === id)
    await readLogs();
}
async function openParcel(id: number, correction: boolean) {
  if (locked.value || unresolved.value) return;
  const permission = correction ? "update-logistics" : "delivery";
  if (!can(permission)) return;
  const ticket = epoch,
    serial = ++modalSequence;
  busy.value = true;
  error.value = "";
  modalError.value = "";
  try {
    // The latest original snapshot determines the version; no address editing or current-product substitution.
    const current = await merchantOrderDetail(id);
    if (ticket !== epoch || serial !== modalSequence) return;
    if (
      correction
        ? ![20, 30].includes(current.status)
        : current.status !== 10 ||
          !current.payStatus ||
          current.afterSalePendingCount > 0
    )
      throw new ApiError("订单状态已变化或有进行中售后，请重新查询", 409);
    const recipient = correction ? null : await merchantDeliveryDetail(id);
    if (ticket !== epoch || serial !== modalSequence) return;
    if (recipient && recipient.authVersion !== current.authVersion)
      throw new ApiError("发货资料已变化，请重新读取", 409);
    const options = await merchantCarriers();
    if (ticket !== epoch || serial !== modalSequence) return;
    detail.value = current;
    delivery.value = recipient;
    carriers.value = options;
    if (
      correction &&
      current.logisticsId !== null &&
      !options.some((c) => c.id === current.logisticsId)
    )
      carriers.value.unshift({
        id: current.logisticsId,
        code: "HISTORY",
        name: current.logisticsName!,
      });
    form.logisticsId = correction ? current.logisticsId! : 0;
    form.logisticsNo = correction ? current.logisticsNo! : "";
    form.reason = "";
    mode.value = correction ? "correction" : "delivery";
    confirming.value = false;
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      error.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) busy.value = false;
  }
}
function review() {
  modalError.value = "";
  if (
    !carriers.value.some((c) => c.id === form.logisticsId) ||
    !/^[A-Za-z0-9][A-Za-z0-9-]{3,63}$/.test(form.logisticsNo.trim()) ||
    (mode.value === "correction" &&
      (!form.reason.trim() ||
        form.reason.trim().length > 500 ||
        /[\x00-\x1f\x7f]/.test(form.reason)))
  ) {
    modalError.value = "请填写承运商、4～64位字母数字单号及有效更正原因。";
    return;
  }
  confirming.value = true;
}
async function submit() {
  if (
    !mode.value ||
    !detail.value ||
    !confirming.value ||
    locked.value ||
    unresolved.value
  )
    return;
  const ticket = epoch,
    original = detail.value,
    correction = mode.value === "correction";
  const command: ParcelCommand = {
    id: original.id,
    expectedVersion: original.authVersion,
    logisticsId: form.logisticsId,
    logisticsNo: form.logisticsNo.trim(),
    ...(correction ? { reason: form.reason.trim() } : {}),
  };
  busy.value = true;
  modalError.value = "";
  let accepted = false;
  try {
    await merchantParcel(command, correction);
    if (ticket !== epoch) return;
    accepted = true;
    notice.value = correction ? "物流更正已保存。" : "发货已保存。";
  } catch (e) {
    if (ticket !== epoch) return;
    modalError.value = message(e);
    fail(e);
    if (!(e instanceof ApiError) || e.outcomeUnknown) {
      unresolved.value = { id: original.id, no: original.no };
      notice.value = "操作结果待确认，请先查询原订单，不要重复发货或更正。";
    }
  } finally {
    if (ticket === epoch) {
      busy.value = false;
      if (accepted || unresolved.value) {
        close();
        await load();
        await openDetail(original.id);
      }
    }
  }
}
function close() {
  if (busy.value) return;
  modalSequence++;
  detail.value = null;
  delivery.value = null;
  mode.value = null;
  confirming.value = false;
  carriers.value = [];
  logs.value = [];
  logError.value = "";
  modalError.value = "";
  form.logisticsNo = "";
  form.reason = "";
}
function clear() {
  epoch++;
  readSequence++;
  modalSequence++;
  rows.value = [];
  total.value = 0;
  detail.value = null;
  delivery.value = null;
  carriers.value = [];
  logs.value = [];
  mode.value = null;
  confirming.value = false;
  busy.value = false;
  loading.value = false;
  unresolved.value = null;
  error.value = "";
  notice.value = "";
  form.logisticsNo = "";
  form.reason = "";
}
watch(
  merchantOrderIdentity,
  () => {
    clear();
    pageNo.value = 1;
    search.value = "";
    state.value = route.query.status === "待发货" ? "10" : "";
    if (can("query")) void load();
  },
  { immediate: true },
);
watch(
  () => identities.merchant?.permissions.join("|"),
  () => {
    if (!can("query")) {
      clear();
      return;
    }
    if (
      mode.value &&
      !can(mode.value === "delivery" ? "delivery" : "update-logistics")
    ) {
      clear();
      void load();
    }
  },
);
onBeforeUnmount(clear);
</script>

<template>
  <PageHeader title="订单管理" eyebrow="店铺经营"
    ><button class="button" :disabled="locked" @click="load">
      刷新订单
    </button></PageHeader
  >
  <OrderOperatingSummary />
  <div class="panel">
    <form
      class="management-filters"
      @submit.prevent="
        pageNo = 1;
        load();
      "
    >
      <label
        >完整订单号<input
          v-model="search"
          maxlength="34"
          placeholder="QH…"
          :disabled="locked"
      /></label>
      <label
        >订单状态<select v-model="state" :disabled="locked">
          <option value="">全部</option>
          <option v-for="s in [0, 10, 20, 30, 40]" :key="s" :value="String(s)">
            {{ statusName(s) }}
          </option>
        </select></label
      >
      <button class="button" :disabled="locked">查询</button>
    </form>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <div v-if="unresolved" class="order-pending" role="alert">
      订单 {{ unresolved.no }} 的操作结果待确认。<button
        class="button"
        :disabled="locked"
        @click="openDetail(unresolved.id)"
      >
        查询原订单
      </button>
    </div>
    <p v-if="loading" role="status">正在查询订单…</p>
    <div v-else-if="rows.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>订单 / 下单时间</th>
            <th>商品</th>
            <th>应付 / 已退款</th>
            <th>状态</th>
            <th>物流</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td class="order-number">
              {{ row.no }}<small>{{ time(row.createdAt) }}</small>
            </td>
            <td>
              <div v-for="item in row.items" :key="item.id">
                {{ item.spuName }} × {{ item.count
                }}<small>{{ spec(item) }}</small>
              </div>
              <small v-if="row.itemCount > row.items.length"
                >另有 {{ row.itemCount - row.items.length }} 项</small
              >
            </td>
            <td>
              ¥{{ money(row.payPrice)
              }}<small>已退款 ¥{{ money(row.refundPrice) }}</small>
            </td>
            <td>
              <span class="tag">{{ statusName(row.status) }}</span
              ><small v-if="row.afterSalePendingCount">有进行中售后</small>
            </td>
            <td>
              {{ row.logisticsName || "—" }}<small>{{ row.logisticsNo }}</small>
            </td>
            <td>
              <div class="management-row-actions">
                <button
                  class="text-link"
                  :disabled="locked"
                  @click="openDetail(row.id)"
                >
                  详情 / 历史
                </button>
                <button
                  v-if="row.status === 10 && row.payStatus && can('delivery')"
                  class="text-link"
                  :disabled="
                    locked || !!unresolved || row.afterSalePendingCount > 0
                  "
                  @click="openParcel(row.id, false)"
                >
                  发货
                </button>
                <button
                  v-if="
                    [20, 30].includes(row.status) && can('update-logistics')
                  "
                  class="text-link"
                  :disabled="locked || !!unresolved"
                  @click="openParcel(row.id, true)"
                >
                  更正物流
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Empty v-else-if="!error" title="暂无符合条件的订单" icon="order" />
    <div class="order-pagination">
      <button
        class="button"
        :disabled="locked || pageNo === 1"
        @click="
          pageNo--;
          load();
        "
      >
        上一页</button
      ><span>第 {{ pageNo }} 页 · 共 {{ total }} 单</span
      ><button
        class="button"
        :disabled="locked || pageNo * 20 >= total || pageNo >= 5001"
        @click="
          pageNo++;
          load();
        "
      >
        下一页
      </button>
    </div>
    <p v-if="pageNo >= 5001 && pageNo * 20 < total">
      已到查询范围上限，请按订单号或状态缩小范围。
    </p>
  </div>
  <Modal
    v-if="detail"
    :title="
      mode === 'delivery'
        ? '订单发货'
        : mode === 'correction'
          ? '更正物流信息'
          : '订单详情'
    "
    wide
    @close="close"
  >
    <p class="order-number">
      {{ detail.no }} · {{ statusName(detail.status) }}
    </p>
    <template v-if="mode">
      <section v-if="delivery" class="delivery-address">
        <h3>本次收件信息</h3>
        <p>{{ delivery.receiverName }} · {{ delivery.receiverMobile }}</p>
        <p>
          {{ delivery.receiverAreaName }} {{ delivery.receiverDetailAddress }}
        </p>
        <p v-if="delivery.userRemark">买家备注：{{ delivery.userRemark }}</p>
        <ul>
          <li v-for="item in delivery.items" :key="item.id">
            {{ item.spuName }} · {{ spec(item) }} · 本次发
            {{ item.count - item.refundCount }} 件
          </li>
        </ul>
      </section>
      <p v-if="mode === 'correction'">
        原物流：{{ detail.logisticsName }} ·
        {{ detail.logisticsNo }}。更正不改变首次发货时间。
      </p>
      <form id="parcel-form" @submit.prevent="confirming ? submit() : review()">
        <fieldset :disabled="busy || confirming" class="parcel-fields">
          <label
            >承运商<select v-model="form.logisticsId" required>
              <option :value="0" disabled>请选择承运商</option>
              <option
                v-for="carrier in carriers"
                :key="carrier.id"
                :value="carrier.id"
              >
                {{ carrier.name }}
              </option>
            </select></label
          >
          <label
            >快递单号<input
              v-model="form.logisticsNo"
              maxlength="64"
              required
              autocomplete="off"
          /></label>
          <label v-if="mode === 'correction'"
            >更正原因<textarea
              v-model="form.reason"
              maxlength="500"
              required
              rows="3"
            />
          </label>
        </fieldset>
        <p v-if="confirming" class="order-pending">
          请核对：{{ carriers.find((c) => c.id === form.logisticsId)?.name }} ·
          {{ form.logisticsNo.trim() }}。{{
            mode === "delivery"
              ? "确认后本次全部可履约商品记为已发货。"
              : "将保存更正前后的物流信息和原因。"
          }}
        </p>
        <p v-if="modalError" class="form-error" role="alert">
          {{ modalError }}
        </p>
      </form>
    </template>
    <template v-else>
      <p>
        收件人：{{ detail.receiverNameMasked }} ·
        {{ detail.receiverMobileMasked }} · {{ detail.receiverAreaName }}
      </p>
      <p>
        商品 ¥{{ money(detail.totalPrice) }} − 优惠 ¥{{
          money(detail.discountPrice)
        }}
        ＋ 运费 ¥{{ money(detail.deliveryPrice) }} ＝ 应付 ¥{{
          money(detail.payPrice)
        }}；已退款 ¥{{ money(detail.refundPrice) }}
      </p>
      <ul>
        <li v-for="item in detail.items" :key="item.id">
          {{ item.spuName }} · {{ spec(item) }} × {{ item.count }}，已发
          {{ item.deliveryCount }}，已退款 {{ item.refundCount }}；成交 ¥{{
            money(item.payPrice)
          }}
        </li>
      </ul>
      <p>
        付款：{{ time(detail.paidAt) }}；发货：{{
          time(detail.deliveredAt)
        }}；收货：{{ time(detail.receivedAt) }}（北京时间）
      </p>
      <p v-if="detail.logisticsId">
        {{ detail.logisticsName }} · {{ detail.logisticsNo }}
      </p>
      <h3>操作历史</h3>
      <p v-if="logError" class="form-error" role="alert">{{ logError }}</p>
      <ol class="order-history">
        <li v-for="log in logs" :key="log.id">
          <strong>{{ actionName(log.action) }}</strong> ·
          {{ time(log.createdAt)
          }}<small
            >{{
              log.actorType === "SYSTEM"
                ? "系统"
                : log.actorType === "ADMIN"
                  ? "员工"
                  : "买家"
            }}
            {{ log.actorId || "" }}</small
          >
          <p v-if="log.details">
            <template v-if="log.details.previousLogisticsNo"
              >原：{{ log.details.previousLogisticsName }} ·
              {{ log.details.previousLogisticsNo }} → </template
            >{{ log.details.logisticsName }} · {{ log.details.logisticsNo }}
          </p>
          <p v-if="log.details?.reason">原因：{{ log.details.reason }}</p>
        </li>
      </ol>
      <p v-if="!logs.length && !busy && !logError">暂无操作记录</p>
      <div class="order-pagination">
        <button
          class="button"
          :disabled="busy || logPage === 1"
          @click="
            logPage--;
            readLogs();
          "
        >
          上一页</button
        ><span>共 {{ logTotal }} 条</span
        ><button
          class="button"
          :disabled="busy || logPage * 20 >= logTotal || logPage >= 5001"
          @click="
            logPage++;
            readLogs();
          "
        >
          下一页</button
        ><button class="button" :disabled="busy" @click="readLogs">
          刷新历史
        </button>
      </div>
    </template>
    <template #footer
      ><button class="button" :disabled="busy" @click="close">关闭</button
      ><template v-if="mode"
        ><button
          v-if="confirming"
          class="button"
          :disabled="busy"
          @click="confirming = false"
        >
          返回修改</button
        ><button
          class="button primary"
          type="submit"
          form="parcel-form"
          :disabled="busy || !!unresolved"
        >
          {{
            busy
              ? "正在保存…"
              : confirming
                ? "确认保存"
                : mode === "delivery"
                  ? "核对发货信息"
                  : "核对更正信息"
          }}
        </button></template
      ></template
    >
  </Modal>
</template>

<style scoped>
small {
  display: block;
  color: var(--text-secondary, #6a7872);
  margin-top: 5px;
  line-height: 1.6;
}
.order-number {
  overflow-wrap: anywhere;
}
.order-pending {
  padding: 14px;
  background: #f4f8f5;
  border: 1px solid var(--border);
  margin: 16px 0;
}
.order-pagination {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 20px;
}
.parcel-fields {
  border: 0;
  padding: 0;
  display: grid;
  gap: 16px;
}
.parcel-fields label {
  display: grid;
  gap: 8px;
}
.delivery-address {
  padding: 16px;
  background: var(--workspace, #f4f6f5);
  margin: 16px 0;
}
.order-history {
  padding-left: 24px;
  line-height: 1.8;
}
.order-history li {
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
}
li,
p {
  overflow-wrap: anywhere;
}
table {
  min-width: 850px;
}
.management-filters input {
  min-width: 250px;
}
@media (max-width: 600px) {
  .management-filters input {
    min-width: 0;
    width: 100%;
  }
  .management-filters label {
    width: 100%;
  }
}
</style>
