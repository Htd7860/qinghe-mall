import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";
import type { SkuProperty } from "./product";

// Selected yudao seckillActivity page/get/create/update duties; MIT attribution in third-party NOTICE.
export const seckillPermission = "promotion:seckill-activity:";
export const seckillStates: Record<number, string> = {
  0: "草稿",
  10: "发布准备中",
  20: "已发布",
  30: "停止接单",
  40: "结束清算中",
  50: "已结清",
  60: "发布失败 / 撤回处理中",
};
type ActivityBase = {
  id: number;
  activityNo: string;
  name: string;
  productSpuId: number;
  productName: string;
  picUrl: string;
  startTime: string;
  endTime: string;
  singleLimit: number;
  memberLimit: number;
  status: number;
  version: number;
  generation: number;
  serverTime: number;
};
type Pool = {
  plannedQuantity: number;
  allocatedQuantity: number;
  availableQuantity: number;
  heldQuantity: number;
  soldQuantity: number;
  returnedQuantity: number;
};
export type ActivityRow = ActivityBase &
  Pool & { skuCount: number; returnPendingQuantity: number };
export type ActivitySku = Pool & {
  skuId: number;
  skuVersion: number;
  properties: SkuProperty[];
  priceCent: number;
};
export type Activity = ActivityBase & {
  productSpuVersion: number;
  skus: ActivitySku[];
};
export type DraftInput = {
  id: number | null;
  expectedVersion: number | null;
  name: string;
  productSpuId: number;
  startTime: string;
  endTime: string;
  singleLimit: number;
  memberLimit: number;
  skus: { skuId: number; priceCent: number; plannedQuantity: number }[];
};
export type PublishIntent = {
  id: number;
  expectedVersion: number;
  requestId: string;
};
export const publishActions = {
  prepare: { path: "prepare-publish", label: "准备发布" },
  advance: { path: "advance-publish-sku", label: "划拨本规格" },
  cancel: { path: "cancel-publish", label: "停止本次准备" },
  compensate: { path: "compensate-publish-sku", label: "归还并关闭本规格" },
  confirm: { path: "confirm-publish", label: "确认发布" },
  withdraw: { path: "withdraw-publish", label: "撤回未开始活动" },
  settle: { path: "settle-publish", label: "确认结清" },
  reopen: { path: "reopen-draft", label: "恢复草稿" },
} as const;
export type PublishAction = keyof typeof publishActions;
export type PublishPending = PublishIntent & {
  action: PublishAction;
  skuId: number | null;
  reasonDigest: string | null;
};
export type PublishHistory = {
  requestId: string;
  actorId: number;
  fromVersion: number;
  preparedVersion: number;
  generation: number;
  preparedAt: string;
  published: boolean;
  withdrawn: boolean;
  settled: boolean;
};
export type PublishResult = {
  id: number;
  requestId: string;
  generation: number;
  fromVersion: number;
  preparedVersion: number;
  currentGeneration: boolean;
  status: number | null;
  version: number | null;
  published: boolean;
  withdrawn: boolean;
  settled: boolean;
  reopenedVersion: number | null;
  serverTime: number;
  skus: {
    skuId: number;
    quantity: number;
    productStatus: number | null;
    productVersion: number | null;
    allocatedQuantity: number | null;
    returnedQuantity: number | null;
  }[];
};
export type ClearingProgress = {
  id: number;
  generation: number;
  currentGeneration: number;
  currentStatus: number;
  basis: "CURRENT_POOL" | "SEALED_BASELINE";
  runtimeClosed: boolean;
  baselineSaved: boolean;
  clearingCompleted: boolean;
  draftReopened: boolean;
  paidQuotaConsistent: boolean;
  pending: Record<
    | "admission"
    | "requests"
    | "outbox"
    | "resultCache"
    | "refunds"
    | "poolReturns"
    | "memberHeld",
    boolean
  >;
  skus: {
    skuId: number;
    allocatedQuantity: number;
    availableQuantity: number;
    heldQuantity: number;
    soldQuantity: number;
    returnedQuantity: number;
    returnPendingQuantity: number;
    historicalReturnedQuantity: number;
    receiptConsistent: boolean;
  }[];
  serverTime: number;
};
export type DraftPending = {
  id: number | null;
  expectedVersion: number | null;
  productSpuId: number;
};
const integer = (
  x: unknown,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
): x is number =>
  typeof x === "number" && Number.isSafeInteger(x) && x >= min && x <= max;
