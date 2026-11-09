import { ApiError, request } from "./http";
import { context, credentials, demo, identities } from "./session";
import { cartIdentity, isCartProduct, type CartProduct } from "./member-cart";
export type SettlementInput = {
  requestId: string;
  cartVersion: number;
  addressId: number;
  couponId: number | null;
};
export type SettlementLine = {
  cartId: number;
  cartLineVersion: number;
  count: number;
  product: CartProduct;
  goodsPrice: number;
  discountPrice: number;
  deliveryPrice: number;
  payPrice: number;
};
export type Settlement = {
  quoteId: string;
  requestId: string;
  cartVersion: number;
  addressId: number;
  couponId: number | null;
  couponName: string | null;
  serverNow: number;
  expiresAt: number;
  canPurchase: boolean;
  expired: boolean;
  cartChanged: boolean;
  bound: boolean;
  snapshotPresent: boolean;
  goodsPrice: number | null;
  discountPrice: number | null;
  deliveryPrice: number | null;
  payPrice: number | null;
  address: {
    id: number;
    authVersion: number;
    name: string;
    mobile: string;
    areaId: number;
    areaName: string;
    detailAddress: string;
  } | null;
  items: SettlementLine[] | null;
};
const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const integer = (v: unknown, min = 0): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v >= min;
async function call<T>(path: string, init: RequestInit = {}) {
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
  const value = await request<T>(
    `/app-api/trade/order/${path}${path.includes("?") ? "&" : "?"}store=${encodeURIComponent(context.tenant)}`,
    { ...bearer, timeoutMs: 35000 },
    init,
  );
  if (
    actor !== cartIdentity() ||
    bearer.accessToken !== credentials().accessToken
  )
    throw new ApiError("登录凭据已变化，请查询原报价", 409, !!init.method);
  return value;
}
function valid(value: unknown): value is Settlement {
  if (!value || typeof value !== "object") return false;
  const x = value as Settlement;
  if (
    !uuid.test(x.quoteId) ||
    !uuid.test(x.requestId) ||
    !integer(x.cartVersion) ||
    !integer(x.addressId, 1) ||
    (x.couponId !== null && !integer(x.couponId, 1)) ||
    !integer(x.serverNow, 1) ||
    !integer(x.expiresAt, 1) ||
    ![
      x.canPurchase,
      x.expired,
      x.cartChanged,
      x.bound,
      x.snapshotPresent,
    ].every((v) => typeof v === "boolean")
  )
    return false;
  if (!x.snapshotPresent)
    return (
      x.address === null &&
      x.items === null &&
      [x.goodsPrice, x.discountPrice, x.deliveryPrice, x.payPrice].every(
        (v) => v === null,
      )
    );
  if (
    !integer(x.goodsPrice) ||
    !integer(x.discountPrice) ||
    !integer(x.deliveryPrice) ||
    !integer(x.payPrice) ||
    x.discountPrice > x.goodsPrice ||
    !Number.isSafeInteger(x.goodsPrice - x.discountPrice + x.deliveryPrice) ||
    x.payPrice !== x.goodsPrice - x.discountPrice + x.deliveryPrice ||
    !x.address ||
    x.address.id !== x.addressId ||
    !integer(x.address.authVersion) ||
    typeof x.address.name !== "string" ||
    !x.address.name.trim() ||
    x.address.name.length > 50 ||
    !/^1[3-9][0-9]{9}$/.test(x.address.mobile) ||
    !integer(x.address.areaId, 1) ||
    typeof x.address.areaName !== "string" ||
    typeof x.address.detailAddress !== "string" ||
    (x.couponId === null
      ? x.couponName !== null
      : typeof x.couponName !== "string") ||
    !Array.isArray(x.items) ||
    !x.items.length ||
    x.items.length > 200
  )
    return false;
  if (
    !x.items.every(
      (line) =>
        line &&
        integer(line.cartId, 1) &&
        integer(line.cartLineVersion) &&
        integer(line.count, 1) &&
        line.count <= 9999 &&
        isCartProduct(line.product) &&
        integer(line.goodsPrice) &&
        integer(line.discountPrice) &&
        integer(line.deliveryPrice) &&
        integer(line.payPrice) &&
        line.goodsPrice === line.product.price * line.count &&
        line.discountPrice <= line.goodsPrice &&
        Number.isSafeInteger(
          line.goodsPrice - line.discountPrice + line.deliveryPrice,
        ) &&
        line.payPrice ===
          line.goodsPrice - line.discountPrice + line.deliveryPrice,
    )
  )
    return false;
  if (
    new Set(x.items.map((v) => v.cartId)).size !== x.items.length ||
    new Set(x.items.map((v) => v.product.skuId)).size !== x.items.length
  )
    return false;
  const sum = (
    key: "goodsPrice" | "discountPrice" | "deliveryPrice" | "payPrice",
  ) => x.items!.reduce((n, line) => n + line[key], 0);
  return ["goodsPrice", "discountPrice", "deliveryPrice", "payPrice"].every(
    (key) => {
      const k = key as
        "goodsPrice" | "discountPrice" | "deliveryPrice" | "payPrice";
      return Number.isSafeInteger(sum(k)) && sum(k) === x[k];
    },
  );
}
function input(value: unknown): value is SettlementInput {
  if (!value || typeof value !== "object") return false;
  const x = value as SettlementInput;
  return (
    uuid.test(x.requestId) &&
    integer(x.cartVersion) &&
    integer(x.addressId, 1) &&
    (x.couponId === null || integer(x.couponId, 1))
  );
}
function key() {
  return `qinghe:settlement:${cartIdentity()}`;
}
export function lastSettlementInput(): SettlementInput | null {
  try {
    const raw = sessionStorage.getItem(key());
    if (!raw) return null;
    const x = JSON.parse(raw);
    if (input(x)) return x;
  } catch {
    throw new ApiError("无法读取上次报价请求，请恢复浏览器存储", -2);
  }
  throw new ApiError("上次报价请求格式不正确，请重新报价", -2);
}
export function newSettlementInput(
  cartVersion: number,
  addressId: number,
  couponId: number | null,
) {
  const x = {
    requestId: crypto.randomUUID(),
    cartVersion,
    addressId,
    couponId,
  };
  if (!input(x)) throw new ApiError("请先选择当前购物车和有效收货地址", 400);
  try {
    sessionStorage.setItem(key(), JSON.stringify(x));
  } catch {
    throw new ApiError("无法保存报价请求，本次未提交", -2);
  }
  return x;
}
export async function createSettlement(body: SettlementInput) {
  const value = await call<unknown>("settlement", {
    method: "POST",
    body: JSON.stringify({
      requestId: body.requestId,
      cartVersion: body.cartVersion,
      addressId: body.addressId,
      couponId: body.couponId,
    }),
  });
  if (
    !valid(value) ||
    value.requestId !== body.requestId ||
    value.cartVersion !== body.cartVersion ||
    value.addressId !== body.addressId ||
    value.couponId !== body.couponId
  )
    throw new ApiError("报价结果待确认，请查询原报价", 503, true);
  return value;
}
export async function getSettlement(body: SettlementInput) {
  const value = await call<unknown>(
    `settlement-result?requestId=${encodeURIComponent(body.requestId)}`,
  );
  if (value === null) return null;
  if (
    !valid(value) ||
    value.requestId !== body.requestId ||
    value.cartVersion !== body.cartVersion ||
    value.addressId !== body.addressId ||
    value.couponId !== body.couponId
  )
    throw new ApiError("原报价响应不完整", -2);
  return value;
}
