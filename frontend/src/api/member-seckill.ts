import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";
import { memberIdentity } from "./member-profile";
import type { SkuProperty } from "./product";

export type SeckillActivity = {
  id: number;
  name: string;
  spuId: number;
  productName: string;
  picUrl: string;
  generation: number;
  startTime: string;
  endTime: string;
  singleLimit: number;
  memberLimit: number;
  status: number;
  minPriceCent: number;
  quoteAvailable: boolean;
  serverTime: number;
  skus:
    | {
        skuId: number;
        properties: SkuProperty[];
        priceCent: number;
        availableQuantity: number;
      }[]
    | null;
};
export type SeckillQuoteInput = {
  requestId: string;
  activityId: number;
  generation: number;
  skuId: number;
  quantity: number;
  addressId: number;
};
export type SeckillQuote = {
  quoteId: string;
  requestId: string;
  quoteDigest: string;
  activityId: number;
  generation: number;
  activityVersion: number;
  redisEpoch: number;
  skuId: number;
  quantity: number;
  priceCent: number;
  goodsAmount: number;
  shippingAmount: number;
  payAmount: number;
  createdAt: string;
  expiresAt: string;
  expired: boolean;
  snapshotPresent: boolean;
  bound: boolean;
  serverTime: number;
};
export type SeckillReceipt = {
  found: boolean;
  requestNo: string | null;
  clientRequestId: string | null;
  activityId: number | null;
  generation: number | null;
  skuId: number | null;
  quantity: number | null;
  goodsAmount: number | null;
  shippingAmount: number | null;
  payAmount: number | null;
  status: number | null;
  orderId: number | null;
  acceptedAt: string | null;
  reconcileDeadline: string | null;
  payDeadline: string | null;
  version: number | null;
  serverTime: number;
};
export type SeckillAttempt = {
  input: SeckillQuoteInput;
  quote: SeckillQuote | null;
  clientId: string | null;
};
export type Admission = {
  requestNo: string;
  clientRequestId: string;
  decision: number;
  failureCode: number | null;
  redisSynchronized: boolean;
  quoteClosed: boolean | null;
  request: SeckillReceipt | null;
};
const integer = (
  v: unknown,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v >= min && v <= max;
const uuid = (v: unknown): v is string =>
  typeof v === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
    v,
  );
const text = (v: unknown, max: number): v is string =>
  typeof v === "string" &&
  !!v.trim() &&
  v.length <= max &&
  !/[\x00-\x1f\x7f]/.test(v);
const time = (v: unknown): v is string =>
  typeof v === "string" &&
  /(?:Z|[+-]\d{2}:\d{2})$/.test(v) &&
  Number.isFinite(Date.parse(v));
