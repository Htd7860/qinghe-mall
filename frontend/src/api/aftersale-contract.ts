// Shared pure aftersale projections/guards; no session, transport or browser-storage side effects.
export type AfterSaleSummary = {
  id: number;
  no: string;
  orderId: number;
  orderNo: string;
  orderItemId: number;
  skuId: number;
  spuName: string;
  picUrl: string | null;
  count: number;
  way: number;
  status: number;
  authVersion: number;
  goodsRefundPrice: number;
  createdAt: number;
  updatedAt: number;
  returnDeadline: number | null;
  serverNow: number;
  overdue: boolean;
};
export type AfterSaleDetail = AfterSaleSummary & {
  applyReason: string;
  applyDescription: string;
  qualityApproved: boolean;
  auditReason: string | null;
  auditedAt: number | null;
  deliveredAt: number | null;
  inspectedAt: number | null;
  logisticsId: number | null;
  logisticsName: string | null;
  logisticsNo: string | null;
  receivedCount: number;
  salableCount: number;
  unsalableCount: number;
  receiveReason: string | null;
  refund: RefundProgress | null;
};
export type RefundProgress = {
  goodsPrice: number;
  deliveryPrice: number;
  totalPrice: number;
  status: string;
  confirmedAt: number | null;
  restoreCount: number;
  stockRecovered: boolean;
  couponReturnRequired: boolean;
  couponRecovered: boolean;
};
export type Inspection = {
  receivedCount: number;
  salableCount: number;
  unsalableCount: number;
  accepted: boolean;
};
export type AfterSaleLog = {
  id: number;
  actorType: string;
  action: string;
  fromStatus: number | null;
  toStatus: number;
  afterSaleVersion: number;
  createdAt: number;
  reason: string;
  qualityApproved: boolean;
  inspection: Inspection | null;
};
export type ReturnAddress = {
  name: string;
  mobile: string;
  areaId: number;
  detail: string;
};
export type AreaOption = {
  id: number;
  parentId: number;
  name: string;
  type: number;
};
export type Decision = { id: number; status: number; authVersion: number };
export const afterSaleStates: Record<number, string> = {
  10: "待审核",
  20: "等待买家寄回",
  30: "待验货",
  35: "待核查",
  40: "待退款",
  45: "退款处理中",
  50: "退款已确认",
  61: "买家已撤销",
  62: "审核未通过",
  64: "寄回超期关闭",
};
export const afterSaleActions: Record<string, string> = {
  MEMBER_APPLY: "申请售后",
  ADMIN_APPROVE: "同意申请",
  ADMIN_REJECT: "拒绝申请",
  MEMBER_CANCEL: "买家撤销",
  MEMBER_DELIVERY: "买家提交寄回物流",
  ADMIN_RECEIVE: "验收通过",
  ADMIN_RECEIVE_DISPUTE: "记录验货争议",
  ADMIN_REFUND_PREPARE: "进入退款处理",
  ADMIN_REFUND_EXECUTE: "确认执行退款",
  ADMIN_REFUND_RETRY: "申请原号重试",
  ADMIN_REFUND_CONTINUE: "申请增加原号提交机会",
  SYSTEM_REFUND_SUCCESS: "确认退款结果",
  SYSTEM_STOCK_RETURN: "恢复售后库存",
  SYSTEM_COUPON_RETURN: "完成原券处理",
  SYSTEM_RETURN_EXPIRE: "寄回超期关闭",
};
export const integer = (x: unknown, min = 0): x is number =>
  typeof x === "number" && Number.isSafeInteger(x) && x >= min;
export const text = (x: unknown, max: number): x is string =>
  typeof x === "string" && x.length <= max;
export const record = (x: unknown): x is Record<string, unknown> =>
  !!x && typeof x === "object" && !Array.isArray(x);
export const stamp = (x: unknown) => integer(x, 1) && x <= 8640000000000000;
export const nullableTime = (x: unknown) => x === null || stamp(x);
export const nullableText = (x: unknown, max: number) =>
  x === null || text(x, max);
export const uuid = (x: unknown): x is string =>
  typeof x === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
    x,
  );