const text = (x: unknown, max: number, min = 0): x is string =>
  typeof x === "string" &&
  x.length >= min &&
  x.length <= max &&
  !/[\x00-\x1f\x7f]/.test(x);
const uuid = (x: unknown): x is string =>
  typeof x === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
    x,
  );
const offsetTime = (x: unknown): x is string =>
  typeof x === "string" &&
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/.test(
    x,
  ) &&
  Number.isFinite(Date.parse(x));
const state = (x: unknown): x is number =>
  integer(x) && Object.hasOwn(seckillStates, x);
export function seckillIdentity() {
  const user = identities[context.realm];
  return `${context.realm}:${user?.tenantId ?? ""}:${user?.userId ?? ""}`;
}
async function call(path: string, init: RequestInit = {}) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口管理秒杀活动", 403);
  const actor = seckillIdentity(),
    bearer = credentials();
  if (!bearer.accessToken) throw new ApiError("请重新登录", 401);
  const result = await request<unknown>(
    `/admin-api/promotion/seckill-activity/${path}`,
    { ...bearer, timeoutMs: 30000 },
    init,
  );
  if (
    actor !== seckillIdentity() ||
    bearer.accessToken !== credentials().accessToken
  )
    throw new ApiError("身份已变化，请重新读取原活动", 409, !!init.method);
  return result;
}
export type RuntimeReceipt = {
  id: number;
  requestId: string;
  generation: number;
  epoch: number;
  stage: number;
  initDeadline: string | null;
  cacheCloseState: "NOT_REQUIRED" | "PENDING" | "SYNCED";
  serverTime: number;
};
export const runtimeActions = {
  seed: { path: "prepare-runtime", label: "准备运行定义" },
  initialize: { path: "initialize-runtime", label: "初始化运行库存" },
  ready: { path: "ready-runtime", label: "确认运行就绪" },
  stop: { path: "stop-runtime", label: "停止新接单" },
} as const;
export type RuntimeAction = keyof typeof runtimeActions;
export type RuntimePending = {
  intent: PublishIntent;
  action: RuntimeAction;
  reason: string;
};
function validRuntimePending(value: unknown): value is RuntimePending {
  if (!value || typeof value !== "object") return false;
  const x = value as RuntimePending;
  return (
    !!x.intent &&
    integer(x.intent.id, 1) &&
    integer(x.intent.expectedVersion, 0, Number.MAX_SAFE_INTEGER - 1) &&
    uuid(x.intent.requestId) &&
    typeof x.action === "string" &&
    Object.hasOwn(runtimeActions, x.action) &&
    (x.action === "stop"
      ? text(x.reason, 500, 1) && x.reason === x.reason.trim()
      : x.reason === "")
  );
}
function runtimeKey() {
  return `qinghe:seckill-runtime-pending:v1:${seckillIdentity()}`;
}
export function readRuntimePending(): RuntimePending | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(runtimeKey());
  } catch {
    throw new ApiError("无法读取原运行操作，请先核对", -2);
  }
  if (raw === null) return null;
  let value: unknown;
  try {
    value = raw.length <= 4000 ? JSON.parse(raw) : null;
  } catch {
    value = null;
  }
  if (!validRuntimePending(value))
    throw new ApiError("原运行记录不完整，请保留并核查", -2);
  return value;
}
export function rememberRuntimePending(value: RuntimePending) {
  if (!validRuntimePending(value))
    throw new ApiError("运行操作或原因不正确", 400);
  const old = readRuntimePending();
  if (old && JSON.stringify(old) !== JSON.stringify(value))
    throw new ApiError("请先核对上一条运行操作", 409);
  try {
    sessionStorage.setItem(runtimeKey(), JSON.stringify(value));
  } catch {
    throw new ApiError("无法保存原运行操作，本次尚未提交", -2);
  }
}
export function forgetRuntimePending(value: RuntimePending) {
  const old = readRuntimePending();
  if (old && JSON.stringify(old) !== JSON.stringify(value))
    throw new ApiError("原运行记录已变化", 409);
  try {
    sessionStorage.removeItem(runtimeKey());
  } catch {
    throw new ApiError("无法清理原运行记录，请再次核对", -2);
  }
}
function runtimeReceipt(
  value: unknown,
  intent: PublishIntent,
): value is RuntimeReceipt {
  if (!value || typeof value !== "object") return false;
  const x = value as RuntimeReceipt;
  return (
    x.id === intent.id &&
    x.requestId === intent.requestId &&
    integer(x.generation, 1, 2147483647) &&
    x.epoch === 1 &&
    [0, 10, 15, 20, 30, 40].includes(x.stage) &&
    integer(x.serverTime, 1, 8640000000000000) &&
    ([15, 20, 30].includes(x.stage)
      ? offsetTime(x.initDeadline)
      : x.initDeadline === null) &&
    (x.stage === 30
      ? ["PENDING", "SYNCED"].includes(x.cacheCloseState)
      : x.cacheCloseState === "NOT_REQUIRED")
  );
}
export async function seckillRuntimeResult(intent: PublishIntent) {
  if (
    !integer(intent.id, 1) ||
    !integer(intent.expectedVersion, 0, Number.MAX_SAFE_INTEGER - 1) ||
    !uuid(intent.requestId)
  )
    throw new ApiError("请选择完整原发布记录", 400);
  const value = await call(
    `runtime-result?${new URLSearchParams({ id: String(intent.id), expectedVersion: String(intent.expectedVersion), requestId: intent.requestId })}`,
  );
  if (value === null) return null;
  if (!runtimeReceipt(value, intent))
    throw new ApiError("原运行结果不完整，请重新查询", -2);
  return value;
}
export async function seckillRuntimeCommand(value: RuntimePending) {
  if (!validRuntimePending(value)) throw new ApiError("原运行操作不完整", 400);
  const result = await call(runtimeActions[value.action].path, {
    method: "POST",
    body: JSON.stringify({
      ...value.intent,
      ...(value.action === "stop" ? { reason: value.reason } : {}),
    }),
  });
  if (!runtimeReceipt(result, value.intent))
    throw new ApiError("运行结果待确认，请查询原发布记录", -2, true);
  return result;
}
function base(value: unknown): value is ActivityBase {
  if (!value || typeof value !== "object") return false;
  const x = value as ActivityBase;
  return (
    integer(x.id, 1) &&
    uuid(x.activityNo) &&
    text(x.name, 60, 1) &&
    integer(x.productSpuId, 1) &&
    text(x.productName, 256, 1) &&
    text(x.picUrl, 2048) &&
    offsetTime(x.startTime) &&
    offsetTime(x.endTime) &&
    Date.parse(x.startTime) < Date.parse(x.endTime) &&
    integer(x.singleLimit, 1, 9999) &&
    integer(x.memberLimit, x.singleLimit, 2147483647) &&
    state(x.status) &&
    integer(x.version) &&
    integer(x.generation, 0, 2147483647) &&
    integer(x.serverTime, 1, 8640000000000000)
  );
}
function pool(x: Pool) {
  return (
    [
      x.plannedQuantity,
      x.allocatedQuantity,
      x.availableQuantity,
      x.heldQuantity,
      x.soldQuantity,
      x.returnedQuantity,
    ].every((n) => integer(n, 0, 2147483647)) &&
    x.plannedQuantity > 0 &&
    x.allocatedQuantity ===
      x.availableQuantity + x.heldQuantity + x.soldQuantity + x.returnedQuantity
  );
}
function row(value: unknown): value is ActivityRow {
  const x = value as ActivityRow;
  return (
    base(value) &&
    pool(x) &&
    integer(x.skuCount, 1, 200) &&
    integer(x.returnPendingQuantity, 0, x.availableQuantity)
  );
}
function property(x: SkuProperty) {
  return (
    x &&
    integer(x.propertyId) &&
    integer(x.valueId) &&
    text(x.propertyName, 256, 1) &&
    text(x.valueName, 256, 1)
  );
}
function activity(value: unknown): value is Activity {
  const x = value as Activity;
  return (
    base(value) &&
    integer(x.productSpuVersion) &&
    Array.isArray(x.skus) &&
    x.skus.length >= 1 &&
    x.skus.length <= 200 &&
    new Set(x.skus.map((s) => s?.skuId)).size === x.skus.length &&
    x.skus.every(
      (s) =>
        s &&
        integer(s.skuId, 1) &&
        integer(s.skuVersion) &&
        pool(s) &&
        integer(s.priceCent, 1, 2000000000) &&
        Array.isArray(s.properties) &&
        s.properties.length >= 1 &&
        s.properties.length <= 20 &&
        s.properties.every(property),
    ) &&
    x.skus.reduce((n, s) => n + s.plannedQuantity, 0) <= 2147483647
  );
}
function page<T>(
  value: unknown,
  guard: (x: unknown) => x is T,
): value is PageResult<T> {
  if (!value || typeof value !== "object") return false;
  const x = value as PageResult<unknown>;
  return (
    integer(x.total, 0, 2147483647) &&
    Array.isArray(x.list) &&
    x.list.length <= 20 &&
    x.list.length <= x.total &&
    x.list.every(guard)
  );
}
function pageNo(n: number) {
  if (!integer(n, 1, 10000)) throw new ApiError("页码超出范围", 400);
}
export async function seckillPage(n: number, name: string, status: string) {
  pageNo(n);
  if (!text(name, 60) || (status !== "" && !state(Number(status))))
    throw new ApiError("筛选条件不正确", 400);
  const query = new URLSearchParams({
    pageNo: String(n),
    pageSize: "20",
    name,
  });
  if (status !== "") query.set("status", status);
  const result = await call(`page?${query}`);
  if (
    !page(result, row) ||
    new Set(result.list.map((x) => x.id)).size !== result.list.length
  )
    throw new ApiError("活动列表不完整，请重新查询", -2);
  return result;
}
export async function seckillGet(id: number) {
  if (!integer(id, 1)) throw new ApiError("活动编号不正确", 400);
  const result = await call(`get?id=${id}`);
  if (!activity(result) || result.id !== id)
    throw new ApiError("活动详情不完整，请重新查询", -2);
  return result;
}
export async function seckillSave(body: DraftInput) {
  if (
    (body.id === null) !== (body.expectedVersion === null) ||
    (body.id !== null &&
      (!integer(body.id, 1) ||
        !integer(body.expectedVersion, 0, Number.MAX_SAFE_INTEGER - 1))) ||
    !text(body.name, 60, 1) ||
    !integer(body.productSpuId, 1) ||
    !offsetTime(body.startTime) ||
    !offsetTime(body.endTime) ||
    Date.parse(body.startTime) >= Date.parse(body.endTime) ||
    !integer(body.singleLimit, 1, 9999) ||
    !integer(body.memberLimit, body.singleLimit, 2147483647) ||
    !body.skus.length ||
    body.skus.length > 200 ||
    new Set(body.skus.map((s) => s.skuId)).size !== body.skus.length ||
    body.skus.some(
      (s) =>
        !integer(s.skuId, 1) ||
        !integer(s.priceCent, 1, 2000000000) ||
        !integer(s.plannedQuantity, 1, 2147483647),
    ) ||
    body.skus.reduce((n, s) => n + s.plannedQuantity, 0) > 2147483647
  )
    throw new ApiError("请检查活动时间、限购和规格数量", 400);
  // Configuration whitelist: never send tenant, actor, status, real balances or product snapshots.
  const result = await call(body.id === null ? "create" : "update", {
    method: body.id === null ? "POST" : "PUT",
    body: JSON.stringify({
      id: body.id,
      expectedVersion: body.expectedVersion,
      name: body.name,
      productSpuId: body.productSpuId,
      startTime: body.startTime,
      endTime: body.endTime,
      singleLimit: body.singleLimit,
      memberLimit: body.memberLimit,
      skus: body.skus.map((s) => ({
        skuId: s.skuId,
        priceCent: s.priceCent,
        plannedQuantity: s.plannedQuantity,
      })),
    }),
  });
  if (
    !activity(result) ||
    result.status !== 0 ||
    (body.id === null
      ? result.version !== 0 || result.generation !== 0
      : result.id !== body.id ||
        result.version !== (body.expectedVersion ?? -1) + 1) ||
    result.productSpuId !== body.productSpuId ||
    result.name !== body.name ||
    Date.parse(result.startTime) !== Date.parse(body.startTime) ||
    Date.parse(result.endTime) !== Date.parse(body.endTime) ||
    result.singleLimit !== body.singleLimit ||
    result.memberLimit !== body.memberLimit ||
    result.skus.length !== body.skus.length ||
    result.skus.some(
      (s) =>
        !body.skus.some(
          (p) =>
            p.skuId === s.skuId &&
            p.priceCent === s.priceCent &&
            p.plannedQuantity === s.plannedQuantity,
        ) ||
        [
          s.allocatedQuantity,
          s.availableQuantity,
          s.heldQuantity,
          s.soldQuantity,
          s.returnedQuantity,
        ].some((n) => n !== 0),
    )
  )
    throw new ApiError("保存结果待确认，请先核对活动列表和详情", -2, true);
  return result;
}
function history(value: unknown): value is PublishHistory {
  if (!value || typeof value !== "object") return false;
  const x = value as PublishHistory;
  return (
    uuid(x.requestId) &&
    integer(x.actorId, 1) &&
    integer(x.fromVersion, 0, Number.MAX_SAFE_INTEGER - 1) &&
    x.preparedVersion === x.fromVersion + 1 &&
    integer(x.generation, 1, 2147483647) &&
    offsetTime(x.preparedAt) &&
    [x.published, x.withdrawn, x.settled].every(
      (b) => typeof b === "boolean",
    ) &&
    (!x.withdrawn || x.published)
  );
}
export async function seckillHistory(id: number, n: number) {
  pageNo(n);
  if (!integer(id, 1)) throw new ApiError("活动编号不正确", 400);
  const result = await call(
    `publish-history?${new URLSearchParams({ id: String(id), pageNo: String(n), pageSize: "20" })}`,
  );
  if (
    !page(result, history) ||
    new Set(result.list.map((x) => x.requestId)).size !== result.list.length
  )
    throw new ApiError("发布历史不完整，请重新查询", -2);
  return result;
}
function clearingProgress(
  value: unknown,
  id: number,
  generation: number,
): value is ClearingProgress {
  if (!value || typeof value !== "object") return false;
  const x = value as ClearingProgress;
  return (
    x.id === id &&
    x.generation === generation &&
    integer(x.currentGeneration, generation, 2147483647) &&
    state(x.currentStatus) &&
    ["CURRENT_POOL", "SEALED_BASELINE"].includes(x.basis) &&
    [
      x.runtimeClosed,
      x.baselineSaved,
      x.clearingCompleted,
      x.draftReopened,
      x.paidQuotaConsistent,
    ].every((b) => typeof b === "boolean") &&
    (x.basis === "SEALED_BASELINE") === x.baselineSaved &&
    (!x.baselineSaved || x.runtimeClosed) &&
    (!x.clearingCompleted || x.baselineSaved) &&
    (!x.draftReopened || x.clearingCompleted) &&
    integer(x.serverTime, 1, 8640000000000000) &&
    !!x.pending &&
    typeof x.pending === "object" &&
    [
      "admission",
      "requests",
      "outbox",
      "resultCache",
      "refunds",
      "poolReturns",
      "memberHeld",
    ].every(
      (k) =>
        typeof x.pending[k as keyof ClearingProgress["pending"]] === "boolean",
    ) &&
    Array.isArray(x.skus) &&
    x.skus.length > 0 &&
    x.skus.length <= 200 &&
    new Set(x.skus.map((s) => s?.skuId)).size === x.skus.length &&
    x.skus.every(
      (s) =>
        !!s &&
        integer(s.skuId, 1) &&
        integer(s.allocatedQuantity, 1, 2147483647) &&
        [
          s.availableQuantity,
          s.heldQuantity,
          s.soldQuantity,
          s.returnedQuantity,
        ].every((n) => integer(n, 0, s.allocatedQuantity)) &&
        integer(s.returnPendingQuantity, 0, s.availableQuantity) &&
        integer(s.historicalReturnedQuantity, 0, s.returnedQuantity) &&
        s.availableQuantity +
          s.heldQuantity +
          s.soldQuantity +
          s.returnedQuantity ===
          s.allocatedQuantity &&
        typeof s.receiptConsistent === "boolean" &&
        (x.baselineSaved
          ? s.availableQuantity === 0 &&
            s.heldQuantity === 0 &&
            s.returnPendingQuantity === 0
          : s.historicalReturnedQuantity === 0),
    )
  );
}
export async function seckillClearingProgress(id: number, generation: number) {
  if (!integer(id, 1) || !integer(generation, 1, 2147483647))
    throw new ApiError("请选择活动及发布轮次", 400);
  const result = await call(
    `clearing-progress?${new URLSearchParams({
      id: String(id),
      generation: String(generation),
    })}`,
  );
  if (!clearingProgress(result, id, generation))
    throw new ApiError("本轮清算记录不完整，请重新查询", -2);
  return result;
}
function publishResult(
  value: unknown,
  intent: PublishIntent,
): value is PublishResult {
  if (!value || typeof value !== "object") return false;
  const x = value as PublishResult;
  return (
    x.id === intent.id &&
    x.requestId === intent.requestId &&
    x.fromVersion === intent.expectedVersion &&
    x.preparedVersion === intent.expectedVersion + 1 &&
    integer(x.generation, 1, 2147483647) &&
    typeof x.currentGeneration === "boolean" &&
    (x.currentGeneration
      ? state(x.status) && integer(x.version, x.preparedVersion)
      : x.status === null && x.version === null) &&
    [x.published, x.withdrawn, x.settled].every(
      (b) => typeof b === "boolean",
    ) &&
    (!x.withdrawn || x.published) &&
    (x.reopenedVersion === null ||
      (integer(x.reopenedVersion, x.preparedVersion + 1) && x.settled)) &&
    integer(x.serverTime, 1, 8640000000000000) &&
    Array.isArray(x.skus) &&
    x.skus.length >= 1 &&
    x.skus.length <= 200 &&
    new Set(x.skus.map((s) => s?.skuId)).size === x.skus.length &&
    x.skus.every(
      (s) =>
        s &&
        integer(s.skuId, 1) &&
        integer(s.quantity, 1, 2147483647) &&
        (s.productStatus === null
          ? s.productVersion === null &&
            s.allocatedQuantity === null &&
            s.returnedQuantity === null
          : integer(s.productVersion, 1) &&
            integer(s.allocatedQuantity) &&
            (s.allocatedQuantity === 0 || s.allocatedQuantity === s.quantity) &&
            integer(s.returnedQuantity, 0, s.allocatedQuantity) &&
            (s.productStatus === 10
              ? s.allocatedQuantity === s.quantity && s.returnedQuantity === 0
              : s.productStatus === 20
                ? s.allocatedQuantity === s.returnedQuantity
                : s.productStatus === 30
                  ? s.allocatedQuantity === s.quantity &&
                    s.allocatedQuantity === s.returnedQuantity
                  : s.productStatus === 50 &&
                    s.allocatedQuantity === s.quantity &&
                    s.returnedQuantity > 0 &&
                    s.returnedQuantity < s.allocatedQuantity)),
    )
  );
}
export async function seckillPublishResult(intent: PublishIntent) {
  if (
    !integer(intent.id, 1) ||
    !integer(intent.expectedVersion, 0, Number.MAX_SAFE_INTEGER - 1) ||
    !uuid(intent.requestId)
  )
    throw new ApiError("原发布请求不完整，请从历史重新选择", 400);
  const result = await call(
    `publish-result?${new URLSearchParams({
      id: String(intent.id),
      expectedVersion: String(intent.expectedVersion),
      requestId: intent.requestId,
    })}`,
  );
  if (result === null) return null; // Never infer no in-flight write or create a new UUID from null.
  if (!publishResult(result, intent))
    throw new ApiError("原发布结果不完整，请重新查询", -2);
  return result;
}

