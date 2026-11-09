import { ApiError, request } from "./http";
import { context, credentials, demo, identities } from "./session";
import { cartIdentity } from "./member-cart";
import type { Settlement } from "./member-checkout";

export type OrderItem = {
  id: number;
  spuId: number;
  skuId: number;
  spuName: string;
  picUrl: string | null;
  count: number;
  refundCount: number;
  deliveryCount: number;
  price: number;
  goodsPrice: number;
  discountPrice: number;
  deliveryPrice: number;
  payPrice: number;
  properties?:
    | {
        propertyId: number;
        propertyName: string;
        valueId: number;
        valueName: string;
      }[]
    | null;
};
export type MemberOrder = {
  id: number;
  no: string;
  status: number;
  payStatus: boolean;
  payOrderId: number | null;
  productCount: number;
  itemCount: number;
  totalPrice: number;
  discountPrice: number;
  deliveryPrice: number;
  payPrice: number;
  refundPrice: number;
  cancelType: number | null;
  cancelResourcesReleased: boolean;
  authVersion: number;
  serverNow: number;
  createdAt: number;
  payDeadline: number;
  paidAt: number | null;
  cancelledAt: number | null;
  logisticsId: number | null;
  logisticsName: string | null;
  logisticsNo: string | null;
  deliveredAt: number | null;
  receivedAt: number | null;
  autoReceiveAt: number | null;
  afterSalePendingCount: number;
  requiresPayment: boolean;
  items: OrderItem[];
};
export type MemberOrderDetail = MemberOrder & {
  userRemark: string | null;
  receiverName: string;
  receiverMobile: string;
  receiverAreaId: number;
  receiverAreaName: string;
  receiverDetailAddress: string;
};
export type OrderCreation = {
  preparationId: number;
  requestId: string;
  quoteId: string;
  stage:
    | "PREPARED"
    | "PROCESSING"
    | "COMPENSATING"
    | "CREATED"
    | "FAILED"
    | "MANUAL";
  serverNow: number;
  order: MemberOrder | null;
};
export type OrderAttempt = {
  requestId: string;
  quoteId: string;
  remark: "";
  expectedPayPrice: number;
  outcome: "pending" | "created" | "failed" | "stopped";
};
const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const integer = (value: unknown, min = 0): value is number =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= min;
const timestamp = (value: unknown): value is number =>
  integer(value, 1) && value <= 8_640_000_000_000_000;

