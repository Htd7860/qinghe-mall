import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";

export const platformSeckillPermission = "promotion:seckill-operation:";
export type PlatformActivity = {
  id: number;
  tenantId: number;
  name: string;
  productName: string;
  status: number;
  generation: number;
  version: number;
  startTime: string;
  endTime: string;
};
export type PlatformAudit = {
  id: number;
  actorId: number;
  actorRealm: "platform" | "merchant" | "system";
  action: string;
  fromVersion: number;
  toVersion: number;
  createTime: string;
};
export type StopCommand = {
  tenantId: number;
  id: number;
  generation: number;
  expectedVersion: number;
  reason: string;
};
export type StopReceipt = {
  tenantId: number;
  id: number;
  generation: number;
  stoppedVersion: number;
  cacheCloseState: "PENDING" | "SYNCED" | "NOT_REQUIRED";
};
const integer = (
  x: unknown,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
): x is number =>
  typeof x === "number" && Number.isSafeInteger(x) && x >= min && x <= max;
const text = (x: unknown, max: number): x is string =>
  typeof x === "string" &&
  x.length > 0 &&
  x.length <= max &&
  !/[\x00-\x1f\x7f]/.test(x);
const time = (x: unknown): x is string =>
  typeof x === "string" &&
  /(?:Z|[+-]\d{2}:\d{2})$/.test(x) &&
  Number.isFinite(Date.parse(x));
export const platformSeckillIdentity = () =>
  `${context.realm}:${identities.platform?.tenantId ?? ""}:${identities.platform?.userId ?? ""}`;
async function call(path: string, init: RequestInit = {}) {
  if (
    demo ||
    context.realm !== "platform" ||
    identities.platform?.tenantId !== "0"
  )
    throw new ApiError("请从平台入口管理秒杀活动", 403);
  const actor = platformSeckillIdentity(),
    auth = credentials();
  if (!auth.accessToken) throw new ApiError("请重新登录", 401);
  const data = await request<unknown>(
    `/platform-api/promotion/seckill-operation/${path}`,
    { ...auth, timeoutMs: 15000 },
    init,
  );
  if (
    actor !== platformSeckillIdentity() ||
    auth.accessToken !== credentials().accessToken
  )
    throw new ApiError(
      "身份已变化，请重新读取原记录",
      409,
      init.method === "POST",
    );
  return data;
}
function page<T>(
  data: unknown,
  valid: (x: unknown) => x is T,
): data is PageResult<T> {
  if (!data || typeof data !== "object") return false;
  const x = data as PageResult<T>;
  return (
    integer(x.total, 0, 2147483647) &&
    Array.isArray(x.list) &&
    x.list.length <= 20 &&
    x.list.length <= x.total &&
    x.list.every(valid)
  );
}
function activity(data: unknown): data is PlatformActivity {
  if (!data || typeof data !== "object") return false;
  const x = data as PlatformActivity;
  return (
    integer(x.id, 1) &&
    integer(x.tenantId, 1) &&
    text(x.name, 60) &&
    text(x.productName, 128) &&
    [0, 10, 20, 30, 40, 50, 60].includes(x.status) &&
    integer(x.generation, 0, 2147483647) &&
    integer(x.version) &&
    time(x.startTime) &&
    time(x.endTime) &&
    Date.parse(x.startTime) < Date.parse(x.endTime)
  );
}
function audit(data: unknown): data is PlatformAudit {
  if (!data || typeof data !== "object") return false;
  const x = data as PlatformAudit;
  return (
    integer(x.id, 1) &&
    integer(x.actorId) &&
    ["platform", "merchant", "system"].includes(x.actorRealm) &&
    (x.actorRealm === "system") === (x.actorId === 0) &&
    text(x.action, 40) &&
    integer(x.fromVersion, -1) &&
    integer(x.toVersion) &&
    x.toVersion === x.fromVersion + 1 &&
    time(x.createTime)
  );
}
export async function platformSeckillPage(
  n: number,
  tenant: string,
  name: string,
  status: string,
) {
  if (
    !integer(n, 1, 10000) ||
    (tenant &&
      (!/^[1-9][0-9]*$/.test(tenant) || !integer(Number(tenant), 1))) ||
    name.length > 60 ||
    /[\x00-\x1f\x7f]/.test(name) ||
    (status && !["0", "10", "20", "30", "40", "50", "60"].includes(status))
  )
    throw new ApiError("商家编号或筛选参数不正确", 400);
  const q = new URLSearchParams({ pageNo: String(n), pageSize: "20" });
  if (tenant) q.set("tenantId", tenant);
  if (name) q.set("name", name);
  if (status) q.set("status", status);
  const result = await call(`page?${q}`);
  if (
    !page(result, activity) ||
    new Set(result.list.map((r) => r.id)).size !== result.list.length ||
    result.list.some((r) => tenant && r.tenantId !== Number(tenant))
  )
    throw new ApiError("活动列表不完整，请重新查询", -2);
  return result;
}
export async function platformSeckillAudits(row: PlatformActivity, n: number) {
  if (!activity(row) || !integer(n, 1, 10000))
    throw new ApiError("目标活动或页码不正确", 400);
  const result = await call(
    `audit-page?${new URLSearchParams({ tenantId: String(row.tenantId), id: String(row.id), pageNo: String(n), pageSize: "20" })}`,
  );
  if (
    !page(result, audit) ||
    new Set(result.list.map((r) => r.id)).size !== result.list.length
  )
    throw new ApiError("审计记录不完整，请重新查询", -2);
  return result;
}
export function validStop(data: unknown): data is StopCommand {
  if (!data || typeof data !== "object") return false;
  const x = data as StopCommand;
  return (
    integer(x.tenantId, 1) &&
    integer(x.id, 1) &&
    integer(x.generation, 1, 2147483647) &&
    integer(x.expectedVersion, 0, Number.MAX_SAFE_INTEGER - 1) &&
    text(x.reason, 500) &&
    x.reason.trim() === x.reason
  );
}
const pendingKey = () =>
  `qinghe.platform.seckill.stop:${platformSeckillIdentity()}`;