function validPublishPending(value: unknown): value is PublishPending {
  if (!value || typeof value !== "object") return false;
  const x = value as PublishPending;
  return (
    Object.keys(x).sort().join(",") ===
      "action,expectedVersion,id,reasonDigest,requestId,skuId" &&
    integer(x.id, 1) &&
    integer(x.expectedVersion, 0, Number.MAX_SAFE_INTEGER - 1) &&
    uuid(x.requestId) &&
    typeof x.action === "string" &&
    Object.hasOwn(publishActions, x.action) &&
    (["advance", "compensate"].includes(x.action)
      ? integer(x.skuId, 1)
      : x.skuId === null) &&
    (["cancel", "withdraw"].includes(x.action)
      ? typeof x.reasonDigest === "string" &&
        /^[0-9a-f]{64}$/.test(x.reasonDigest)
      : x.reasonDigest === null)
  );
}
function publishPendingKey() {
  return `qinghe:seckill-publish-pending:v1:${seckillIdentity()}`;
}
export function samePublishIntent(a: PublishIntent, b: PublishIntent) {
  return (
    a.id === b.id &&
    a.expectedVersion === b.expectedVersion &&
    a.requestId === b.requestId
  );
}
export function samePublishPending(a: PublishPending, b: PublishPending) {
  return (
    samePublishIntent(a, b) &&
    a.action === b.action &&
    a.skuId === b.skuId &&
    a.reasonDigest === b.reasonDigest
  );
}
export function readPublishPending(): PublishPending | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(publishPendingKey());
  } catch {
    throw new ApiError("无法读取待确认发布记录", -2);
  }
  if (raw === null) return null;
  let value: unknown;
  try {
    value = raw.length <= 512 ? JSON.parse(raw) : null;
  } catch {
    value = null;
  }
  if (!validPublishPending(value))
    throw new ApiError("待确认发布记录不完整，请先核对原活动", -2);
  return value;
}
function rememberPublish(value: PublishPending) {
  if (!validPublishPending(value)) throw new ApiError("原发布操作不完整", 400);
  const old = readPublishPending();
  const stopping =
    old &&
    samePublishIntent(old, value) &&
    value.action === "cancel" &&
    ["advance", "confirm"].includes(old.action);
  if (old && !samePublishPending(old, value) && !stopping)
    throw new ApiError("请先查询上一笔发布操作", 409);
  if (old && samePublishPending(old, value)) return;
  try {
    sessionStorage.setItem(publishPendingKey(), JSON.stringify(value));
  } catch {
    throw new ApiError("无法保存原发布记录，本次尚未提交", -2);
  }
}
export function forgetPublish(value: PublishPending) {
  const old = readPublishPending();
  if (old && !samePublishPending(old, value))
    throw new ApiError("待确认发布操作已变化", 409);
  try {
    sessionStorage.removeItem(publishPendingKey());
  } catch {
    throw new ApiError("无法清除已查询的发布记录，请再次查询", -2);
  }
}
async function reasonDigest(reason: string) {
  const normalized = reason.trim();
  if (!text(reason, 500, 1) || !normalized)
    throw new ApiError("请填写有效原因，最多500字且不含换行", 400);
  if (!globalThis.crypto?.subtle)
    throw new ApiError("当前浏览器无法保存原原因摘要，本次尚未提交", -2);
  const hash = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(normalized),
  );
  return Array.from(new Uint8Array(hash), (n) =>
    n.toString(16).padStart(2, "0"),
  ).join("");
}
export async function publishPendingFor(
  action: PublishAction,
  intent: PublishIntent,
  skuId: number | null,
  reason: string,
) {
  const value: PublishPending = {
    id: intent.id,
    expectedVersion: intent.expectedVersion,
    requestId: intent.requestId,
    action,
    skuId,
    reasonDigest: ["cancel", "withdraw"].includes(action)
      ? await reasonDigest(reason)
      : null,
  };
  if (!validPublishPending(value)) throw new ApiError("原发布操作不完整", 400);
  return value;
}
export function publishOutcome(
  pending: PublishPending,
  result: PublishResult | null,
): "confirmed" | "closed" | "unknown" {
  if (
    !result ||
    !samePublishIntent(pending, {
      id: result.id,
      expectedVersion: result.fromVersion,
      requestId: result.requestId,
    })
  )
    return "unknown";
  const sku = result.skus.find((s) => s.skuId === pending.skuId);
  const confirmed =
    pending.action === "prepare" ||
    (pending.action === "advance" &&
      sku?.productStatus !== null &&
      sku?.productStatus !== undefined) ||
    (pending.action === "cancel" &&
      result.currentGeneration &&
      result.status === 60) ||
    (pending.action === "compensate" && sku?.productStatus === 20) ||
    (pending.action === "confirm" && result.published) ||
    (pending.action === "withdraw" && result.withdrawn) ||
    (pending.action === "settle" && result.settled) ||
    (pending.action === "reopen" && result.reopenedVersion !== null);
  if (confirmed) return "confirmed";
  // Original batch closure is a terminal resource fact, not proof the attempted action succeeded.
  if (
    pending.action !== "reopen" &&
    result.settled &&
    result.skus.every(
      (s) =>
        s.productStatus === 20 && s.allocatedQuantity === s.returnedQuantity,
    )
  )
    return "closed";
  return "unknown";
}
export async function seckillPublishCommand(
  value: PublishPending,
  reason: string,
) {
  if (!validPublishPending(value)) throw new ApiError("原发布操作不完整", 400);
  if (
    demo ||
    context.realm !== "merchant" ||
    !identities.merchant ||
    !credentials().accessToken
  )
    throw new ApiError("请从商家入口重新登录，本次尚未提交", 401);
  const actor = seckillIdentity(),
    token = credentials().accessToken;
  if (
    value.reasonDigest !== null &&
    (await reasonDigest(reason)) !== value.reasonDigest
  )
    throw new ApiError("请填写与原操作一致的原因，不能修改原请求", 400);
  if (actor !== seckillIdentity() || token !== credentials().accessToken)
    throw new ApiError("身份已变化，本次尚未提交", 409);
  rememberPublish(value); // Must succeed before the single POST; all errors retain this exact marker.
  const body: Record<string, unknown> = {
    id: value.id,
    expectedVersion: value.expectedVersion,
    requestId: value.requestId,
  };
  if (value.skuId !== null) body.skuId = value.skuId;
  if (value.reasonDigest !== null) body.reason = reason.trim();
  let result: unknown;
  try {
    result = await call(publishActions[value.action].path, {
      method: "POST",
      body: JSON.stringify(body),
    });
  } catch (e) {
    // Only an actual endpoint rejection, never local input/hash/storage errors or unknown transport failures.
    if (
      e instanceof ApiError &&
      !e.outcomeUnknown &&
      [400, 409].includes(e.code)
    )
      throw new PublishRejected(e);
    throw e;
  }
  if (
    !publishResult(result, value) ||
    publishOutcome(value, result) === "unknown"
  )
    throw new ApiError("原发布结果待确认，请先查询", -2, true);
  return result;
}
export class PublishRejected extends ApiError {
  constructor(source: ApiError) {
    super(source.message, source.code, false, source.requestId);
  }
}

