import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";
import type { OrderItem } from "./member-order";

// Selected upstream mall/trade/order page/detail/delivery API duties; current project HTTP contract.
export type MerchantOrder = {
  id: number;
  no: string;
  status: number;
  authVersion: number;
  payStatus: boolean;
  productCount: number;
  itemCount: number;
  totalPrice: number;
  discountPrice: number;
  deliveryPrice: number;
  payPrice: number;
  refundPrice: number;
  cancelResourcesReleased: boolean;
  afterSalePendingCount: number;
  logisticsId: number | null;
  logisticsName: string | null;
  logisticsNo: string | null;
  createdAt: number;
  paidAt: number | null;
  deliveredAt: number | null;
  receivedAt: number | null;
  cancelledAt: number | null;
  serverNow: number;
  items: OrderItem[];
};
export type MerchantDetail = MerchantOrder & {
  receiverNameMasked: string;
  receiverMobileMasked: string;
  receiverAreaName: string;
};
export type DeliveryDetail = {
  id: number;
  no: string;
  authVersion: number;
  receiverName: string;
  receiverMobile: string;
  receiverAreaId: number;
  receiverAreaName: string;
  receiverDetailAddress: string;
  userRemark: string | null;
  items: Pick<
    OrderItem,
    | "id"
    | "skuId"
    | "spuName"
    | "count"
    | "refundCount"
    | "deliveryCount"
    | "properties"
  >[];
};
export type Carrier = { id: number; code: string; name: string };
export type ParcelCommand = {
  id: number;
  expectedVersion: number;
  logisticsId: number;
  logisticsNo: string;
  reason?: string;
};
export type OrderLog = {
  id: number;
  actorId: number;
  actorType: string;
  action: string;
  fromStatus: number | null;
  toStatus: number;
  orderVersion: number;
  createdAt: number;
  details: {
    previousLogisticsId: number | null;
    previousLogisticsName: string | null;
    previousLogisticsNo: string | null;
    logisticsId: number;
    logisticsName: string;
    logisticsNo: string;
    reason: string | null;
  } | null;
};
const int = (x: unknown, min = 0): x is number =>
  typeof x === "number" && Number.isSafeInteger(x) && x >= min;
const text = (x: unknown, max: number): x is string =>
  typeof x === "string" && x.length <= max;
const stamp = (x: unknown): x is number => int(x, 1) && x <= 8640000000000000;
const nullableTime = (x: unknown) => x === null || stamp(x);
const tracking = (x: unknown): x is string =>
  typeof x === "string" && /^[A-Za-z0-9][A-Za-z0-9-]{3,63}$/.test(x);