export function readPlatformStop(): StopCommand | null {
  const raw = sessionStorage.getItem(pendingKey());
  if (raw === null) return null;
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new ApiError("原停止记录损坏，请保留并核查", -2);
  }
  if (!validStop(value))
    throw new ApiError("原停止记录不完整，请保留并核查", -2);
  return value;
}
export function rememberPlatformStop(command: StopCommand) {
  if (!validStop(command)) throw new ApiError("停止目标或原因不正确", 400);
  const old = readPlatformStop();
  if (old && JSON.stringify(old) !== JSON.stringify(command))
    throw new ApiError("请先核对上一条停止操作", 409);
  sessionStorage.setItem(pendingKey(), JSON.stringify(command));
}
export async function platformSeckillStop(command: StopCommand) {
  if (!validStop(command)) throw new ApiError("原停止操作不完整", 400);
  const result = await call("stop", {
    method: "POST",
    body: JSON.stringify(command),
  });
  const x = result as StopReceipt | null;
  if (
    !x ||
    x.tenantId !== command.tenantId ||
    x.id !== command.id ||
    x.generation !== command.generation ||
    x.stoppedVersion !== command.expectedVersion + 1 ||
    !["PENDING", "SYNCED", "NOT_REQUIRED"].includes(x.cacheCloseState)
  )
    throw new ApiError("停止结果待核对，请保留原目标重查", -2, true);
  return x;
}
export function forgetPlatformStop(command: StopCommand) {
  const old = readPlatformStop();
  if (old && JSON.stringify(old) !== JSON.stringify(command))
    throw new ApiError("原停止记录已变化", 409);
  sessionStorage.removeItem(pendingKey());
}
