<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import PageHeader from "../components/PageHeader.vue";
import Empty from "../components/Empty.vue";
import Modal from "../components/Modal.vue";
import { allowed } from "../api/management";
import { credentials, identities } from "../api/session";
import { ApiError } from "../api/http";
import {
  financialRefundResult,
  refundPreparationResult,
  prepareFinancialRefund,
  commandFinancialRefund,
  retryFinancialRefund,
  refundRetryResult,
  financialRefundBudget,
  continueFinancialRefund,
  financialContinuationResult,
  type RefundBudget,
  readPendingRefund,
  savePendingRefund,
  removePendingRefund,
  type FinancialRefund,
  type PendingRefund,
  type RefundAction,
} from "../api/merchant-refund";
import {
  afterSaleIdentity,
  afterSaleStates,
  afterSaleActions,
  afterSalePage,
  afterSaleDetail,
  afterSaleLogs,
  afterSaleAreas,
  afterSaleReturnAddress,
  afterSaleCommand,
  afterSaleDecisionResult,
  readPendingDecision,
  savePendingDecision,
  removePendingDecision,
  type AfterSaleSummary,
  type AfterSaleDetail,
  type AfterSaleLog,
  type ReturnAddress,
  type AreaOption,
  type Action,
  type Command,
  type PendingDecision,
} from "../api/merchant-aftersale";

const rows = ref<AfterSaleSummary[]>([]),
  total = ref(0),
  page = ref(1),
  search = ref(""),
  state = ref(""),
  overdueOnly = ref(false);
const loading = ref(false),
  busy = ref(false),
  denied = ref(false),
  error = ref(""),
  notice = ref(""),
  modalError = ref(""),
  storageError = ref("");
const detail = ref<AfterSaleDetail | null>(null),
  logs = ref<AfterSaleLog[]>([]),
  logPage = ref(1),
  logTotal = ref(0),
  logError = ref("");
const areas = ref<AreaOption[]>([]),
  address = ref<ReturnAddress | null>(null),
  editing = ref<Action | null>(null);
const draft = ref<{ action: Action; body: Command; toStatus: number } | null>(
    null,
  ),
  pending = ref<PendingDecision | null>(null);
const refundInfo = ref<FinancialRefund | null>(null),
  refundPending = ref<PendingRefund | null>(null),
  refundMode = ref<"prepare" | "continue" | RefundAction | null>(null),
  refundDraft = ref<FinancialRefund | null>(null),
  refundReason = ref("");
const refundBudget = ref<RefundBudget | null>(null),
  continuationDraft = ref<RefundBudget | null>(null),
  continuationResume = ref<Extract<
    PendingRefund,
    { action: "continue" }
  > | null>(null),
  continuationResumeReason = ref("");
const form = reactive({
  reason: "",
  quality: false,
  name: "",
  mobile: "",
  province: 0,
  city: 0,
  district: 0,
  street: "",
  received: "0",
  salable: "0",
  accepted: "",
});
const locked = computed(() => loading.value || busy.value);
const can = (action: string) =>
  !denied.value && allowed(`trade:after-sale:${action}`);
const mayWrite = computed(
  () => !pending.value && !refundPending.value && !storageError.value,
);
const refundComplete = (value: FinancialRefund) =>
  value.status === "SUCCESS" && value.stockRecovered && value.couponRecovered;
const refundStates: Record<string, string> = {
  PREPARED: "待执行退款",
  PROCESSING: "渠道处理中",
  UNKNOWN: "退款结果待确认",
  FAILED: "退款未完成，待核查",
  SUCCESS: "退款已确认",
};
const children = (id: number) => areas.value.filter((x) => x.parentId === id);
const provinces = computed(() => children(0)),
  cities = computed(() => children(form.province)),
  districts = computed(() => children(form.city));
const areaMap = computed(() => new Map(areas.value.map((x) => [x.id, x])));
const title = computed(() =>
  editing.value === "agree"
    ? "同意售后申请"
    : editing.value === "disagree"
      ? "拒绝售后申请"
      : editing.value === "receive"
        ? "记录退货验收"
        : "售后详情",
);
const statusName = (n: number) => afterSaleStates[n] || "状态待确认";
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
let epoch = 0,
  sequence = 0,
  modalSequence = 0;
const live = (ticket: number, serial: number) =>
  ticket === epoch && serial === modalSequence;
