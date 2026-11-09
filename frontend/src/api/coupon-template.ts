import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";

export const couponPermission = "promotion:coupon-template:";
export type CouponRules = {
  name: string;
  description: string;
  totalCount: number;
  takeLimitCount: number;
  takeType: number;
  usePrice: number;
  productScope: number;
  productScopeValues: number[];
  validityType: number;
  validStartTime: string | null;
  validEndTime: string | null;
  fixedStartTerm: number | null;
  fixedEndTerm: number | null;
  discountType: number;
  discountPercent: number | null;
  discountPrice: number | null;
  discountLimitPrice: number | null;
};
export type CouponTemplate = CouponRules & {
  id: number;
  status: number;
  takeCount: number;
  authVersion: number;
};
const integer = (x: unknown, min = 0): x is number =>
  typeof x === "number" && Number.isSafeInteger(x) && x >= min;
const amount = (x: unknown, min = 0) => integer(x, min) && x <= 2_000_000_000;
const quota = (x: unknown) => x === -1 || amount(x, 1);
export function couponIdentity() {
  const user = identities[context.realm];
  return `${context.realm}:${user?.tenantId || ""}:${user?.userId || ""}`;
}
async function call<T>(path: string, init: RequestInit = {}) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口管理优惠券", 403);
  const actor = couponIdentity();
  const result = await request<T>(
    `/admin-api/promotion/coupon-template/${path}`,
    { ...credentials(), timeoutMs: 35000 },
    init,
  );
  if (actor !== couponIdentity())
    throw new ApiError("登录身份已变化，请重新读取", 409, !!init.method);
  return result;
}
export function validCoupon(value: unknown): value is CouponTemplate {
  if (!value || typeof value !== "object") return false;
  const x = value as CouponTemplate;
  const date = (v: unknown) =>
    typeof v === "string" && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(v);
  return (
    integer(x.id, 1) &&
    integer(x.authVersion) &&
    [0, 1].includes(x.status) &&
    typeof x.name === "string" &&
    !!x.name.trim() &&
    x.name.length <= 64 &&
    typeof x.description === "string" &&
    quota(x.totalCount) &&
    quota(x.takeLimitCount) &&
    amount(x.takeCount) &&
    (x.totalCount === -1 || x.takeCount <= x.totalCount) &&
    [1, 2].includes(x.takeType) &&
    amount(x.usePrice) &&
    [1, 2, 3].includes(x.productScope) &&
    Array.isArray(x.productScopeValues) &&
    x.productScopeValues.length <= 200 &&
    x.productScopeValues.every((id) => integer(id, 1)) &&
    new Set(x.productScopeValues).size === x.productScopeValues.length &&
    (x.productScope === 1
      ? x.productScopeValues.length === 0
      : x.productScopeValues.length > 0) &&
    (x.validityType === 1
      ? date(x.validStartTime) &&
        date(x.validEndTime) &&
        x.validStartTime! < x.validEndTime!
      : x.validityType === 2 &&
        x.fixedStartTerm === 0 &&
        integer(x.fixedEndTerm, 1) &&
        x.fixedEndTerm <= 3650) &&
    (x.discountType === 1
      ? amount(x.discountPrice, 1)
      : x.discountType === 2 &&
        integer(x.discountPercent, 1) &&
        x.discountPercent <= 99 &&
        amount(x.discountLimitPrice, 1))
  );
}
export async function couponPage(pageNo: number, name: string, status: string) {
  const query = new URLSearchParams({
    pageNo: String(pageNo),
    pageSize: "20",
    name,
  });
  if (status !== "") query.set("status", status);
  const result = await call<PageResult<CouponTemplate>>(`page?${query}`);
  if (
    !result ||
    !integer(result.total) ||
    !Array.isArray(result.list) ||
    !result.list.every(validCoupon)
  )
    throw new ApiError("优惠券模板列表不完整", -2);
  return result;
}
export async function couponGet(id: number) {
  if (!integer(id, 1)) throw new ApiError("模板编号不正确", 400);
  const result = await call<unknown>(`get?id=${id}`);
  if (!validCoupon(result) || result.id !== id)
    throw new ApiError("优惠券模板详情不完整", -2);
  return result;
}
export async function couponSave(body: CouponRules, current?: CouponTemplate) {
  const result = await call<unknown>(current ? "update" : "create", {
    method: current ? "PUT" : "POST",
    body: JSON.stringify(
      current
        ? { ...body, id: current.id, expectedVersion: current.authVersion }
        : body,
    ),
  });
  if (current ? result !== true : !integer(result, 1))
    throw new ApiError("模板保存结果待确认，请核对列表", 503, true);
}
export async function couponStatus(current: CouponTemplate, status: number) {
  const result = await call<unknown>("update-status", {
    method: "PUT",
    body: JSON.stringify({
      id: current.id,
      expectedVersion: current.authVersion,
      status,
    }),
  });
  if (result !== true)
    throw new ApiError("启停结果待确认，请核对详情", 503, true);
}
export async function couponDelete(current: CouponTemplate) {
  const result = await call<unknown>(
    `delete?id=${current.id}&expectedVersion=${current.authVersion}`,
    { method: "DELETE" },
  );
  if (result !== true)
    throw new ApiError("删除结果待确认，请核对列表", 503, true);
}