function validItem(value: unknown): value is OrderItem {
  if (!value || typeof value !== "object") return false;
  const x = value as OrderItem;
  return (
    [x.id, x.spuId, x.skuId].every((id) => integer(id, 1)) &&
    typeof x.spuName === "string" &&
    !!x.spuName.trim() &&
    x.spuName.length <= 256 &&
    (x.picUrl === null || typeof x.picUrl === "string") &&
    (x.properties == null || validProperties(x.properties)) &&
    integer(x.count, 1) &&
    x.count <= 9999 &&
    integer(x.refundCount) &&
    x.refundCount <= x.count &&
    integer(x.deliveryCount) &&
    x.deliveryCount <= x.count &&
    integer(x.price, 1) &&
    [x.goodsPrice, x.discountPrice, x.deliveryPrice, x.payPrice].every((n) =>
      integer(n),
    ) &&
    x.goodsPrice === x.price * x.count &&
    x.discountPrice <= x.goodsPrice &&
    Number.isSafeInteger(x.goodsPrice - x.discountPrice + x.deliveryPrice) &&
    x.payPrice === x.goodsPrice - x.discountPrice + x.deliveryPrice
  );
}
export function validMemberOrder(
  value: unknown,
  full = false,
): value is MemberOrder {
  if (!value || typeof value !== "object") return false;
  const x = value as MemberOrder;
  return (
    integer(x.id, 1) &&
    /^QH[0-9a-f]{32}$/.test(x.no) &&
    [0, 10, 20, 30, 40].includes(x.status) &&
    typeof x.payStatus === "boolean" &&
    (x.payOrderId === null || integer(x.payOrderId, 1)) &&
    integer(x.productCount, 1) &&
    integer(x.itemCount, 1) &&
    x.itemCount <= 200 &&
    [
      x.totalPrice,
      x.discountPrice,
      x.deliveryPrice,
      x.payPrice,
      x.refundPrice,
      x.authVersion,
    ].every((n) => integer(n)) &&
    x.discountPrice <= x.totalPrice &&
    x.refundPrice <= x.payPrice &&
    Number.isSafeInteger(x.totalPrice - x.discountPrice + x.deliveryPrice) &&
    x.payPrice === x.totalPrice - x.discountPrice + x.deliveryPrice &&
    [x.serverNow, x.createdAt, x.payDeadline].every(timestamp) &&
    x.payDeadline === x.createdAt + 1_800_000 &&
    (x.paidAt === null || timestamp(x.paidAt)) &&
    x.payStatus === (x.paidAt !== null) &&
    (x.payStatus
      ? [10, 20, 30, 40].includes(x.status) &&
        (x.payPrice === 0 || x.payOrderId !== null)
      : [0, 40].includes(x.status)) &&
    (x.cancelledAt === null || timestamp(x.cancelledAt)) &&
    (x.cancelType === null || [10, 20, 30].includes(x.cancelType)) &&
    (x.status !== 40 ||
      (x.payStatus ? x.cancelType === 20 : x.cancelType !== 20)) &&
    (x.status === 40
      ? x.cancelledAt !== null && x.cancelType !== null
      : x.cancelledAt === null && x.cancelType === null) &&
    typeof x.cancelResourcesReleased === "boolean" &&
    (x.status === 40 || !x.cancelResourcesReleased) &&
    x.requiresPayment === (x.status === 0 && !x.payStatus && x.payPrice > 0) &&
    validFulfillment(x) &&
    Array.isArray(x.items) &&
    x.items.length === (full ? x.itemCount : Math.min(x.itemCount, 3)) &&
    x.items.every(validItem) &&
    new Set(x.items.map((i) => i.id)).size === x.items.length &&
    ((!full && x.itemCount > 3) ||
      (x.items.reduce((n, i) => n + i.count, 0) === x.productCount &&
        x.items.reduce((n, i) => n + i.goodsPrice, 0) === x.totalPrice &&
        x.items.reduce((n, i) => n + i.discountPrice, 0) === x.discountPrice &&
        x.items.reduce((n, i) => n + i.deliveryPrice, 0) === x.deliveryPrice &&
        x.items.reduce((n, i) => n + i.payPrice, 0) === x.payPrice))
  );
}
function validFulfillment(x: MemberOrder) {
  if (
    !integer(x.afterSalePendingCount) ||
    x.afterSalePendingCount > 200 ||
    (x.afterSalePendingCount > 0 && !x.payStatus)
  )
    return false;
  if (
    ![20, 30].includes(x.status) &&
    !(x.status === 40 && x.payStatus && x.deliveredAt !== null)
  )
    return [
      x.logisticsId,
      x.logisticsName,
      x.logisticsNo,
      x.deliveredAt,
      x.receivedAt,
      x.autoReceiveAt,
    ].every((value) => value === null);
  return (
    integer(x.logisticsId, 1) &&
    text(x.logisticsName, 64) &&
    typeof x.logisticsNo === "string" &&
    /^[A-Za-z0-9][A-Za-z0-9-]{3,63}$/.test(x.logisticsNo) &&
    timestamp(x.deliveredAt) &&
    x.deliveredAt >= x.createdAt &&
    x.autoReceiveAt === x.deliveredAt + 14 * 86400000 &&
    (x.status === 20
      ? x.receivedAt === null
      : (x.status === 40 && x.receivedAt === null) ||
        (timestamp(x.receivedAt) && x.receivedAt >= x.deliveredAt))
  );
}
function validCreation(value: unknown): value is OrderCreation {
  if (!value || typeof value !== "object") return false;
  const x = value as OrderCreation;
  return (
    integer(x.preparationId, 1) &&
    uuid.test(x.requestId) &&
    uuid.test(x.quoteId) &&
    timestamp(x.serverNow) &&
    [
      "PREPARED",
      "PROCESSING",
      "COMPENSATING",
      "CREATED",
      "FAILED",
      "MANUAL",
    ].includes(x.stage) &&
    (x.stage === "CREATED"
      ? validMemberOrder(x.order) && x.order.id === x.preparationId
      : x.order === null)
  );
}
function validAttempt(value: unknown): value is OrderAttempt {
  if (!value || typeof value !== "object") return false;
  const x = value as OrderAttempt;
  return (
    uuid.test(x.requestId) &&
    uuid.test(x.quoteId) &&
    x.remark === "" &&
    integer(x.expectedPayPrice) &&
    ["pending", "created", "failed", "stopped"].includes(x.outcome)
  );
}
function storageKey() {
  return `qinghe:order-create:${cartIdentity()}`;
}
export function lastOrderAttempt(): OrderAttempt | null {
  try {
    const raw = sessionStorage.getItem(storageKey());
    if (raw === null) return null;
    const parsed: unknown = JSON.parse(raw);
    if (validAttempt(parsed)) return parsed;
  } catch {
    throw new ApiError("无法读取上次下单请求，请恢复浏览器存储后再操作", -2);
  }
  throw new ApiError(
    "上次下单记录无法识别，请先核对本人订单，避免重复购买",
    -2,
  );
}
export function newOrderAttempt(quote: Settlement): OrderAttempt {
  const old = lastOrderAttempt();
  if (old?.quoteId === quote.quoteId) return old;
  if (old?.outcome === "pending")
    throw new ApiError("上次订单结果待确认，请先查询原请求", 409);
  if (
    !quote.snapshotPresent ||
    quote.bound ||
    quote.expired ||
    quote.cartChanged ||
    !quote.canPurchase ||
    !integer(quote.payPrice) ||
    !uuid.test(quote.quoteId)
  )
    throw new ApiError("请重新确认有效报价", 409);
  const value: OrderAttempt = {
    requestId: crypto.randomUUID(),
    quoteId: quote.quoteId,
    remark: "",
    expectedPayPrice: quote.payPrice,
    outcome: "pending",
  };
  save(value, storageKey());
  return value;
}
function save(value: OrderAttempt, key: string) {
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    throw new ApiError(
      "无法保存原下单请求，本次不要重新下单，请恢复浏览器存储",
      -2,
    );
  }
}
async function call(path: string, init: RequestInit = {}) {
  if (
    demo ||
    context.realm !== "member" ||
    !identities.member ||
    identities.member.tenantCode !== context.tenant
  )
    throw new ApiError("请从当前商城登录", 401);
  const actor = cartIdentity(),
    bearer = credentials();
  if (!bearer.accessToken) throw new ApiError("请重新登录", 401);
  const value = await request<unknown>(
    `/app-api/trade/order/${path}${path.includes("?") ? "&" : "?"}store=${encodeURIComponent(context.tenant)}`,
    { ...bearer, timeoutMs: init.method ? 125000 : 15000 },
    init,
  );
  if (
    actor !== cartIdentity() ||
    bearer.accessToken !== credentials().accessToken
  )
    throw new ApiError("登录凭据已变化，请重新查询原订单", 409, !!init.method);
  return value;
}
export { call as memberOrderRequest };
function accept(value: unknown, input: OrderAttempt): OrderCreation | null {
  if (value === null) return null;
  if (
    !validCreation(value) ||
    value.requestId !== input.requestId ||
    value.quoteId !== input.quoteId ||
    (value.order !== null && value.order.payPrice !== input.expectedPayPrice)
  )
    throw new ApiError("原订单响应不完整，请保留原请求并重新查询", 503, true);
  const old = lastOrderAttempt();
  if (
    !old ||
    old.requestId !== input.requestId ||
    old.quoteId !== input.quoteId
  )
    throw new ApiError("当前下单记录已变化，请查询本人订单", 409, true);
  save(
    {
      ...old,
      outcome:
        value.stage === "CREATED"
          ? "created"
          : value.stage === "FAILED"
            ? "failed"
            : "pending",
    },
    storageKey(),
  );
  return value;
}
export async function createMemberOrder(input: OrderAttempt) {
  if (!validAttempt(input)) throw new ApiError("原下单请求不正确", 400);
  const saved = lastOrderAttempt();
  if (
    !saved ||
    saved.requestId !== input.requestId ||
    saved.quoteId !== input.quoteId ||
    saved.expectedPayPrice !== input.expectedPayPrice
  )
    throw new ApiError("原下单请求尚未保存，本次未提交", 409);
  const value = accept(
    await call("create", {
      method: "POST",
      body: JSON.stringify({
        requestId: input.requestId,
        quoteId: input.quoteId,
        remark: "",
      }),
    }),
    input,
  );
  if (value === null)
    throw new ApiError("创建结果待确认，请查询原请求", 503, true);
  return value;
}
export async function getOrderCreation(input: OrderAttempt) {
  if (!validAttempt(input)) throw new ApiError("原下单请求不正确", 400);
  return accept(
    await call(
      `create-result?requestId=${encodeURIComponent(input.requestId)}`,
    ),
    input,
  );
}
export async function stopOrderCreation(input: OrderAttempt) {
  if (!validAttempt(input)) throw new ApiError("原下单请求不正确", 400);
  const value = await call("stop-create", {
    method: "POST",
    body: JSON.stringify({
      requestId: input.requestId,
      quoteId: input.quoteId,
      remark: "",
    }),
  });
  if (
    !value ||
    typeof value !== "object" ||
    typeof (value as { stopped?: unknown }).stopped !== "boolean"
  )
    throw new ApiError("原请求结束结果待确认，请查询或重试同一操作", 503, true);
  const response = value as { stopped: boolean; result: unknown };
  if (response.stopped) {
    if (response.result !== null)
      throw new ApiError("原请求结束响应不完整", 503, true);
    const saved = lastOrderAttempt();
    if (
      !saved ||
      saved.requestId !== input.requestId ||
      saved.quoteId !== input.quoteId
    )
      throw new ApiError("当前下单记录已变化，请核对本人订单", 409, true);
    save({ ...saved, outcome: "stopped" }, storageKey());
    return { stopped: true as const, result: null };
  }
  const result = accept(response.result, input);
  if (result === null)
    throw new ApiError("原受理结果待确认，请查询原请求", 503, true);
  return { stopped: false as const, result };
}