function requireMember() {
  if (
    demo ||
    context.realm !== "member" ||
    !identities.member ||
    identities.member.tenantCode !== context.tenant
  )
    throw new ApiError("请先登录当前商城", 401);
}
async function call(path: string, init: RequestInit = {}) {
  requireMember();
  const actor = memberIdentity(),
    auth = credentials();
  if (!auth.accessToken) throw new ApiError("请重新登录", 401);
  const result = await request<unknown>(
    `${path}${path.includes("?") ? "&" : "?"}store=${encodeURIComponent(context.tenant)}`,
    { ...auth, timeoutMs: 35000 },
    init,
  );
  if (
    actor !== memberIdentity() ||
    auth.accessToken !== credentials().accessToken
  )
    throw new ApiError("身份已变化，请查询原申请", 409, init.method === "POST");
  return result;
}
/** Only sales browsing is public. Quote/admission/history continue to use call(). */
async function browse(path: string) {
  if (demo || context.realm !== "member")
    throw new ApiError("请从商城入口浏览", 403);
  if (identities.member && identities.member.tenantCode !== context.tenant)
    throw new ApiError("当前登录身份不属于此店铺", 403);
  const actor = memberIdentity(),
    token = credentials().accessToken;
  const auth = identities.member ? credentials() : {};
  if (identities.member && !token) throw new ApiError("请重新登录", 401);
  const value = await request<unknown>(
    `${path}&store=${encodeURIComponent(context.tenant)}`,
    auth,
  );
  if (actor !== memberIdentity() || token !== credentials().accessToken)
    throw new ApiError("店铺或登录身份已变化，请重新读取", 409);
  return value;
}
function validActivity(value: unknown): value is SeckillActivity {
  if (!value || typeof value !== "object") return false;
  const x = value as SeckillActivity;
  return (
    integer(x.id, 1) &&
    text(x.name, 60) &&
    integer(x.spuId, 1) &&
    text(x.productName, 128) &&
    typeof x.picUrl === "string" &&
    x.picUrl.length <= 2048 &&
    integer(x.generation, 1, 2147483647) &&
    time(x.startTime) &&
    time(x.endTime) &&
    Date.parse(x.startTime) < Date.parse(x.endTime) &&
    integer(x.singleLimit, 1, 9999) &&
    integer(x.memberLimit, x.singleLimit, 2147483647) &&
    [20, 30, 40, 50].includes(x.status) &&
    integer(x.minPriceCent, 1, 2000000000) &&
    typeof x.quoteAvailable === "boolean" &&
    integer(x.serverTime, 1, 8640000000000000) &&
    (x.skus === null ||
      (Array.isArray(x.skus) &&
        x.skus.length > 0 &&
        x.skus.length <= 200 &&
        new Set(x.skus.map((s) => s?.skuId)).size === x.skus.length &&
        x.skus.every(
          (s) =>
            !!s &&
            integer(s.skuId, 1) &&
            integer(s.priceCent, 1, 2000000000) &&
            integer(s.availableQuantity, 0, 2147483647) &&
            Array.isArray(s.properties) &&
            s.properties.length <= 100 &&
            s.properties.every(
              (p) =>
                !!p &&
                integer(p.propertyId, 1) &&
                integer(p.valueId, 1) &&
                text(p.propertyName, 256) &&
                text(p.valueName, 256),
            ),
        )))
  );
}
export async function memberSeckillPage(n: number) {
  if (!integer(n, 1, 10000)) throw new ApiError("页码不正确", 400);
  const result = await browse(
    `/app-api/promotion/seckill-activity/page?pageNo=${n}&pageSize=20`,
  );
  const x = result as PageResult<SeckillActivity>;
  if (
    !x ||
    !integer(x.total, 0, 2147483647) ||
    !Array.isArray(x.list) ||
    x.list.length > 20 ||
    x.list.length > x.total ||
    !x.list.every(validActivity) ||
    new Set(x.list.map((r) => r.id)).size !== x.list.length
  )
    throw new ApiError("活动列表不完整，请刷新", -2);
  return x;
}
export async function memberSeckillGet(id: number) {
  if (!integer(id, 1)) throw new ApiError("活动编号不正确", 400);
  const value = await browse(
    `/app-api/promotion/seckill-activity/get?id=${id}`,
  );
  if (!validActivity(value) || value.id !== id || !value.skus)
    throw new ApiError("活动详情不完整，请刷新", -2);
  return value;
}
function validInput(x: unknown): x is SeckillQuoteInput {
  if (!x || typeof x !== "object") return false;
  const v = x as SeckillQuoteInput;
  return (
    uuid(v.requestId) &&
    integer(v.activityId, 1) &&
    integer(v.generation, 1, 2147483647) &&
    integer(v.skuId, 1) &&
    integer(v.quantity, 1, 9999) &&
    integer(v.addressId, 1)
  );
}
function validQuote(x: unknown, input: SeckillQuoteInput): x is SeckillQuote {
  if (!x || typeof x !== "object") return false;
  const v = x as SeckillQuote;
  return (
    uuid(v.quoteId) &&
    v.requestId === input.requestId &&
    /^[0-9a-f]{64}$/.test(v.quoteDigest) &&
    v.activityId === input.activityId &&
    v.generation === input.generation &&
    v.skuId === input.skuId &&
    v.quantity === input.quantity &&
    integer(v.activityVersion) &&
    v.redisEpoch === 1 &&
    integer(v.priceCent, 1, 2000000000) &&
    integer(v.goodsAmount, 1) &&
    v.goodsAmount === v.priceCent * v.quantity &&
    integer(v.shippingAmount) &&
    integer(v.payAmount, 1) &&
    v.payAmount === v.goodsAmount + v.shippingAmount &&
    time(v.createdAt) &&
    time(v.expiresAt) &&
    Date.parse(v.createdAt) < Date.parse(v.expiresAt) &&
    Date.parse(v.expiresAt) - Date.parse(v.createdAt) <= 120000 &&
    [v.expired, v.snapshotPresent, v.bound].every(
      (b) => typeof b === "boolean",
    ) &&
    integer(v.serverTime, 1, 8640000000000000)
  );
}
function validReceipt(value: unknown): value is SeckillReceipt {
  if (!value || typeof value !== "object") return false;
  const r = value as SeckillReceipt;
  if (
    typeof r.found !== "boolean" ||
    !integer(r.serverTime, 1, 8640000000000000)
  )
    return false;
  if (!r.found)
    return [
      r.requestNo,
      r.clientRequestId,
      r.activityId,
      r.generation,
      r.skuId,
      r.quantity,
      r.goodsAmount,
      r.shippingAmount,
      r.payAmount,
      r.status,
      r.orderId,
      r.acceptedAt,
      r.reconcileDeadline,
      r.payDeadline,
      r.version,
    ].every((v) => v === null);
  return (
    uuid(r.requestNo) &&
    uuid(r.clientRequestId) &&
    integer(r.activityId, 1) &&
    integer(r.generation, 1, 2147483647) &&
    integer(r.skuId, 1) &&
    integer(r.quantity, 1, 9999) &&
    integer(r.goodsAmount, 1) &&
    integer(r.shippingAmount) &&
    integer(r.payAmount, 1) &&
    r.payAmount === r.goodsAmount + r.shippingAmount &&
    [10, 20, 30, 40, 50, 60].includes(r.status!) &&
    (r.orderId === null || integer(r.orderId, 1)) &&
    (r.status !== 20 || r.orderId !== null) &&
    time(r.acceptedAt) &&
    time(r.reconcileDeadline) &&
    time(r.payDeadline) &&
    Date.parse(r.reconcileDeadline) - Date.parse(r.acceptedAt) === 120000 &&
    Date.parse(r.payDeadline) - Date.parse(r.acceptedAt) === 1800000 &&
    integer(r.version)
  );
}
function key() {
  requireMember();
  return `qinghe:seckill-member-attempt:v1:${memberIdentity()}`;
}
function validAttempt(x: unknown): x is SeckillAttempt {
  if (!x || typeof x !== "object") return false;
  const v = x as SeckillAttempt;
  return (
    validInput(v.input) &&
    (v.quote === null || validQuote(v.quote, v.input)) &&
    (v.clientId === null || (uuid(v.clientId) && v.quote !== null))
  );
}
export function readSeckillAttempt(): SeckillAttempt | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(key());
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError("无法读取原购买记录，请先核对", -2);
  }
  if (raw === null) return null;
  let value: unknown;
  try {
    value = raw.length <= 6000 ? JSON.parse(raw) : null;
  } catch {
    value = null;
  }
  if (!validAttempt(value))
    throw new ApiError("原购买记录不完整，请保留并核查", -2);
  return value;
}
function save(value: SeckillAttempt) {
  if (!validAttempt(value)) throw new ApiError("原购买记录不完整", 400);
  try {
    sessionStorage.setItem(key(), JSON.stringify(value));
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError("无法保存原请求，请保留记录并核对原结果", -2);
  }
}
export function beginSeckillQuote(input: Omit<SeckillQuoteInput, "requestId">) {
  if (readSeckillAttempt()) throw new ApiError("请先核对上一笔购买", 409);
  if (!globalThis.crypto?.randomUUID)
    throw new ApiError("当前浏览器无法创建原请求号", -2);
  const value: SeckillAttempt = {
    input: { ...input, requestId: crypto.randomUUID() },
    quote: null,
    clientId: null,
  };
  save(value);
  return value;
}
function current(value: SeckillAttempt) {
  const old = readSeckillAttempt();
  if (!old || JSON.stringify(old) !== JSON.stringify(value))
    throw new ApiError("原购买记录已变化，请重新读取", 409);
  return old;
}
export async function fetchSeckillQuote(value: SeckillAttempt, retry = false) {
  current(value);
  const actor = memberIdentity();
  const result = await call(
    retry
      ? "/app-api/trade/seckill/quote"
      : "/app-api/trade/seckill/quote-result?requestId=" +
          value.input.requestId,
    retry ? { method: "POST", body: JSON.stringify(value.input) } : {},
  );
  if (result === null && !retry) return null;
  if (!validQuote(result, value.input))
    throw new ApiError("原报价结果不完整，请查询原请求", -2, retry);
  if (actor !== memberIdentity()) throw new ApiError("身份已变化", 409, retry);
  current(value);
  const updated = { ...value, quote: result };
  save(updated);
  return updated;
}
export function beginSeckillAdmission(value: SeckillAttempt) {
  current(value);
  if (!value.quote) throw new ApiError("请先确认原报价", 400);
  if (value.clientId) return value;
  if (value.quote.expired || value.quote.bound || !value.quote.snapshotPresent)
    throw new ApiError("原报价已过期或不可用，请核对", 409);
  const next = { ...value, clientId: crypto.randomUUID() };
  save(next);
  return next;
}
export async function admitSeckill(value: SeckillAttempt) {
  current(value);
  if (!value.quote || !value.clientId)
    throw new ApiError("原受理记录不完整", 400);
  const result = await call("/app-api/promotion/seckill/admit", {
    method: "POST",
    body: JSON.stringify({
      clientRequestId: value.clientId,
      quoteId: value.quote.quoteId,
      quoteDigest: value.quote.quoteDigest,
    }),
  });
  const a = result as Admission;
  if (
    !a ||
    !uuid(a.requestNo) ||
    a.clientRequestId !== value.clientId ||
    ![10, 20].includes(a.decision) ||
    typeof a.redisSynchronized !== "boolean" ||
    (a.decision === 10
      ? a.failureCode !== null ||
        a.quoteClosed !== null ||
        !validReceipt(a.request) ||
        !a.request.found ||
        a.request.clientRequestId !== value.clientId ||
        a.request.requestNo !== a.requestNo ||
        !matches(a.request, value)
      : ![400, 409, 429, 503].includes(a.failureCode!) ||
        typeof a.quoteClosed !== "boolean" ||
        a.request !== null)
  )
    throw new ApiError("原受理结果待核对，请保留原申请号", -2, true);
  return a;
}
function matches(r: SeckillReceipt, v: SeckillAttempt) {
  return (
    r.activityId === v.input.activityId &&
    r.generation === v.input.generation &&
    r.skuId === v.input.skuId &&
    r.quantity === v.input.quantity &&
    (!v.quote ||
      (r.goodsAmount === v.quote.goodsAmount &&
        r.shippingAmount === v.quote.shippingAmount &&
        r.payAmount === v.quote.payAmount))
  );
}
export async function readSeckillReceipt(value: SeckillAttempt) {
  current(value);
  if (!value.clientId) throw new ApiError("原申请号尚未生成", 400);
  const r = await call(
    "/app-api/promotion/seckill/request-result?clientRequestId=" +
      value.clientId,
  );
  if (
    !validReceipt(r) ||
    (r.found && (r.clientRequestId !== value.clientId || !matches(r, value)))
  )
    throw new ApiError("原申请结果不完整", -2);
  return r;
}
export async function memberSeckillHistory(n: number) {
  if (!integer(n, 1, 10000)) throw new ApiError("页码不正确", 400);
  const value = await call(
    `/app-api/promotion/seckill/request-page?pageNo=${n}&pageSize=20`,
  );
  const p = value as PageResult<SeckillReceipt>;
  if (
    !p ||
    !integer(p.total, 0, 2147483647) ||
    !Array.isArray(p.list) ||
    p.list.length > 20 ||
    p.list.length > p.total ||
    !p.list.every((x) => validReceipt(x) && x.found) ||
    new Set(p.list.map((x) => x.requestNo)).size !== p.list.length
  )
    throw new ApiError("本人申请列表不完整，请重新读取", -2);
  return p;
}
export async function memberSeckillRequest(requestNo: string) {
  if (!uuid(requestNo)) throw new ApiError("申请编号不正确", 400);
  const r = await call(
    `/app-api/promotion/seckill/request-detail?requestNo=${requestNo}`,
  );
  if (!validReceipt(r) || (r.found && r.requestNo !== requestNo))
    throw new ApiError("本人申请结果不完整", -2);
  return r;
}
export function clearSeckillAttempt(value: SeckillAttempt) {
  current(value);
  try {
    sessionStorage.removeItem(key());
  } catch {
    throw new ApiError("无法清理原购买记录，请重查", -2);
  }
}