function areaName(id: number) {
  const names: string[] = [],
    seen = new Set<number>();
  let item = areaMap.value.get(id);
  while (item && !seen.has(item.id)) {
    seen.add(item.id);
    names.unshift(item.name);
    item = areaMap.value.get(item.parentId);
  }
  return names.join(" / ");
}
function resetForm() {
  Object.assign(form, {
    reason: "",
    quality: false,
    name: "",
    mobile: "",
    province: 0,
    city: 0,
    district: 0,
    street: "",
    received: "0",
    salable: "0",
    accepted: "",
  });
  editing.value = null;
  draft.value = null;
  address.value = null;
  modalError.value = "";
  resetRefundForm();
}
function resetRefundForm() {
  refundMode.value = null;
  refundDraft.value = null;
  refundReason.value = "";
  continuationDraft.value = null;
}
function closeDetail() {
  continuationResume.value = null;
  continuationResumeReason.value = "";
  modalSequence++;
  detail.value = null;
  refundInfo.value = null;
  refundBudget.value = null;
  logs.value = [];
  logError.value = "";
  logTotal.value = 0;
  busy.value = false;
  resetForm();
}
function rejectAccess(e: unknown) {
  if (e instanceof ApiError && [401, 403].includes(e.code)) {
    epoch++;
    sequence++;
    denied.value = true;
    rows.value = [];
    total.value = 0;
    loading.value = false;
    closeDetail();
    areas.value = [];
  }
}
async function load() {
  if (locked.value || !can("query")) return;
  const ticket = epoch,
    serial = ++sequence;
  loading.value = true;
  error.value = "";
  rows.value = [];
  total.value = 0;
  try {
    const result = await afterSalePage(
      page.value,
      search.value.trim(),
      state.value,
      overdueOnly.value,
    );
    if (ticket !== epoch || serial !== sequence) return;
    rows.value = result.list;
    total.value = result.total;
  } catch (e) {
    if (ticket === epoch && serial === sequence) {
      error.value = message(e);
      rejectAccess(e);
    }
  } finally {
    if (ticket === epoch && serial === sequence) loading.value = false;
  }
}
async function readLogs() {
  if (!detail.value || locked.value) return;
  const ticket = epoch,
    serial = modalSequence,
    id = detail.value.id;
  busy.value = true;
  logs.value = [];
  logTotal.value = 0;
  logError.value = "";
  try {
    const result = await afterSaleLogs(id, logPage.value);
    if (!live(ticket, serial)) return;
    logs.value = result.list;
    logTotal.value = result.total;
  } catch (e) {
    if (live(ticket, serial)) {
      logError.value = message(e);
      rejectAccess(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
}
async function openDetail(id: number) {
  if (locked.value || !can("query")) return;
  closeDetail();
  const ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  error.value = "";
  try {
    const value = await afterSaleDetail(id);
    if (!live(ticket, serial)) return;
    detail.value = value;
    logPage.value = 1;
  } catch (e) {
    if (live(ticket, serial)) {
      error.value = message(e);
      rejectAccess(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
  if (live(ticket, serial) && detail.value) await readLogs();
  if (
    live(ticket, serial) &&
    detail.value &&
    [45, 50].includes(detail.value.status) &&
    can("refund")
  )
    await readFinancialRefund();
}
async function openAction(action: Action) {
  if (
    locked.value ||
    !detail.value ||
    !mayWrite.value ||
    !can(action === "receive" ? "receive" : "audit")
  )
    return;
  const id = detail.value.id,
    ticket = epoch,
    serial = ++modalSequence;
  busy.value = true;
  modalError.value = "";
  try {
    const current = await afterSaleDetail(id);
    if (!live(ticket, serial)) return;
    detail.value = current;
    if (
      action === "receive"
        ? ![30, 35].includes(current.status)
        : current.status !== 10
    )
      throw new ApiError("售后阶段已变化，请核对最新进度", 409);
    if (action === "agree" && current.way === 20 && !areas.value.length) {
      const values = await afterSaleAreas();
      if (!live(ticket, serial)) return;
      areas.value = values;
    }
    resetForm();
    editing.value = action;
    form.received = String(current.receivedCount);
    form.salable = String(current.salableCount);
    logs.value = [];
  } catch (e) {
    if (live(ticket, serial)) {
      modalError.value = message(e);
      rejectAccess(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
}
async function showAddress() {
  if (!detail.value || locked.value || !can("audit")) return;
  const id = detail.value.id,
    ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  address.value = null;
  modalError.value = "";
  try {
    const [value, options] = await Promise.all([
      afterSaleReturnAddress(id),
      areas.value.length ? Promise.resolve(areas.value) : afterSaleAreas(),
    ]);
    if (!live(ticket, serial)) return;
    address.value = value;
    areas.value = options;
  } catch (e) {
    if (live(ticket, serial)) {
      modalError.value = message(e);
      rejectAccess(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
}
function count(text: string) {
  if (!/^(0|[1-9][0-9]{0,3})$/.test(text))
    throw new Error("数量请填写0至9999的整数");
  return Number(text);
}
function preview() {
  if (!detail.value || !editing.value || locked.value || !mayWrite.value)
    return;
  modalError.value = "";
  try {
    const row = detail.value,
      action = editing.value,
      reason = form.reason.trim();
    if (!reason || reason.length > 500 || /[\x00-\x1f\x7f]/.test(reason))
      throw new Error("请填写不超过500字、无换行的处理原因");
    if (row.authVersion >= Number.MAX_SAFE_INTEGER)
      throw new Error("原申请版本需核查，请联系管理员");
    const body: Command = {
      id: row.id,
      expectedVersion: row.authVersion,
      requestId: crypto.randomUUID(),
      reason,
      qualityApproved: action === "agree" && form.quality,
    };
    let toStatus = action === "disagree" ? 62 : row.way === 20 ? 20 : 40;
    if (action === "agree" && row.way === 20) {
      const areaId = form.district || form.city || form.province;
      if (
        !form.name.trim() ||
        form.name.trim().length > 50 ||
        !/^1[3-9][0-9]{9}$/.test(form.mobile) ||
        !form.street.trim() ||
        form.street.trim().length > 255 ||
        /[\x00-\x1f\x7f]/.test(form.name + form.street)
      )
        throw new Error("请填写有效的寄回联系人、手机和详细地址");
      if (!areaMap.value.has(areaId) || children(areaId).length)
        throw new Error("请选择完整的寄回地区");
      body.returnAddress = {
        name: form.name.trim(),
        mobile: form.mobile,
        areaId,
        detail: form.street.trim(),
      };
    }
    if (action === "receive") {
      const received = count(form.received),
        salable = count(form.salable);
      if (received < row.receivedCount || salable > received)
        throw new Error("实收不能少于已记录数量，可售不能超过实收数量");
      if (!["yes", "no"].includes(form.accepted))
        throw new Error("请选择验收结论");
      if (form.accepted === "yes" && received !== row.count)
        throw new Error("实收与申请数量不符，请选择保留待核查");
      body.receivedCount = received;
      body.salableCount = salable;
      body.accepted = form.accepted === "yes";
      toStatus = body.accepted ? 40 : 35;
    }
    draft.value = { action, body, toStatus };
  } catch (e) {
    modalError.value = message(e);
  }
}
async function submit() {
  if (locked.value || !draft.value || !detail.value || !mayWrite.value) return;
  const value = draft.value,
    id = value.body.id,
    no = detail.value.no,
    ticket = epoch,
    serial = modalSequence;
  const marker: PendingDecision = {
    id,
    no,
    action: value.action,
    requestId: value.body.requestId,
    expectedVersion: value.body.expectedVersion,
    toStatus: value.toStatus,
  };
  busy.value = true;
  modalError.value = "";
  let persisted = false,
    confirmed = false,
    cleared = false;
  try {
    if (!can(value.action === "receive" ? "receive" : "audit"))
      throw new ApiError("没有此项售后处理权限", 403);
    const current = await afterSaleDetail(id);
    if (!live(ticket, serial)) return;
    if (current.authVersion !== value.body.expectedVersion) {
      detail.value = current;
      draft.value = null;
      throw new ApiError("原申请已变化，请重新核对后处理", 409);
    }
    savePendingDecision(marker);
    persisted = true;
    pending.value = marker;
    const receipt = await afterSaleCommand(value.action, value.body);
    if (receipt.status !== marker.toStatus)
      throw new ApiError("原操作结论待确认，请查询原请求", -2, true);
    confirmed = true;
    if (!live(ticket, serial)) return;
    removePendingDecision(marker);
    pending.value = null;
    cleared = true;
    resetForm();
    notice.value = "售后决定已记录，正在读取最新进度。";
  } catch (e) {
    if (!live(ticket, serial)) return;
    if (!persisted) {
      try {
        pending.value = readPendingDecision();
      } catch (storage) {
        storageError.value = message(storage);
      }
    }
    if (
      persisted &&
      !confirmed &&
      e instanceof ApiError &&
      !e.outcomeUnknown &&
      [400, 401, 403, 404, 409, 429].includes(e.code)
    ) {
      try {
        removePendingDecision(marker);
        pending.value = null;
      } catch (storage) {
        storageError.value = message(storage);
      }
    }
    modalError.value = message(e);
    draft.value = null;
    rejectAccess(e);
    if (pending.value) {
      resetForm();
      error.value = "处理结果待确认，请查询原操作后再继续。";
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
  if (cleared && live(ticket, serial)) {
    await openDetail(id);
    if (ticket === epoch) await load();
  }
}
async function resolvePending() {
  const marker = pending.value;
  if (!marker || locked.value || !can("query")) return;
  const ticket = epoch,
    serial = ++modalSequence;
  busy.value = true;
  error.value = "";
  closeSensitive();
  let resolved = false;
  try {
    let receipt = await afterSaleDecisionResult(marker);
    if (!live(ticket, serial)) return;
    const current = await afterSaleDetail(marker.id);
    if (!live(ticket, serial)) return;
    if (!receipt && current.authVersion > marker.expectedVersion) {
      receipt = await afterSaleDecisionResult(marker);
      if (!live(ticket, serial)) return;
    }
    if (!receipt && current.authVersion <= marker.expectedVersion) {
      error.value = "原操作仍待确认，请稍后再次查询。";
      return;
    }
    if (receipt && current.authVersion < receipt.authVersion)
      throw new ApiError("当前进度尚未确认，请保留原操作记录", 503);
    removePendingDecision(marker);
    pending.value = null;
    storageError.value = "";
    resolved = true;
    notice.value = receipt
      ? "已查到原决定，当前进度已更新。"
      : "原申请已推进，请核对最新进度与历史。";
  } catch (e) {
    if (live(ticket, serial)) {
      error.value = message(e);
      rejectAccess(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
  if (resolved && live(ticket, serial)) {
    await openDetail(marker.id);
    if (ticket === epoch) await load();
  }
}
function readPendingOperations() {
  try {
    const decision = readPendingDecision(),
      refund = readPendingRefund();
    pending.value = decision;
    refundPending.value = refund;
    storageError.value = "";
  } catch (e) {
    storageError.value = message(e);
  }
}
function checkRefundOrigin(value: FinancialRefund, row: AfterSaleDetail) {
  if (
    value.id !== row.id ||
    value.orderId !== row.orderId ||
    value.goodsPrice !== row.goodsRefundPrice ||
    value.restoreCount > row.count ||
    (row.way === 20 && value.restoreCount > row.salableCount)
  )
    throw new ApiError("原退款计划与售后不一致，请重新查询", -2);
}
async function readFinancialRefund() {
  if (!detail.value || locked.value || !can("refund")) return null;
  const row = detail.value,
    ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  modalError.value = "";
  refundInfo.value = null;
  refundBudget.value = null;
  try {
    const value = await financialRefundResult(row.id);
    if (!live(ticket, serial) || !can("refund")) return null;
    checkRefundOrigin(value, row);
    refundInfo.value = value;
    return value;
  } catch (e) {
    if (live(ticket, serial)) {
      modalError.value = message(e);
      rejectAccess(e);
    }
    return null;
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
}
function startRefundPreparation() {
  if (
    locked.value ||
    !mayWrite.value ||
    !can("refund") ||
    !detail.value ||
    detail.value.status !== 40 ||
    refundInfo.value
  )
    return;
  resetRefundForm();
  refundMode.value = "prepare";
  modalError.value = "";
}
async function readRefundBudget() {
  if (locked.value || !can("refund") || !detail.value || !refundInfo.value)
    return null;
  const row = refundInfo.value,
    ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  modalError.value = "";
  refundBudget.value = null;
  try {
    const value = await financialRefundBudget(row.id, row.refundNo);
    if (!live(ticket, serial) || !can("refund")) return null;
    refundBudget.value = value;
    return value;
  } catch (e) {
    if (live(ticket, serial)) {
      modalError.value = message(e);
      rejectAccess(e);
    }
    return null;
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
}
async function startContinuation() {
  if (locked.value || !mayWrite.value || !can("refund") || !detail.value)
    return;
  const ticket = epoch,
    serial = modalSequence;
  const current = await readFinancialRefund();
  if (!current || !live(ticket, serial)) return;
  const budget = await readRefundBudget();
  if (!budget || !live(ticket, serial)) return;
  if (
    !["PROCESSING", "UNKNOWN", "FAILED"].includes(budget.status) ||
    budget.submitCount !== budget.submissionLimit ||
    budget.submissionLimit >= 2147483647 ||
    !["PROCESSING", "UNKNOWN", "FAILED"].includes(current.status)
  ) {
    modalError.value = "原退款尚有提交机会或阶段已变化，请先同步并核对。";
    return;
  }
  refundMode.value = "continue";
  refundDraft.value = { ...current };
  continuationDraft.value = { ...budget };
  refundReason.value = "";
}
async function submitContinuation(resume: boolean) {
  if (
    locked.value ||
    !can("refund") ||
    !can("query") ||
    (!resume && !mayWrite.value)
  )
    return;
  const ticket = epoch,
    serial = modalSequence,
    reason = (
      resume ? continuationResumeReason.value : refundReason.value
    ).trim();
  let marker: Extract<PendingRefund, { action: "continue" }> | null = resume
    ? continuationResume.value
    : null;
  let persisted = resume && !!marker;
  busy.value = true;
  modalError.value = "";
  error.value = "";
  try {
    if (
      !reason ||
      reason.length > 500 ||
      /[\u0000-\u001f\u007f-\u009f]/.test(reason)
    )
      throw new ApiError("请填写有效的原人工授权原因", 400);
    if (resume) {
      const stored = readPendingRefund();
      if (
        !marker ||
        !stored ||
        stored.action !== "continue" ||
        stored.requestId !== marker.requestId
      )
        throw new ApiError("原人工待确认记录已变化", 409);
    } else {
      const original = refundDraft.value,
        budget = continuationDraft.value,
        row = detail.value;
      if (!original || !budget || !row || refundMode.value !== "continue")
        throw new ApiError("请重新核对原人工授权", 409);
      const current = await financialRefundResult(row.id, original.refundNo);
      if (!live(ticket, serial)) return;
      checkRefundOrigin(current, row);
      const latest = await financialRefundBudget(row.id, original.refundNo);
      if (!live(ticket, serial)) return;
      refundInfo.value = current;
      refundBudget.value = latest;
      if (
        current.authVersion !== original.authVersion ||
        current.authVersion >= Number.MAX_SAFE_INTEGER ||
        latest.payVersion !== budget.payVersion ||
        latest.payVersion >= Number.MAX_SAFE_INTEGER ||
        latest.submitCount !== latest.submissionLimit ||
        latest.submissionLimit !== budget.submissionLimit ||
        !["PROCESSING", "UNKNOWN", "FAILED"].includes(latest.status) ||
        !["PROCESSING", "UNKNOWN", "FAILED"].includes(current.status)
      )
        throw new ApiError("原支付预算或售后版本已变化，请重新核对", 409);
      marker = {
        action: "continue",
        id: row.id,
        no: row.no,
        refundNo: original.refundNo,
        requestId: crypto.randomUUID(),
        expectedVersion: current.authVersion,
        expectedPayVersion: latest.payVersion,
      };
      savePendingRefund(marker);
      persisted = true;
      refundPending.value = marker;
    }
    if (!marker) throw new ApiError("原人工授权记录缺失", 409);
    await continueFinancialRefund({
      id: marker.id,
      refundNo: marker.refundNo,
      requestId: marker.requestId,
      expectedVersion: marker.expectedVersion,
      expectedPayVersion: marker.expectedPayVersion,
      reason,
    });
    if (!live(ticket, serial)) return;
    notice.value = "已收到原授权响应，正在核对这次请求的回执；尚未自动退款。";
  } catch (e) {
    if (live(ticket, serial)) {
      modalError.value = message(e);
      error.value = message(e);
      rejectAccess(e);
    }
  } finally {
    if (live(ticket, serial)) {
      busy.value = false;
      resetRefundForm();
      continuationResumeReason.value = "";
    }
  }
  // After any attempted continuation, only GETs inspect the original UUID. No automatic POST replay.
  if (persisted && live(ticket, serial)) await resolvePendingRefund();
}
async function startRefundAction(action: RefundAction) {
  if (locked.value || !mayWrite.value || !can("refund") || !detail.value)
    return;
  const ticket = epoch,
    serial = modalSequence;
  const current = await readFinancialRefund();
  if (!current || !live(ticket, serial)) return;
  if (action === "execute" && current.status !== "PREPARED") {
    modalError.value = "原退款已进入处理，请查询或同步原退款。";
    return;
  }
  if (
    action === "retry" &&
    !["PROCESSING", "UNKNOWN", "FAILED"].includes(current.status)
  ) {
    modalError.value = "当前阶段不需要原号重试，请先同步退款结果。";
    return;
  }
  refundReason.value = "";
  if (action === "sync" && refundComplete(current)) {
    notice.value = "退款及库存、优惠券处理已完成。";
    return;
  }
  refundMode.value = action;
  refundDraft.value = { ...current };
}
async function submitRefund() {
  if (refundMode.value === "continue") return;
  if (
    locked.value ||
    !mayWrite.value ||
    !can("refund") ||
    !detail.value ||
    !refundMode.value
  )
    return;
  const row = detail.value,
    mode = refundMode.value,
    original = refundDraft.value,
    reason = refundReason.value.trim(),
    ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  modalError.value = "";
  let marker: PendingRefund | null = null,
    persisted = false,
    confirmed = false,
    cleared = false;
  try {
    if (mode === "prepare") {
      if (
        !reason ||
        reason.length > 500 ||
        /[\u0000-\u001f\u007f-\u009f]/.test(reason)
      )
        throw new ApiError("请填写不超过500字、无控制字符的退款原因", 400);
      const current = await afterSaleDetail(row.id);
      if (!live(ticket, serial)) return;
      if (
        current.status !== 40 ||
        current.authVersion !== row.authVersion ||
        current.authVersion >= Number.MAX_SAFE_INTEGER
      ) {
        detail.value = current;
        throw new ApiError("原售后已变化，请核对最新进度后准备退款", 409);
      }
      marker = {
        action: "prepare",
        id: row.id,
        no: row.no,
        requestId: crypto.randomUUID(),
        expectedVersion: current.authVersion,
      };
    } else {
      if (!original || original.id !== row.id)
        throw new ApiError("请重新核对原退款计划", 409);
      const current = await financialRefundResult(row.id, original.refundNo);
      if (!live(ticket, serial)) return;
      checkRefundOrigin(current, row);
      if (
        current.preparedVersion !== original.preparedVersion ||
        current.requestId !== original.requestId ||
        current.totalPrice !== original.totalPrice ||
        current.deliveryPrice !== original.deliveryPrice ||
        current.restoreCount !== original.restoreCount ||
        current.couponReturnRequired !== original.couponReturnRequired
      )
        throw new ApiError("原退款计划已变化，请重新核对", 409);
      refundInfo.value = current;
      if (mode === "execute" && current.status !== "PREPARED")
        throw new ApiError("原退款已进入处理，请同步原退款结果", 409);
      if (mode === "retry") {
        if (
          !reason ||
          reason.length > 500 ||
          /[\u0000-\u001f\u007f-\u009f]/.test(reason)
        )
          throw new ApiError("请填写不超过500字的重试原因", 400);
        if (
          !["PROCESSING", "UNKNOWN", "FAILED"].includes(current.status) ||
          current.authVersion !== original.authVersion ||
          current.authVersion >= Number.MAX_SAFE_INTEGER
        )
          throw new ApiError("原退款阶段或版本已变化，请重新核对", 409);
        marker = {
          action: "retry",
          id: row.id,
          no: row.no,
          refundNo: current.refundNo,
          expectedVersion: current.authVersion,
          requestId: crypto.randomUUID(),
        };
      } else
        marker = {
          action: mode,
          id: row.id,
          no: row.no,
          refundNo: current.refundNo,
        };
    }
    savePendingRefund(marker);
    persisted = true;
    refundPending.value = marker;
    const value =
      marker.action === "prepare"
        ? await prepareFinancialRefund({
            id: marker.id,
            expectedVersion: marker.expectedVersion,
            requestId: marker.requestId,
            reason,
          })
        : marker.action === "retry"
          ? await retryFinancialRefund({
              id: marker.id,
              refundNo: marker.refundNo,
              expectedVersion: marker.expectedVersion,
              requestId: marker.requestId,
              reason,
            })
          : await commandFinancialRefund(
              marker.action,
              marker.id,
              marker.refundNo,
            );
    if (!live(ticket, serial)) return;
    checkRefundOrigin(value, row);
    confirmed = true;
    refundInfo.value = value;
    removePendingRefund(marker);
    refundPending.value = null;
    cleared = true;
    notice.value =
      mode === "prepare"
        ? "退款计划已准备，请核对总额后执行退款。"
        : refundComplete(value)
          ? "退款及库存、优惠券处理已完成。"
          : value.status === "SUCCESS"
            ? "退款已确认，库存或优惠券仍待处理。"
            : "已读取原退款进度，退款尚未确认完成。";
    resetRefundForm();
  } catch (e) {
    if (!live(ticket, serial)) return;
    if (!persisted) readPendingOperations();
    // Only a definite preparation rejection proves no new plan was committed.
    // Execution/sync can commit money before a later resource error, including 4xx.
    if (
      persisted &&
      !confirmed &&
      marker?.action === "prepare" &&
      e instanceof ApiError &&
      !e.outcomeUnknown &&
      [400, 401, 403, 404, 409, 429].includes(e.code)
    ) {
      try {
        removePendingRefund(marker);
        refundPending.value = null;
      } catch (storage) {
        storageError.value = message(storage);
      }
    }
    resetRefundForm();
    modalError.value = message(e);
    rejectAccess(e);
    if (refundPending.value)
      error.value = "退款操作结果待确认，请先查询原退款。";
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
  if (cleared && live(ticket, serial)) {
    await openDetail(row.id);
    if (ticket === epoch) await load();
  }
}
async function resolvePendingRefund() {
  const marker = refundPending.value;
  if (!marker || locked.value || !can("refund") || !can("query")) return;
  const ticket = epoch,
    serial = ++modalSequence;
  busy.value = true;
  error.value = "";
  closeSensitive();
  let cleared = false;
  try {
    let value =
      marker.action === "prepare"
        ? await refundPreparationResult(marker)
        : await financialRefundResult(marker.id, marker.refundNo);
    if (!live(ticket, serial)) return;
    let current = await afterSaleDetail(marker.id);
    if (!live(ticket, serial)) return;
    if (marker.action === "continue") {
      let receipt = await financialContinuationResult(marker);
      if (!live(ticket, serial)) return;
      if (!receipt && current.authVersion > marker.expectedVersion) {
        receipt = await financialContinuationResult(marker);
        if (!live(ticket, serial)) return;
      }
      if (!receipt && current.authVersion <= marker.expectedVersion) {
        error.value = "原人工申请仍待确认，请稍后查询。";
        return;
      }
      if (receipt && current.authVersion < receipt.recordedVersion)
        throw new ApiError("原人工申请与当前进度需核对", 503);
      if (receipt && !receipt.authorizationRecorded) {
        const budget = await financialRefundBudget(marker.id, marker.refundNo);
        if (!live(ticket, serial)) return;
        if (
          budget.status !== "SUCCESS" &&
          budget.payVersion <= marker.expectedPayVersion
        ) {
          continuationResume.value = marker;
          continuationResumeReason.value = "";
          error.value =
            "原申请已登记，支付授权尚未确认。可再次查询，或填写原原因后明确续送同一申请。";
          return;
        }
        receipt = await financialContinuationResult(marker);
        if (!live(ticket, serial)) return;
        notice.value = receipt?.authorizationRecorded
          ? "原授权已登记，请重新核对剩余机会后明确重试。"
          : "原支付进度已变化，请核对后使用新的依据。";
      } else if (receipt?.authorizationRecorded)
        notice.value = "原授权已登记，请核对当前剩余机会；授权未自动退款。";
    }
    if (marker.action === "retry") {
      let receipt = await refundRetryResult(marker);
      if (!live(ticket, serial)) return;
      if (!receipt && current.authVersion > marker.expectedVersion) {
        receipt = await refundRetryResult(marker);
        if (!live(ticket, serial)) return;
      }
      if (!receipt && current.authVersion <= marker.expectedVersion) {
        error.value = "原重试请求仍待确认，请稍后查询；不要重新提交。";
        return;
      }
      if (receipt && current.authVersion < receipt.recordedVersion) {
        current = await afterSaleDetail(marker.id);
        if (!live(ticket, serial)) return;
        if (current.authVersion < receipt.recordedVersion)
          throw new ApiError("原重试回执与当前进度需核对", 503);
      }
    }
    if (
      !value &&
      marker.action === "prepare" &&
      current.authVersion > marker.expectedVersion
    ) {
      value = await refundPreparationResult(marker);
      if (!live(ticket, serial)) return;
    }
    if (
      !value &&
      marker.action === "prepare" &&
      current.authVersion <= marker.expectedVersion
    ) {
      error.value = "原退款准备仍待确认，请稍后再次查询。";
      return;
    }
    if (value) {
      checkRefundOrigin(value, current);
      if (current.authVersion < value.authVersion) {
        current = await afterSaleDetail(marker.id);
        if (!live(ticket, serial)) return;
        if (current.authVersion < value.authVersion)
          throw new ApiError("当前售后进度尚未确认，请保留原记录", 503);
      }
    }
    removePendingRefund(marker);
    refundPending.value = null;
    continuationResume.value = null;
    continuationResumeReason.value = "";
    readPendingOperations();
    cleared = true;
    if (marker.action !== "continue")
      notice.value = value
        ? "已读取原退款进度，请核对资金、库存和优惠券状态。"
        : "原售后已变更，请核对最新进度；本次查询未重新发起退款。";
  } catch (e) {
    if (live(ticket, serial)) {
      error.value = message(e);
      rejectAccess(e);
    }
  } finally {
    if (live(ticket, serial)) busy.value = false;
  }
  if (cleared && live(ticket, serial)) {
    await openDetail(marker.id);
    if (ticket === epoch) await load();
  }
}
function closeSensitive() {
  continuationResume.value = null;
  continuationResumeReason.value = "";
  detail.value = null;
  refundInfo.value = null;
  refundBudget.value = null;
  logs.value = [];
  logError.value = "";
  resetForm();
}
function clearScreen() {
  epoch++;
  sequence++;
  modalSequence++;
  closeSensitive();
  rows.value = [];
  total.value = 0;
  areas.value = [];
  busy.value = false;
  loading.value = false;
  pending.value = null;
  refundPending.value = null;
  continuationResume.value = null;
  continuationResumeReason.value = "";
  notice.value = "";
  error.value = "";
  storageError.value = "";
}
watch(
  [afterSaleIdentity, () => credentials().accessToken],
  () => {
    clearScreen();
    denied.value = false;
    page.value = 1;
    readPendingOperations();
    if (can("query")) void load();
  },
  { immediate: true },
);
watch(
  () => identities.merchant?.permissions.join("|"),
  () => {
    if (!allowed("trade:after-sale:query")) {
      clearScreen();
      return;
    }
    denied.value = false;
    readPendingOperations();
    if (
      !can("refund") &&
      (refundInfo.value || refundMode.value || refundPending.value)
    )
      closeDetail();
    if (
      (editing.value === "receive" && !can("receive")) ||
      (editing.value && editing.value !== "receive" && !can("audit"))
    )
      closeDetail();
    if (!can("audit")) {
      address.value = null;
      areas.value = [];
    }
  },
);
onBeforeUnmount(clearScreen);
</script>

<template>
  <PageHeader title="退货退款"
    ><button class="button" :disabled="locked || !can('query')" @click="load">
      刷新售后
    </button></PageHeader
  >
  <div class="panel">
    <form
      class="management-filters"
      @submit.prevent="
        page = 1;
        load();
      "
    >
      <label
        >完整售后编号<input
          v-model="search"
          maxlength="34"
          placeholder="AS…"
          :disabled="locked"
      /></label>
      <label
        >售后状态<select v-model="state" :disabled="locked">
          <option value="">全部</option>
          <option
            v-for="(name, id) in afterSaleStates"
            :key="id"
            :value="String(id)"
          >
            {{ name }}
          </option>
        </select></label
      >
      <label class="check-label"
        ><input
          v-model="overdueOnly"
          type="checkbox"
          :disabled="locked"
        />仅看处理超过48小时</label
      >
      <button class="button" :disabled="locked || !can('query')">查询</button>
    </form>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="storageError" class="form-error" role="alert">
      {{ storageError }}
    </p>
    <div v-if="pending" class="pending" role="alert">
      <span>售后 {{ pending.no }} 的处理结果待确认。</span
      ><button
        class="button"
        :disabled="locked || !can('query')"
        @click="resolvePending"
      >
        查询原操作
      </button>
    </div>
    <div v-if="refundPending" class="pending" role="alert">
      <span>售后 {{ refundPending.no }} 的退款操作结果待确认。</span>
      <button
        class="button"
        :disabled="locked || !can('refund') || !can('query')"
        @click="resolvePendingRefund"
      >
        查询原退款
      </button>
      <small v-if="!can('refund')">需要资金退款权限才能查询处理结果。</small>
    </div>
    <form
      v-if="continuationResume && can('refund')"
      class="panel confirmation"
      @submit.prevent="submitContinuation(true)"
    >
      <h3>续送原人工申请</h3>
      <p>
        售后 {{ continuationResume.no }} · 原退款号
        {{ continuationResume.refundNo }}
      </p>
      <p>
        原申请已登记，支付侧尚未确认。填写与原申请完全相同的原因后，可明确续送原请求；不会生成新申请或自动退款。
      </p>
      <label class="refund-reason"
        >原申请原因<textarea
          v-model="continuationResumeReason"
          :disabled="locked"
          maxlength="500"
          rows="3"
          required
        />
      </label>
      <div class="case-actions">
        <button class="button primary" :disabled="locked">
          确认续送原申请
        </button>
      </div>
    </form>
    <p v-if="loading" role="status">正在查询售后…</p>
    <div
      v-else-if="rows.length"
      class="table-wrap"
      tabindex="0"
      role="region"
      aria-label="售后列表"
    >
      <table>
        <thead>
          <tr>
            <th scope="col">售后 / 申请时间</th>
            <th scope="col">商品 / 数量</th>
            <th scope="col">类型</th>
            <th scope="col">商品申请金额</th>
            <th scope="col">状态</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td class="number">
              {{ row.no }}<small>{{ time(row.createdAt) }}</small>
            </td>
            <td>
              {{ row.spuName }}<small>申请 {{ row.count }} 件</small>
            </td>
            <td>{{ row.way === 20 ? "退货退款" : "仅退款" }}</td>
            <td>¥{{ money(row.goodsRefundPrice) }}</td>
            <td>
              {{ statusName(row.status)
              }}<small v-if="row.overdue" class="warning"
                >处理已超过48小时</small
              >
            </td>
            <td>
              <button
                class="button"
                :disabled="locked"
                @click="openDetail(row.id)"
              >
                查看详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Empty v-else-if="!error && !loading" title="暂无符合条件的售后"
      ><p>调整编号、状态或超时筛选后查询。</p></Empty
    >
    <nav class="pagination" aria-label="售后分页">
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
  </div>

  <Modal v-if="detail" :title="title" wide @close="closeDetail">
    <div class="case-heading">
      <strong>{{ detail.no }}</strong
      ><span>{{ statusName(detail.status) }}</span>
    </div>
    <p>
      {{ detail.spuName }} · 申请 {{ detail.count }} 件 ·
      {{ detail.way === 20 ? "退货退款" : "仅退款" }}
    </p>
    <p>
      商品申请金额（不含运费）：<strong
        >¥{{ money(detail.goodsRefundPrice) }}</strong
      >
    </p>
    <div v-if="detail.refund && !can('refund')">
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
    <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
    <template v-if="!editing">
      <dl class="facts">
        <div>
          <dt>原订单</dt>
          <dd>{{ detail.orderNo }}</dd>
        </div>
        <div>
          <dt>申请原因</dt>
          <dd>{{ detail.applyReason }}</dd>
        </div>
        <div v-if="detail.applyDescription">
          <dt>申请说明</dt>
          <dd>{{ detail.applyDescription }}</dd>
        </div>
        <div>
          <dt>申请时间</dt>
          <dd>{{ time(detail.createdAt) }}</dd>
        </div>
        <div v-if="detail.auditReason">
          <dt>审核意见</dt>
          <dd>{{ detail.auditReason }}</dd>
        </div>
        <div v-if="detail.returnDeadline">
          <dt>提交寄回物流期限</dt>
          <dd>{{ time(detail.returnDeadline) }}</dd>
        </div>
        <div v-if="detail.deliveredAt">
          <dt>寄回物流</dt>
          <dd>
            {{ detail.logisticsName }} · {{ detail.logisticsNo
            }}<small>提交于 {{ time(detail.deliveredAt) }}</small>
          </dd>
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
      <section
        v-if="can('refund') && [40, 45, 50].includes(detail.status)"
        class="refund-panel"
        aria-labelledby="refund-title"
      >
        <h3 id="refund-title">资金退款</h3>
        <div v-if="refundPending" class="pending" role="alert">
          <span
            >售后
            {{ refundPending.no }} 的退款操作待确认，请先查询原结果。</span
          >
          <button
            class="button"
            :disabled="locked"
            @click="resolvePendingRefund"
          >
            查询原操作结果
          </button>
        </div>
        <template v-if="refundInfo">
          <p class="number">原退款号：{{ refundInfo.refundNo }}</p>
          <p v-if="refundBudget">
            原渠道已提交 {{ refundBudget.submitCount }} 次，当前上限
            {{ refundBudget.submissionLimit }} 次。<small
              v-if="refundBudget.retryAfter"
              >原号重试最早时间：{{ time(refundBudget.retryAfter) }}</small
            >
          </p>
          <dl class="facts">
            <div>
              <dt>退款总额</dt>
              <dd>
                <strong>¥{{ money(refundInfo.totalPrice) }}</strong>
              </dd>
            </div>
            <div>
              <dt>金额明细</dt>
              <dd>
                商品 ¥{{ money(refundInfo.goodsPrice) }}，运费 ¥{{
                  money(refundInfo.deliveryPrice)
                }}
              </dd>
            </div>
            <div>
              <dt>资金进度</dt>
              <dd>
                {{ refundStates[refundInfo.status]
                }}<small v-if="refundInfo.confirmedAt">{{
                  time(refundInfo.confirmedAt)
                }}</small>
              </dd>
            </div>
            <div>
              <dt>库存恢复</dt>
              <dd>
                {{
                  refundInfo.restoreCount === 0
                    ? "无需恢复"
                    : refundInfo.stockRecovered
                      ? "已恢复"
                      : "待处理"
                }}（{{ refundInfo.restoreCount }} 件）
              </dd>
            </div>
            <div>
              <dt>优惠券</dt>
              <dd>
                {{
                  !refundInfo.couponReturnRequired
                    ? "无需退回"
                    : refundInfo.couponRecovered
                      ? "已处理"
                      : "待处理"
                }}
              </dd>
            </div>
          </dl>
          <p v-if="refundInfo.couponReturnRequired">退券不延长原有效期。</p>
        </template>
        <p v-else-if="detail.status === 40">
          商品申请金额 ¥{{
            money(detail.goodsRefundPrice)
          }}，运费以准备结果为准。
        </p>
        <p v-else>读取原退款记录后查看资金及资源处理进度。</p>

        <div v-if="!refundMode" class="case-actions">
          <button
            v-if="detail.status === 40 && !refundInfo"
            class="button primary"
            :disabled="locked || !mayWrite"
            @click="startRefundPreparation"
          >
            准备退款
          </button>
          <button
            v-if="[45, 50].includes(detail.status) || refundInfo"
            class="button"
            :disabled="locked"
            @click="readFinancialRefund"
          >
            读取退款进度
          </button>
          <button
            v-if="refundInfo?.status === 'PREPARED'"
            class="button primary"
            :disabled="locked || !mayWrite"
            @click="startRefundAction('execute')"
          >
            执行退款
          </button>
          <button
            v-if="refundInfo && !refundComplete(refundInfo)"
            class="button"
            :disabled="locked || !mayWrite"
            @click="startRefundAction('sync')"
          >
            {{
              refundInfo.status === "SUCCESS"
                ? "继续处理库存和退券"
                : "同步退款结果"
            }}
          </button>
          <button
            v-if="
              refundInfo &&
              ['PROCESSING', 'UNKNOWN', 'FAILED'].includes(refundInfo.status)
            "
            class="button"
            :disabled="locked || !mayWrite"
            @click="startRefundAction('retry')"
          >
            原号重试
          </button>
          <button
            v-if="
              refundInfo &&
              refundInfo.totalPrice > 0 &&
              refundInfo.status !== 'PREPARED'
            "
            class="button"
            :disabled="locked"
            @click="readRefundBudget"
          >
            读取提交次数
          </button>
          <button
            v-if="
              refundBudget &&
              refundBudget.submitCount === refundBudget.submissionLimit &&
              ['PROCESSING', 'UNKNOWN', 'FAILED'].includes(refundBudget.status)
            "
            class="button"
            :disabled="locked || !mayWrite"
            @click="startContinuation"
          >
            申请增加一次机会
          </button>
        </div>

        <form
          v-else-if="refundMode === 'prepare'"
          class="confirmation"
          @submit.prevent="submitRefund"
        >
          <h3>确认准备退款</h3>
          <p>
            售后 {{ detail.no }} · 商品申请金额 ¥{{
              money(detail.goodsRefundPrice)
            }}。
          </p>
          <p>
            准备后将冻结本次退款计划，原售后不可再撤销。此步骤尚不向支付渠道退款。
          </p>
          <label class="refund-reason"
            >退款原因<textarea
              v-model="refundReason"
              :disabled="locked"
              maxlength="500"
              rows="3"
              required
            />
          </label>
          <div class="case-actions">
            <button class="button primary" :disabled="locked || !mayWrite">
              {{ busy ? "正在准备…" : "确认进入退款处理" }}
            </button>
            <button
              type="button"
              class="button"
              :disabled="locked"
              @click="resetRefundForm"
            >
              返回详情
            </button>
          </div>
        </form>
        <div v-else-if="refundDraft" class="confirmation">
          <h3>
            {{
              refundMode === "execute"
                ? "确认执行退款"
                : refundMode === "continue"
                  ? "确认增加一次提交机会"
                  : refundMode === "retry"
                    ? "确认原号重试"
                    : "确认同步原退款"
            }}
          </h3>
          <p>售后 {{ detail.no }} · 原退款号 {{ refundDraft.refundNo }}</p>
          <p>
            退款总额 <strong>¥{{ money(refundDraft.totalPrice) }}</strong
            >，其中商品 ¥{{ money(refundDraft.goodsPrice) }}、运费 ¥{{
              money(refundDraft.deliveryPrice)
            }}。
          </p>
          <p v-if="refundMode === 'execute' && refundDraft.totalPrice > 0">
            确认后将按此计划向原支付渠道退款。
          </p>
          <p v-else-if="refundMode === 'execute'">
            本笔金额为0，仅确认原售后并处理库存和优惠券。
          </p>
          <template
            v-else-if="refundMode === 'retry' || refundMode === 'continue'"
          >
            <p v-if="refundMode === 'retry'">
              先查询原渠道结果，未完成时尝试按原号再次提交。提交次数与间隔仍受限制。
            </p>
            <p v-else>
              已用
              {{
                continuationDraft?.submitCount
              }}
              次，确认后原号最多再增加一次提交机会，不清零历史次数。授权不会自动退款。
            </p>
            <label class="refund-reason"
              >{{ refundMode === "continue" ? "人工授权原因" : "重试原因"
              }}<textarea
                v-model="refundReason"
                :disabled="locked"
                maxlength="500"
                rows="3"
                required
              />
            </label>
          </template>
          <p v-else>查询原退款结果，并继续处理未完成的库存和退券。</p>
          <div class="case-actions">
            <button
              class="button primary"
              :disabled="locked || !mayWrite"
              @click="
                refundMode === 'continue'
                  ? submitContinuation(false)
                  : submitRefund()
              "
            >
              {{
                busy
                  ? "正在处理…"
                  : refundMode === "execute"
                    ? "确认执行退款"
                    : refundMode === "continue"
                      ? "确认增加一次机会"
                      : refundMode === "retry"
                        ? "确认原号重试"
                        : "确认同步结果"
              }}
            </button>
            <button class="button" :disabled="locked" @click="resetRefundForm">
              返回详情
            </button>
          </div>
        </div>
      </section>
      <div class="case-actions">
        <button
          v-if="detail.status === 10 && can('audit')"
          class="button primary"
          :disabled="locked || !mayWrite"
          @click="openAction('agree')"
        >
          同意申请
        </button>
        <button
          v-if="detail.status === 10 && can('audit')"
          class="button danger"
          :disabled="locked || !mayWrite"
          @click="openAction('disagree')"
        >
          拒绝申请
        </button>
        <button
          v-if="[30, 35].includes(detail.status) && can('receive')"
          class="button primary"
          :disabled="locked || !mayWrite"
          @click="openAction('receive')"
        >
          记录验货
        </button>
        <button
          v-if="
            detail.way === 20 &&
            detail.auditedAt &&
            detail.status !== 62 &&
            can('audit')
          "
          class="button"
          :disabled="locked"
          @click="showAddress"
        >
          查看寄回地址
        </button>
      </div>
      <dl v-if="address" class="address">
        <dt>审核确认的寄回地址</dt>
        <dd>{{ address.name }} · {{ address.mobile }}</dd>
        <dd>{{ areaName(address.areaId) }} {{ address.detail }}</dd>
      </dl>
      <h3>操作记录</h3>
      <p v-if="logError" class="form-error" role="alert">{{ logError }}</p>
      <ol v-if="logs.length" class="history">
        <li v-for="item in logs" :key="item.id">
          <strong>{{ afterSaleActions[item.action] }}</strong
          ><small
            >{{ time(item.createdAt) }} ·
            {{
              item.actorType === "MEMBER"
                ? "买家"
                : item.actorType === "ADMIN"
                  ? "商家"
                  : "系统"
            }}</small
          >
          <p v-if="item.reason">{{ item.reason }}</p>
          <p v-if="item.inspection">
            实收 {{ item.inspection.receivedCount }} 件，可售
            {{ item.inspection.salableCount }} 件，不可售
            {{ item.inspection.unsalableCount }} 件
          </p>
        </li>
      </ol>
      <p v-else-if="!busy && !logError">暂无操作记录。</p>
      <p v-if="busy" role="status">正在读取…</p>
      <nav class="pagination" aria-label="操作记录分页">
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
      <fieldset :disabled="locked" class="fields">
        <label
          >处理原因<textarea
            v-model="form.reason"
            rows="3"
            maxlength="500"
            required
          />
        </label>
        <label v-if="editing === 'agree'" class="check-label"
          ><input v-model="form.quality" type="checkbox" />确认为质量问题</label
        >
        <template v-if="editing === 'agree' && detail.way === 20">
          <h3>本次寄回地址</h3>
          <p>同意后，买家须在7天内提交寄回物流。</p>
          <div class="field-grid">
            <label
              >联系人<input
                v-model="form.name"
                maxlength="50"
                autocomplete="off"
                required /></label
            ><label
              >手机<input
                v-model="form.mobile"
                type="tel"
                inputmode="tel"
                maxlength="11"
                autocomplete="off"
                required
            /></label>
          </div>
          <div class="field-grid regions">
            <label
              >省份<select
                v-model.number="form.province"
                @change="
                  form.city = 0;
                  form.district = 0;
                "
              >
                <option :value="0">请选择</option>
                <option
                  v-for="item in provinces"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.name }}
                </option>
              </select></label
            ><label
              >城市<select
                v-model.number="form.city"
                @change="form.district = 0"
              >
                <option :value="0">请选择</option>
                <option v-for="item in cities" :key="item.id" :value="item.id">
                  {{ item.name }}
                </option>
              </select></label
            ><label
              >区县<select v-model.number="form.district">
                <option :value="0">请选择</option>
                <option
                  v-for="item in districts"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.name }}
                </option>
              </select></label
            >
          </div>
          <label
            >详细地址<textarea
              v-model="form.street"
              rows="2"
              maxlength="255"
              autocomplete="off"
              required
            />
          </label>
        </template>
        <template v-if="editing === 'receive'">
          <div class="field-grid">
            <label
              >累计实收数量<input
                v-model="form.received"
                inputmode="numeric"
                maxlength="4"
                required /></label
            ><label
              >其中可售数量<input
                v-model="form.salable"
                inputmode="numeric"
                maxlength="4"
                required
            /></label>
          </div>
          <label
            >验收结论<select v-model="form.accepted" required>
              <option value="">请选择</option>
              <option value="yes">验收通过，进入待退款</option>
              <option value="no">保留待核查</option>
            </select></label
          >
          <p>实收数量不符或拒收时，请保留待核查并说明原因。</p>
        </template>
      </fieldset>
      <div class="case-actions">
        <button class="button primary" :disabled="locked || !mayWrite">
          核对本次决定</button
        ><button
          type="button"
          class="button"
          :disabled="locked"
          @click="
            resetForm();
            readLogs();
          "
        >
          返回详情
        </button>
      </div>
    </form>
    <div v-else class="confirmation">
      <h3>确认本次决定</h3>
      <p>
        处理后阶段：<strong>{{ statusName(draft.toStatus) }}</strong>
      </p>
      <p>原因：{{ draft.body.reason }}</p>
      <p v-if="draft.body.qualityApproved">本次审核确认为质量问题。</p>
      <p v-if="draft.body.returnAddress">
        寄回至：{{ draft.body.returnAddress.name }} ·
        {{ draft.body.returnAddress.mobile }}<br />{{
          areaName(draft.body.returnAddress.areaId)
        }}
        {{ draft.body.returnAddress.detail }}
      </p>
      <p v-if="draft.action === 'receive'">
        实收 {{ draft.body.receivedCount }} 件，可售
        {{ draft.body.salableCount }} 件，不可售
        {{ (draft.body.receivedCount ?? 0) - (draft.body.salableCount ?? 0) }}
        件
      </p>
      <div class="case-actions">
        <button
          class="button primary"
          :disabled="locked || !mayWrite"
          @click="submit"
        >
          {{ busy ? "正在处理…" : "确认记录本次决定" }}</button
        ><button class="button" :disabled="locked" @click="draft = null">
          返回修改
        </button>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
small {
  display: block;
  color: var(--muted);
  margin-top: 5px;
  line-height: 1.6;
}
.number,
p,
dd,
strong {
  overflow-wrap: anywhere;
}
.warning {
  color: var(--warning);
  font-weight: 600;
}
.refund-panel {
  border-top: 1px solid var(--border);
  margin-top: 24px;
  padding-top: 4px;
}
.refund-reason {
  display: grid;
  gap: 8px;
}
.refund-reason textarea {
  width: 100%;
}
.pending {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  padding: 16px;
  background: var(--primary-soft);
  margin: 16px 0;
}
.pagination,
.case-actions,
.case-heading {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.pagination {
  justify-content: center;
  margin-top: 20px;
}
.case-heading {
  justify-content: space-between;
}
.case-actions {
  margin: 20px 0;
}
.facts {
  display: grid;
  gap: 16px;
}
.facts > div {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 12px;
}
dt {
  color: var(--muted);
}
dd {
  margin: 0;
  white-space: pre-wrap;
}
.fields {
  display: grid;
  gap: 16px;
  border: 0;
  padding: 0;
}
.fields label {
  display: grid;
  gap: 8px;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.regions {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.check-label {
  display: flex !important;
  gap: 8px;
  align-items: center;
}
.check-label input {
  width: auto;
}
.address,
.confirmation {
  background: var(--workspace);
  padding: 16px;
  margin: 20px 0;
}
.history {
  padding-left: 24px;
}
.history li {
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
}
table {
  min-width: 820px;
}
h3 {
  margin: 24px 0 12px;
}
.management-filters input {
  min-width: 240px;
}
.management-filters .check-label input {
  min-width: 0;
}
.table-wrap:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}
@media (max-width: 600px) {
  .management-filters label {
    width: 100%;
  }
  .management-filters input {
    min-width: 0;
    width: 100%;
  }
  .field-grid,
  .regions {
    grid-template-columns: 1fr;
  }
  .facts > div {
    grid-template-columns: 1fr;
    gap: 6px;
  }
  .fields input,
  .fields select,
  .fields textarea,
  .refund-reason textarea {
    font-size: 16px;
  }
  .case-actions .button {
    flex: 1;
  }
}
</style>