export function merchantOrderIdentity() {
  return `${context.realm}:${identities[context.realm]?.tenantId || ""}:${identities[context.realm]?.userId || ""}`;
}
async function call(path: string, init: RequestInit = {}) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口查询订单", 403);
  const actor = merchantOrderIdentity(),
    bearer = credentials();
  if (!bearer.accessToken) throw new ApiError("请重新登录", 401);
  const result = await request<unknown>(
    `/admin-api/trade/order/${path}`,
    { ...bearer, timeoutMs: 15000 },
    init,
  );
  if (
    actor !== merchantOrderIdentity() ||
    bearer.accessToken !== credentials().accessToken
  )
    throw new ApiError("身份已变化，请重新查询原订单", 409, !!init.method);
  return result;
}
function properties(x: unknown) {
  return (
    Array.isArray(x) &&
    x.length <= 200 &&
    x.every(
      (p) =>
        p &&
        int(p.propertyId) &&
        int(p.valueId) &&
        text(p.propertyName, 256) &&
        text(p.valueName, 256),
    )
  );
}
function quantities(x: OrderItem | DeliveryDetail["items"][number]) {
  return (
    int(x.id, 1) &&
    int(x.skuId, 1) &&
    text(x.spuName, 256) &&
    int(x.count, 1) &&
    x.count <= 9999 &&
    int(x.refundCount) &&
    x.refundCount <= x.count &&
    int(x.deliveryCount) &&
    x.deliveryCount <= x.count &&
    properties(x.properties)
  );
}
function item(x: OrderItem) {
  return (
    x &&
    quantities(x) &&
    int(x.spuId, 1) &&
    (x.picUrl === null || text(x.picUrl, 2048)) &&
    int(x.price, 1) &&
    [x.goodsPrice, x.discountPrice, x.deliveryPrice, x.payPrice].every((n) =>
      int(n),
    ) &&
    x.goodsPrice === x.price * x.count &&
    x.discountPrice <= x.goodsPrice &&
    Number.isSafeInteger(x.goodsPrice - x.discountPrice + x.deliveryPrice) &&
    x.payPrice === x.goodsPrice - x.discountPrice + x.deliveryPrice
  );
}
function order(value: unknown, full = false): value is MerchantOrder {
  if (!value || typeof value !== "object") return false;
  const x = value as MerchantOrder;
  return (
    int(x.id, 1) &&
    /^QH[0-9a-f]{32}$/.test(x.no) &&
    [0, 10, 20, 30, 40].includes(x.status) &&
    int(x.authVersion) &&
    typeof x.payStatus === "boolean" &&
    int(x.productCount, 1) &&
    int(x.itemCount, 1) &&
    x.itemCount <= 200 &&
    [
      x.totalPrice,
      x.discountPrice,
      x.deliveryPrice,
      x.payPrice,
      x.refundPrice,
    ].every((n) => int(n)) &&
    x.discountPrice <= x.totalPrice &&
    x.refundPrice <= x.payPrice &&
    Number.isSafeInteger(x.totalPrice - x.discountPrice + x.deliveryPrice) &&
    x.payPrice === x.totalPrice - x.discountPrice + x.deliveryPrice &&
    typeof x.cancelResourcesReleased === "boolean" &&
    int(x.afterSalePendingCount) &&
    x.afterSalePendingCount <= 200 &&
    stamp(x.createdAt) &&
    stamp(x.serverNow) &&
    [x.paidAt, x.deliveredAt, x.receivedAt, x.cancelledAt].every(
      nullableTime,
    ) &&
    x.payStatus === (x.paidAt !== null) &&
    (x.payStatus
      ? [10, 20, 30, 40].includes(x.status)
      : [0, 40].includes(x.status)) &&
    ([20, 30].includes(x.status) ||
    (x.status === 40 && x.payStatus && x.deliveredAt !== null)
      ? int(x.logisticsId, 1) &&
        text(x.logisticsName, 64) &&
        tracking(x.logisticsNo) &&
        stamp(x.deliveredAt) &&
        x.deliveredAt >= x.createdAt
      : x.logisticsId === null &&
        x.logisticsName === null &&
        x.logisticsNo === null &&
        x.deliveredAt === null) &&
    (x.status === 30
      ? stamp(x.receivedAt) &&
        x.deliveredAt !== null &&
        x.receivedAt >= x.deliveredAt
      : x.receivedAt === null ||
        (x.status === 40 &&
          stamp(x.receivedAt) &&
          stamp(x.deliveredAt) &&
          x.receivedAt >= x.deliveredAt)) &&
    Array.isArray(x.items) &&
    x.items.length === (full ? x.itemCount : Math.min(x.itemCount, 3)) &&
    x.items.every(item) &&
    new Set(x.items.map((i) => i.id)).size === x.items.length &&
    ((!full && x.itemCount > 3) ||
      (x.items.reduce((a, i) => a + i.count, 0) === x.productCount &&
        x.items.reduce((a, i) => a + i.goodsPrice, 0) === x.totalPrice &&
        x.items.reduce((a, i) => a + i.discountPrice, 0) === x.discountPrice &&
        x.items.reduce((a, i) => a + i.deliveryPrice, 0) === x.deliveryPrice &&
        x.items.reduce((a, i) => a + i.payPrice, 0) === x.payPrice))
  );
}
function page<T>(value: unknown, valid: (x: unknown) => x is T): PageResult<T> {
  const x = value as PageResult<T>;
  if (
    !x ||
    !int(x.total) ||
    !Array.isArray(x.list) ||
    x.list.length > 20 ||
    x.list.length > x.total ||
    !x.list.every((value) => valid(value))
  )
    throw new ApiError("分页响应不完整，请重新查询", 503);
  return x;
}
export async function merchantOrderPage(
  pageNo: number,
  no: string,
  status: string,
) {
  if (
    !int(pageNo, 1) ||
    pageNo > 5001 ||
    (no && !/^QH[0-9a-f]{32}$/.test(no)) ||
    (status && !["0", "10", "20", "30", "40"].includes(status))
  )
    throw new ApiError("请输入完整订单号及有效筛选条件", 400);
  const q = new URLSearchParams({ pageNo: String(pageNo), pageSize: "20" });
  if (no) q.set("no", no);
  if (status) q.set("status", status);
  return page(await call(`page?${q}`), order);
}
export async function merchantOrderDetail(id: number) {
  if (!int(id, 1)) throw new ApiError("订单编号不正确", 400);
  const x = (await call(`get-detail?id=${id}`)) as MerchantDetail;
  if (
    !order(x, true) ||
    x.id !== id ||
    !text(x.receiverNameMasked, 256) ||
    !text(x.receiverMobileMasked, 64) ||
    !text(x.receiverAreaName, 512)
  )
    throw new ApiError("订单详情不完整", 503);
  return x;
}
export async function merchantDeliveryDetail(id: number) {
  if (!int(id, 1)) throw new ApiError("订单编号不正确", 400);
  const x = (await call(`get-delivery-detail?id=${id}`)) as DeliveryDetail;
  if (
    !x ||
    x.id !== id ||
    !/^QH[0-9a-f]{32}$/.test(x.no) ||
    !int(x.authVersion) ||
    !text(x.receiverName, 256) ||
    !text(x.receiverMobile, 64) ||
    !int(x.receiverAreaId, 1) ||
    !text(x.receiverAreaName, 512) ||
    !text(x.receiverDetailAddress, 1024) ||
    !(x.userRemark === null || text(x.userRemark, 500)) ||
    !Array.isArray(x.items) ||
    !x.items.length ||
    x.items.length > 200 ||
    !x.items.every(quantities) ||
    new Set(x.items.map((i) => i.id)).size !== x.items.length
  )
    throw new ApiError("发货资料不完整", 503);
  return x;
}
export async function merchantCarriers() {
  const x = await call("express-options");
  if (
    !Array.isArray(x) ||
    x.length > 200 ||
    !x.every(
      (c) =>
        c &&
        int(c.id, 1) &&
        /^[A-Z0-9_-]{2,32}$/.test(c.code) &&
        text(c.name, 64),
    ) ||
    new Set(x.map((c) => c.id)).size !== x.length
  )
    throw new ApiError("承运商选项不完整", 503);
  return x as Carrier[];
}
export async function merchantParcel(
  command: ParcelCommand,
  correction: boolean,
) {
  if (
    !int(command.id, 1) ||
    !int(command.expectedVersion) ||
    !int(command.logisticsId, 1) ||
    !tracking(command.logisticsNo) ||
    (correction &&
      (!text(command.reason, 500) ||
        !command.reason.trim() ||
        /[\x00-\x1f\x7f]/.test(command.reason)))
  )
    throw new ApiError("请填写承运商、有效单号及更正原因", 400);
  const body = {
    id: command.id,
    expectedVersion: command.expectedVersion,
    logisticsId: command.logisticsId,
    logisticsNo: command.logisticsNo,
    ...(correction ? { reason: command.reason!.trim() } : {}),
  };
  if (
    (await call(correction ? "update-logistics" : "delivery", {
      method: "POST",
      body: JSON.stringify(body),
    })) !== true
  )
    throw new ApiError("操作结果待确认，请查询原订单", 503, true);
}
const actions = [
  "AFTERSALE_APPLY",
  "AFTERSALE_APPROVE",
  "AFTERSALE_REJECT",
  "AFTERSALE_CANCEL",
  "AFTERSALE_DELIVERY",
  "AFTERSALE_INSPECT",
  "AFTERSALE_REFUND_PREPARE",
  "AFTERSALE_REFUND_EXECUTE",
  "AFTERSALE_REFUND_RETRY",
  "AFTERSALE_REFUND_CONTINUE",
  "AFTERSALE_REFUND_SUCCESS",
  "AFTERSALE_STOCK_RETURN",
  "AFTERSALE_COUPON_RETURN",
  "AFTERSALE_EXPIRE",
  "CREATE",
  "PAY_BIND",
  "FREE_PAY",
  "PAY_CONFIRM",
  "MEMBER_CANCEL",
  "TIMEOUT_CANCEL",
  "CANCEL_RELEASED",
  "ADMIN_DELIVERY",
  "ADMIN_LOGISTICS_UPDATE",
  "MEMBER_RECEIVE",
  "SYSTEM_RECEIVE",
];
export async function merchantOrderLogs(id: number, pageNo: number) {
  if (!int(id, 1) || !int(pageNo, 1) || pageNo > 5001)
    throw new ApiError("日志分页参数不正确", 400);
  return page(
    await call(`log-page?id=${id}&pageNo=${pageNo}&pageSize=20`),
    (value: unknown): value is OrderLog => {
      const x = value as OrderLog;
      return (
        !!x &&
        int(x.id, 1) &&
        int(x.actorId) &&
        ["ADMIN", "MEMBER", "SYSTEM"].includes(x.actorType) &&
        (x.actorType === "SYSTEM" ? x.actorId === 0 : x.actorId > 0) &&
        actions.includes(x.action) &&
        (x.fromStatus === null || [0, 10, 20, 30, 40].includes(x.fromStatus)) &&
        [0, 10, 20, 30, 40].includes(x.toStatus) &&
        int(x.orderVersion) &&
        stamp(x.createdAt) &&
        (x.details === null ||
          (int(x.details.logisticsId, 1) &&
            text(x.details.logisticsName, 64) &&
            tracking(x.details.logisticsNo) &&
            (x.details.reason === null || text(x.details.reason, 500)) &&
            (x.details.previousLogisticsId === null ||
              int(x.details.previousLogisticsId, 1)) &&
            (x.details.previousLogisticsName === null ||
              text(x.details.previousLogisticsName, 64)) &&
            (x.details.previousLogisticsNo === null ||
              tracking(x.details.previousLogisticsNo))))
      );
    },
  );
}