// Minimal actor-scoped draft save reminder, not a request body or a trusted success receipt.
function pendingKey() {
  return `qinghe:seckill-draft-pending:v1:${seckillIdentity()}`;
}
export function readDraftPending(): DraftPending | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(pendingKey());
  } catch {
    throw new ApiError("无法读取保存记录，请检查浏览器隐私设置", -2);
  }
  if (raw === null) return null;
  let x: unknown;
  try {
    x = raw.length <= 256 ? JSON.parse(raw) : null;
  } catch {
    x = null;
  }
  if (!x || typeof x !== "object")
    throw new ApiError("保存记录无法读取，请先核对活动列表", -2);
  const v = x as DraftPending;
  if (
    Object.keys(v).sort().join(",") !== "expectedVersion,id,productSpuId" ||
    !integer(v.productSpuId, 1) ||
    !(
      (v.id === null && v.expectedVersion === null) ||
      (integer(v.id, 1) && integer(v.expectedVersion))
    )
  )
    throw new ApiError("保存记录无法读取，请先核对活动列表", -2);
  return v;
}
export function rememberDraft(body: DraftPending) {
  if (readDraftPending()) throw new ApiError("请先核对上一笔保存结果", 409);
  try {
    sessionStorage.setItem(
      pendingKey(),
      JSON.stringify({
        id: body.id,
        expectedVersion: body.expectedVersion,
        productSpuId: body.productSpuId,
      }),
    );
  } catch {
    throw new ApiError("无法保存处理记录，本次尚未提交", -2);
  }
}
export function forgetDraft(body: DraftPending) {
  const old = readDraftPending();
  if (
    old &&
    (old.id !== body.id ||
      old.expectedVersion !== body.expectedVersion ||
      old.productSpuId !== body.productSpuId)
  )
    throw new ApiError("保存记录已变化，请重新核对", 409);
  try {
    sessionStorage.removeItem(pendingKey());
  } catch {
    throw new ApiError("无法清除保存记录，请重新核对", -2);
  }
}
