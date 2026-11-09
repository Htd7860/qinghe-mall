import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";
import { memberIdentity } from "./member-profile";
export type CartProduct = {
  tenantId: number;
  skuId: number;
  spuId: number;
  categoryId: number;
  deliveryTemplateId: number;
  spuVersion: number;
  skuVersion: number;
  name: string;
  picUrl: string;
  price: number;
  stock: number;
  weight: string | null;
  properties: {
    propertyId: number;
    propertyName: string;
    valueId: number;
    valueName: string;
  }[];
};
export type CartLine = {
  id: number;
  spuId: number;
  skuId: number;
  count: number;
  selected: boolean;
  authVersion: number;
  valid: boolean;
  invalidReason: string;
  product: CartProduct | null;
};
export type MemberCart = {
  version: number;
  canPurchase: boolean;
  maxCartLines: number;
  maxItemQuantity: number;
  maxOrderLines: number;
  lines: CartLine[];
};
const integer = (v: unknown, min = 0): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v >= min;
export function cartIdentity() {
  return memberIdentity();
}
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
  const data = await request<T>(
    `/app-api/trade/cart/${path}${path.includes("?") ? "&" : "?"}store=${encodeURIComponent(context.tenant)}`,
    { ...bearer, timeoutMs: 35000 },
    init,
  );
  if (
    actor !== cartIdentity() ||
    bearer.accessToken !== credentials().accessToken
  )
    throw new ApiError("登录凭据已变化，请重新读取购物车", 409, !!init.method);
  return data;
}
function product(v: unknown): v is CartProduct {
  if (!v || typeof v !== "object") return false;
  const x = v as CartProduct;
  return (
    integer(x.tenantId, 1) &&
    String(x.tenantId) === identities.member?.tenantId &&
    integer(x.skuId, 1) &&
    integer(x.spuId, 1) &&
    integer(x.categoryId, 1) &&
    integer(x.deliveryTemplateId, 1) &&
    integer(x.spuVersion) &&
    integer(x.skuVersion) &&
    typeof x.name === "string" &&
    !!x.name.trim() &&
    x.name.length <= 500 &&
    typeof x.picUrl === "string" &&
    integer(x.price, 1) &&
    x.price <= 2_000_000_000 &&
    integer(x.stock) &&
    x.stock <= 2_147_483_647 &&
    (x.weight === null ||
      (typeof x.weight === "string" &&
        /^\d{1,12}(?:\.\d{1,6})?$/.test(x.weight))) &&
    Array.isArray(x.properties) &&
    x.properties.length <= 20 &&
    x.properties.every(
      (p) =>
        p &&
        ((integer(p.propertyId, 1) && integer(p.valueId, 1)) ||
          (x.properties.length === 1 &&
            p.propertyId === 0 &&
            p.valueId === 0 &&
            p.propertyName === "默认" &&
            p.valueName === "默认")) &&
        typeof p.propertyName === "string" &&
        typeof p.valueName === "string" &&
        p.propertyName.length <= 100 &&
        p.valueName.length <= 100,
    )
  );
}
function line(v: unknown): v is CartLine {
  if (!v || typeof v !== "object") return false;
  const x = v as CartLine;
  return (
    integer(x.id, 1) &&
    integer(x.spuId, 1) &&
    integer(x.skuId, 1) &&
    integer(x.count, 1) &&
    x.count <= 9999 &&
    integer(x.authVersion) &&
    typeof x.selected === "boolean" &&
    typeof x.valid === "boolean" &&
    typeof x.invalidReason === "string" &&
    (x.product === null
      ? !x.valid
      : product(x.product) &&
        x.product.skuId === x.skuId &&
        x.product.spuId === x.spuId &&
        (!x.valid || x.product.stock >= x.count))
  );
}
export async function readMemberCart() {
  const x = await call<MemberCart>("list");
  if (
    !x ||
    !integer(x.version) ||
    typeof x.canPurchase !== "boolean" ||
    !integer(x.maxCartLines, 1) ||
    x.maxCartLines > 200 ||
    !integer(x.maxOrderLines, 1) ||
    x.maxOrderLines > 200 ||
    !integer(x.maxItemQuantity, 1) ||
    x.maxItemQuantity > 9999 ||
    !Array.isArray(x.lines) ||
    x.lines.length > 200 ||
    !x.lines.every(line) ||
    new Set(x.lines.map((v) => v.id)).size !== x.lines.length ||
    new Set(x.lines.map((v) => v.skuId)).size !== x.lines.length
  )
    throw new ApiError("购物车响应不完整，请重新读取", -2);
  return x;
}
export async function cartProductOptions(
  keyword: string,
  pageNo: number,
  spuId?: number,
) {
  const data = await call<PageResult<CartProduct>>(
    `product-options?keyword=${encodeURIComponent(keyword)}&pageNo=${pageNo}${spuId ? `&spuId=${spuId}` : ""}`,
  );
  if (
    !data ||
    !integer(data.total) ||
    !Array.isArray(data.list) ||
    data.list.length > 20 ||
    !data.list.every(product) ||
    new Set(data.list.map((x) => x.skuId)).size !== data.list.length ||
    (spuId && data.list.some((x) => x.spuId !== spuId))
  )
    throw new ApiError("商品选择响应不完整", -2);
  return data;
}
export async function addMemberCart(
  skuId: number,
  count: number,
  expectedVersion: number,
) {
  const result = await call<unknown>("add", {
    method: "POST",
    body: JSON.stringify({ skuId, count, expectedVersion }),
  });
  if (!integer(result, 1))
    throw new ApiError("添加结果待确认，请重新读取购物车", 503, true);
  return result;
}
export async function setCartCount(
  id: number,
  count: number,
  expectedVersion: number,
) {
  const result = await call<unknown>("update-count", {
    method: "PUT",
    body: JSON.stringify({ id, count, expectedVersion }),
  });
  if (result !== true)
    throw new ApiError("数量修改结果待确认，请重新读取", 503, true);
}
export async function setCartSelected(
  ids: number[],
  selected: boolean,
  expectedVersion: number,
) {
  const result = await call<unknown>("update-selected", {
    method: "PUT",
    body: JSON.stringify({ ids, selected, expectedVersion }),
  });
  if (result !== true)
    throw new ApiError("勾选结果待确认，请重新读取", 503, true);
}
export async function resetMemberCart(
  id: number,
  skuId: number,
  count: number,
  expectedVersion: number,
) {
  const result = await call<unknown>("reset", {
    method: "PUT",
    body: JSON.stringify({ id, skuId, count, expectedVersion }),
  });
  if (!integer(result, 1))
    throw new ApiError("规格修改结果待确认，请重新读取", 503, true);
  return result;
}
export async function deleteMemberCart(ids: number[], expectedVersion: number) {
  const result = await call<unknown>(
    `delete?ids=${ids.join(",")}&expectedVersion=${expectedVersion}`,
    { method: "DELETE" },
  );
  if (result !== true)
    throw new ApiError("移除结果待确认，请重新读取", 503, true);
}

// Shared current-member product response guard for checkout line projections.
export const isCartProduct = product;