/** Yudao summary-query duty adapted to the local trade ledger contract. */
export type OperatingSummary = {
  timezone: "Asia/Shanghai";
  beginTime: number;
  endTime: number;
  queriedAt: number;
  status: number | null;
  createdOrderCount: string;
  paidOrderCount: string;
  paidAmount: string;
  successfulRefundCount: string;
  refundGoodsAmount: string;
  refundDeliveryAmount: string;
  refundAmount: string;
  periodNetAmount: string;
  paidCohortAccumulatedRefundAmount: string;
  paidCohortCurrentNetAmount: string;
};
const unsignedAggregate = (value: unknown): value is string =>
  typeof value === "string" && /^(0|[1-9][0-9]{0,64})$/.test(value);
const signedAggregate = (value: unknown): value is string =>
  unsignedAggregate(value) ||
  (typeof value === "string" && /^-[1-9][0-9]{0,64}$/.test(value));
function calendarDay(value: string): number {
  if (!/^20[0-9]{2}-[0-9]{2}-[0-9]{2}$/.test(value))
    throw new ApiError("请选择有效的统计日期", 400);
  const stamp = Date.parse(`${value}T00:00:00Z`);
  if (
    !Number.isFinite(stamp) ||
    new Date(stamp).toISOString().slice(0, 10) !== value
  )
    throw new ApiError("请选择有效的统计日期", 400);
  return stamp;
}
export function operatingMoney(value: string): string {
  if (!signedAggregate(value)) return "—";
  const negative = value.startsWith("-"),
    digits = negative ? value.slice(1) : value;
  const padded = digits.padStart(3, "0");
  return `${negative ? "−" : ""}${padded.slice(0, -2)}.${padded.slice(-2)}`;
}
export async function merchantOperatingSummary(
  beginDate: string,
  endDate: string,
  status: string,
) {
  const beginDay = calendarDay(beginDate),
    endDay = calendarDay(endDate) + 86_400_000;
  if (
    endDay <= beginDay ||
    endDay - beginDay > 366 * 86_400_000 ||
    !["", "0", "10", "20", "30", "40"].includes(status)
  )
    throw new ApiError("请选择不超过366天的有效日期范围和状态", 400);
  const beginTime = `${beginDate}T00:00:00`,
    endTime = `${new Date(endDay).toISOString().slice(0, 10)}T00:00:00`;
  const params = new URLSearchParams({ beginTime, endTime });
  if (status !== "") params.set("status", status);
  const result = await call(`summary?${params}`);
  if (!result || typeof result !== "object")
    throw new ApiError("经营汇总返回不完整，请重试", 503);
  const x = result as OperatingSummary;
  const money = [
    x.paidAmount,
    x.refundGoodsAmount,
    x.refundDeliveryAmount,
    x.refundAmount,
    x.paidCohortAccumulatedRefundAmount,
    x.paidCohortCurrentNetAmount,
  ];
  if (
    x.timezone !== "Asia/Shanghai" ||
    x.beginTime !== Date.parse(`${beginTime}+08:00`) ||
    x.endTime !== Date.parse(`${endTime}+08:00`) ||
    !stamp(x.queriedAt) ||
    x.status !== (status === "" ? null : Number(status)) ||
    ![x.createdOrderCount, x.paidOrderCount, x.successfulRefundCount].every(
      unsignedAggregate,
    ) ||
    !money.every(unsignedAggregate) ||
    !signedAggregate(x.periodNetAmount)
  )
    throw new ApiError("经营汇总口径或金额返回异常，请重试", 503);
  if (
    BigInt(x.refundGoodsAmount) + BigInt(x.refundDeliveryAmount) !==
      BigInt(x.refundAmount) ||
    BigInt(x.paidAmount) - BigInt(x.refundAmount) !==
      BigInt(x.periodNetAmount) ||
    BigInt(x.paidAmount) - BigInt(x.paidCohortAccumulatedRefundAmount) !==
      BigInt(x.paidCohortCurrentNetAmount)
  )
    throw new ApiError("经营汇总合计不一致，请重试", 503);
  return x;
}