export const states = Object.keys(afterSaleStates).map(Number);
export function summary(x: unknown): x is AfterSaleSummary {
  return (
    record(x) &&
    integer(x.id, 1) &&
    typeof x.no === "string" &&
    /^AS[0-9a-f]{32}$/.test(x.no) &&
    integer(x.orderId, 1) &&
    typeof x.orderNo === "string" &&
    /^QH[0-9a-f]{32}$/.test(x.orderNo) &&
    integer(x.orderItemId, 1) &&
    integer(x.skuId, 1) &&
    text(x.spuName, 256) &&
    nullableText(x.picUrl, 2048) &&
    integer(x.count, 1) &&
    x.count <= 9999 &&
    [10, 20].includes(Number(x.way)) &&
    typeof x.way === "number" &&
    states.includes(Number(x.status)) &&
    typeof x.status === "number" &&
    integer(x.authVersion) &&
    integer(x.goodsRefundPrice) &&
    stamp(x.createdAt) &&
    stamp(x.updatedAt) &&
    Number(x.updatedAt) >= Number(x.createdAt) &&
    nullableTime(x.returnDeadline) &&
    stamp(x.serverNow) &&
    typeof x.overdue === "boolean"
  );
}
export function detail(x: unknown): x is AfterSaleDetail {
  if (!record(x)) return false;
  const fields: Record<string, unknown> = x;
  return (
    summary(x) &&
    text(fields.applyReason, 200) &&
    text(fields.applyDescription, 500) &&
    typeof fields.qualityApproved === "boolean" &&
    nullableText(fields.auditReason, 500) &&
    [fields.auditedAt, fields.deliveredAt, fields.inspectedAt].every(
      nullableTime,
    ) &&
    (fields.logisticsId === null || integer(fields.logisticsId, 1)) &&
    nullableText(fields.logisticsName, 64) &&
    nullableText(fields.logisticsNo, 64) &&
    integer(fields.receivedCount) &&
    fields.receivedCount <= 9999 &&
    integer(fields.salableCount) &&
    fields.salableCount <= fields.receivedCount &&
    integer(fields.unsalableCount) &&
    fields.unsalableCount === fields.receivedCount - fields.salableCount &&
    nullableText(fields.receiveReason, 500) &&
    (fields.refund === null || refundProgress(fields.refund)) &&
    (fields.status === 45
      ? fields.refund !== null && fields.refund.status !== "SUCCESS"
      : true) &&
    (fields.status === 50
      ? fields.refund !== null && fields.refund.status === "SUCCESS"
      : true)
  );
}
export function refundProgress(x: unknown): x is RefundProgress {
  return (
    record(x) &&
    integer(x.goodsPrice) &&
    integer(x.deliveryPrice) &&
    integer(x.totalPrice) &&
    Number.isSafeInteger(x.goodsPrice + x.deliveryPrice) &&
    x.totalPrice === x.goodsPrice + x.deliveryPrice &&
    ["PREPARED", "PROCESSING", "UNKNOWN", "SUCCESS", "FAILED"].includes(
      String(x.status),
    ) &&
    nullableTime(x.confirmedAt) &&
    (x.status === "SUCCESS") === (x.confirmedAt !== null) &&
    integer(x.restoreCount) &&
    x.restoreCount <= 9999 &&
    typeof x.stockRecovered === "boolean" &&
    typeof x.couponReturnRequired === "boolean" &&
    typeof x.couponRecovered === "boolean" &&
    (x.status === "SUCCESS" || (!x.stockRecovered && !x.couponRecovered))
  );
}
export function inspection(x: unknown): x is Inspection {
  return (
    record(x) &&
    integer(x.receivedCount) &&
    x.receivedCount <= 9999 &&
    integer(x.salableCount) &&
    x.salableCount <= x.receivedCount &&
    integer(x.unsalableCount) &&
    x.unsalableCount === x.receivedCount - x.salableCount &&
    typeof x.accepted === "boolean"
  );
}
export function log(x: unknown): x is AfterSaleLog {
  if (
    !record(x) ||
    !integer(x.id, 1) ||
    !["MEMBER", "ADMIN", "SYSTEM"].includes(String(x.actorType)) ||
    typeof x.action !== "string" ||
    !Object.hasOwn(afterSaleActions, x.action) ||
    !(
      x.fromStatus === null ||
      (typeof x.fromStatus === "number" && states.includes(x.fromStatus))
    ) ||
    typeof x.toStatus !== "number" ||
    !states.includes(x.toStatus) ||
    !integer(x.afterSaleVersion) ||
    !stamp(x.createdAt) ||
    !text(x.reason, 500) ||
    typeof x.qualityApproved !== "boolean"
  )
    return false;
  if (["ADMIN_RECEIVE", "ADMIN_RECEIVE_DISPUTE"].includes(x.action))
    return (
      inspection(x.inspection) &&
      x.inspection.accepted === (x.action === "ADMIN_RECEIVE")
    );
  return x.inspection === null;
}
