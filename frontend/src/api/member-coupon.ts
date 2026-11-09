import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";
import { memberIdentity } from "./member-profile";

export type MemberCouponRules = {
  id: number;
  name: string;
  usePrice: number;
  productScope: number;
  productScopeValues: number[];
  discountType: number;
  discountPercent: number | null;
  discountPrice: number | null;
  discountLimitPrice: number | null;
  validStartTime: string | null;
  validEndTime: string | null;
};
export type MemberCouponTemplate = MemberCouponRules & {
  description: string;
  validityType: number;
  fixedEndTerm: number | null;
  remainingCount: number;
  takeLimitCount: number;
  takenCount: number;
  canTake: boolean;
  unavailableReason: string;
};
export type MemberCoupon = MemberCouponRules & {
  templateId: number;
  status: number;
};
export type CouponClaim = { templateId: number; requestId: string };
export type ClaimResult = {
  found: boolean;
  templateId: number;
  couponId: number | null;
};
const integer = (v: unknown, min = 0): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v >= min;
const cents = (v: unknown, min = 0) => integer(v, min) && v <= 2_000_000_000;
const quota = (v: unknown) => v === -1 || cents(v, 1);
const date = (v: unknown): v is string =>
  typeof v === "string" && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(v);
const uuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export function memberCouponIdentity() {
  return memberIdentity();
}
export function memberCouponReady() {
  return (
    !demo &&
    context.realm === "member" &&
    !!identities.member &&
    identities.member.tenantCode === context.tenant
  );
}
async function call<T>(path: string, init: RequestInit = {}) {
  if (!memberCouponReady()) throw new ApiError("请先登录当前商城", 401);
  const actor = memberCouponIdentity(),
    bearer = credentials();
  if (!bearer.accessToken) throw new ApiError("请重新登录", 401);
  const result = await request<T>(
    `/app-api/promotion/${path}${path.includes("?") ? "&" : "?"}store=${encodeURIComponent(context.tenant)}`,
    { ...bearer, timeoutMs: 35000 },
    init,
  );
  if (
    actor !== memberCouponIdentity() ||
    bearer.accessToken !== credentials().accessToken
  )
    throw new ApiError(
      "登录身份或凭据已变化，请查询原领取结果",
      409,
      !!init.method,
    );
  return result;
}
function rules(v: unknown): v is MemberCouponRules {
  if (!v || typeof v !== "object") return false;
  const x = v as MemberCouponRules;
  return (
    integer(x.id, 1) &&
    typeof x.name === "string" &&
    !!x.name.trim() &&
    x.name.length <= 64 &&
    cents(x.usePrice) &&
    [1, 2, 3].includes(x.productScope) &&
    Array.isArray(x.productScopeValues) &&
    x.productScopeValues.length <= 200 &&
    x.productScopeValues.every((id) => integer(id, 1)) &&
    new Set(x.productScopeValues).size === x.productScopeValues.length &&
    (x.productScope === 1
      ? x.productScopeValues.length === 0
      : x.productScopeValues.length > 0) &&
    (x.discountType === 1
      ? cents(x.discountPrice, 1)
      : x.discountType === 2 &&
        integer(x.discountPercent, 1) &&
        x.discountPercent <= 99 &&
        cents(x.discountLimitPrice, 1))
  );
}
function template(v: unknown): v is MemberCouponTemplate {
  if (!rules(v)) return false;
  const x = v as MemberCouponTemplate;
  return (
    typeof x.description === "string" &&
    x.description.length <= 500 &&
    (x.remainingCount === -1 || cents(x.remainingCount)) &&
    quota(x.takeLimitCount) &&
    integer(x.takenCount) &&
    typeof x.canTake === "boolean" &&
    typeof x.unavailableReason === "string" &&
    (x.validityType === 1
      ? date(x.validStartTime) &&
        date(x.validEndTime) &&
        x.validStartTime < x.validEndTime
      : x.validityType === 2 &&
        integer(x.fixedEndTerm, 1) &&
        x.fixedEndTerm <= 3650)
  );
}
function owned(v: unknown): v is MemberCoupon {
  if (!rules(v)) return false;
  const x = v as MemberCoupon;
  return (
    integer(x.templateId, 1) &&
    [1, 2, 3, 4, 5].includes(x.status) &&
    date(x.validStartTime) &&
    date(x.validEndTime) &&
    x.validStartTime < x.validEndTime
  );
}
async function page<T>(path: string, check: (v: unknown) => v is T) {
  const result = await call<PageResult<unknown>>(path);
  if (
    !result ||
    !integer(result.total) ||
    !Array.isArray(result.list) ||
    result.list.length > 20 ||
    !result.list.every(check)
  )
    throw new ApiError("优惠券响应不完整，请重新读取", -2);
  return result as PageResult<T>;
}
export function memberTemplatePage(pageNo: number) {
  return page(`coupon-template/page?pageNo=${pageNo}&pageSize=12`, template);
}
export function memberCouponPage(pageNo: number, status: number | null) {
  return page(
    `coupon/page?pageNo=${pageNo}&pageSize=12${status === null ? "" : `&status=${status}`}`,
    owned,
  );
}
export async function getMemberCoupon(id: number) {
  const row = await call<unknown>(`coupon/get?id=${id}`);
  if (!owned(row) || row.id !== id)
    throw new ApiError("优惠券详情响应不完整", -2);
  return row;
}
export async function claimMemberCoupon(claim: CouponClaim, readOnly = false) {
  if (!integer(claim.templateId, 1) || !uuid.test(claim.requestId))
    throw new ApiError("领取请求不正确", 400);
  const result = await call<ClaimResult>(
    readOnly
      ? `coupon/take-result?templateId=${claim.templateId}&requestId=${claim.requestId}`
      : "coupon/take",
    readOnly ? {} : { method: "POST", body: JSON.stringify(claim) },
  );
  if (
    !result ||
    typeof result.found !== "boolean" ||
    result.templateId !== claim.templateId ||
    (result.found ? !integer(result.couponId, 1) : result.couponId !== null) ||
    (!readOnly && !result.found)
  )
    throw new ApiError("领取结果待确认，请查询原结果", 503, !readOnly);
  return result;
}
// 每个模板最多保留一个未确认请求，跨模板互不阻塞；只存请求号/模板号，不存券明细。
function pendingKey() {
  return `qinghe:coupon-claim:${memberCouponIdentity()}`;
}
export function readPendingClaims(): CouponClaim[] {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(pendingKey());
  } catch {
    throw new ApiError("浏览器无法保存领取请求，请恢复存储后再领取", -2);
  }
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw),
      rows = Array.isArray(parsed) ? parsed : [parsed];
    if (
      rows.length <= 100 &&
      rows.every(
        (x) =>
          x &&
          integer(x.templateId, 1) &&
          typeof x.requestId === "string" &&
          uuid.test(x.requestId),
      ) &&
      new Set(rows.map((x) => x.templateId)).size === rows.length &&
      new Set(rows.map((x) => x.requestId)).size === rows.length
    )
      return rows.map((x) => ({
        templateId: x.templateId,
        requestId: x.requestId,
      }));
  } catch {}
  throw new ApiError("保存的领取请求无法读取，请保留当前标签页并联系支持", -2);
}
function savePendingClaims(rows: CouponClaim[]) {
  try {
    if (rows.length) sessionStorage.setItem(pendingKey(), JSON.stringify(rows));
    else sessionStorage.removeItem(pendingKey());
  } catch {
    throw new ApiError("无法保存领取请求，请保留当前标签页", -2);
  }
}
export function createPendingClaim(templateId: number) {
  const rows = readPendingClaims();
  if (rows.some((x) => x.templateId === templateId))
    throw new ApiError("请先查询此模板上一次领取结果", 409);
  if (rows.length >= 100)
    throw new ApiError("待确认领取过多，请先查询原结果", 409);
  const row = { templateId, requestId: crypto.randomUUID() };
  savePendingClaims([...rows, row]);
  return row;
}
export function clearPendingClaim(claim: CouponClaim) {
  const rows = readPendingClaims();
  if (
    !rows.some(
      (x) =>
        x.requestId === claim.requestId && x.templateId === claim.templateId,
    )
  )
    throw new ApiError("领取请求已变化，请重新读取", 409);
  savePendingClaims(rows.filter((x) => x.requestId !== claim.requestId));
}