// Queries use the same own-order endpoints as the selected yudao App controller.
// Detail contains every original line; list/create results contain at most three.
export async function readMemberOrders(pageNo: number, status: number | null) {
  if (
    !integer(pageNo, 1) ||
    pageNo > 10001 ||
    (status !== null && ![0, 10, 20, 30, 40].includes(status))
  )
    throw new ApiError("订单分页参数不正确", 400);
  const value = await call(
    `page?pageNo=${pageNo}&pageSize=10${status === null ? "" : `&status=${status}`}`,
  );
  const result = value as { list: MemberOrder[]; total: number } | null;
  if (
    !result ||
    !integer(result.total) ||
    !Array.isArray(result.list) ||
    result.list.length > 10 ||
    result.list.length > result.total ||
    !result.list.every(
      (row) =>
        validMemberOrder(row) && (status === null || row.status === status),
    ) ||
    new Set(result.list.map((row) => row.id)).size !== result.list.length
  )
    throw new ApiError("订单列表响应不完整，请重新查询", 503);
  return result;
}
function text(
  value: unknown,
  max: number,
  allowEmpty = false,
): value is string {
  return (
    typeof value === "string" &&
    value.length <= max &&
    (allowEmpty || !!value.trim())
  );
}
function validProperties(
  value: unknown,
): value is NonNullable<OrderItem["properties"]> {
  return (
    Array.isArray(value) &&
    value.length <= 200 &&
    value.every(
      (p) =>
        !!p &&
        integer(p.propertyId) &&
        integer(p.valueId) &&
        text(p.propertyName, 256) &&
        text(p.valueName, 256),
    )
  );
}
export async function readMemberOrder(id: number) {
  if (!integer(id, 1)) throw new ApiError("订单编号不正确", 400);
  const value = await call(`get-detail?id=${id}`);
  const row = value as MemberOrderDetail;
  if (
    !validMemberOrder(row, true) ||
    row.id !== id ||
    !(row.userRemark === null || text(row.userRemark, 500, true)) ||
    !text(row.receiverName, 256) ||
    !text(row.receiverMobile, 64) ||
    !integer(row.receiverAreaId, 1) ||
    !text(row.receiverAreaName, 512, true) ||
    !text(row.receiverDetailAddress, 1024) ||
    !row.items.every((item) => validProperties(item.properties))
  )
    throw new ApiError("订单详情响应不完整，请重新查询", 503);
  return row;
}
function cancellationKey() {
  return `qinghe:order-cancel:${cartIdentity()}`;
}
export function pendingOrderCancellation(): number | null {
  try {
    const value = sessionStorage.getItem(cancellationKey());
    if (value === null) return null;
    const id: unknown = JSON.parse(value);
    if (integer(id, 1)) return id;
  } catch {
    throw new ApiError("无法读取上次取消记录，请恢复浏览器存储后重试", -2);
  }
  throw new ApiError("上次取消记录无法识别，请先核对本人订单", -2);
}
function clearCancellation(key: string, id: number) {
  try {
    if (sessionStorage.getItem(key) === JSON.stringify(id))
      sessionStorage.removeItem(key);
  } catch {
    throw new ApiError(
      "订单状态已确认，但无法更新本地记录，请恢复浏览器存储后重新查询",
      -2,
    );
  }
}
export function reconcileOrderCancellation(order: MemberOrder) {
  if (
    pendingOrderCancellation() === order.id &&
    (order.payStatus || (order.status === 40 && order.cancelResourcesReleased))
  )
    clearCancellation(cancellationKey(), order.id);
}
export async function cancelMemberOrder(id: number) {
  if (!integer(id, 1)) throw new ApiError("订单编号不正确", 400);
  const old = pendingOrderCancellation();
  if (old !== null && old !== id)
    throw new ApiError("请先确认上次取消的订单结果", 409);
  const key = cancellationKey();
  try {
    // Only the original order ID is persisted; no address, order snapshot or token.
    sessionStorage.setItem(key, JSON.stringify(id));
  } catch {
    throw new ApiError("无法保存取消记录，本次未发送，请恢复浏览器存储", -2);
  }
  const value = await call("cancel", {
    method: "POST",
    body: JSON.stringify({ id }),
  });
  if (value !== true)
    throw new ApiError("取消结果待确认，请查询原订单", 503, true);
  clearCancellation(key, id);
}

export async function receiveMemberOrder(id: number, expectedVersion: number) {
  if (!integer(id, 1) || !integer(expectedVersion))
    throw new ApiError("订单编号或版本不正确，请重新查询", 400);
  const result = await call("receive", {
    method: "POST",
    body: JSON.stringify({ id, expectedVersion }),
  });
  if (result !== true)
    throw new ApiError("收货结果待确认，请查询原订单", 503, true);
}
