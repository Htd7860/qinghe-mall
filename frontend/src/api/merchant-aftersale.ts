import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";

import {
  type AfterSaleSummary,
  type AfterSaleDetail,
  type AfterSaleLog,
  type ReturnAddress,
  type AreaOption,
  type Decision,
  afterSaleStates,
  afterSaleActions,
  integer,
  text,
  record,
  uuid,
  states,
  summary,
  detail,
  log,
} from "./aftersale-contract";
export type {
  Inspection,
  AfterSaleSummary,
  AfterSaleDetail,
  AfterSaleLog,
  ReturnAddress,
  AreaOption,
  Decision,
} from "./aftersale-contract";
export { afterSaleStates, afterSaleActions } from "./aftersale-contract";

// Selected upstream afterSale page/detail/decision duties; current tenant and UUID/version contract.
export type Action = "agree" | "disagree" | "receive";
export type Command = {
  id: number;
  expectedVersion: number;
  requestId: string;
  reason: string;
  qualityApproved: boolean;
  returnAddress?: ReturnAddress;
  receivedCount?: number;
  salableCount?: number;
  accepted?: boolean;
};
export type PendingDecision = {
  id: number;
  no: string;
  action: Action;
  requestId: string;
  expectedVersion: number;
  toStatus: number;
};
export function afterSaleIdentity() {
  return `${context.realm}:${identities.merchant?.tenantId || ""}:${identities.merchant?.userId || ""}`;
}
async function call(path: string, init: RequestInit = {}) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口处理售后", 403);
  const actor = afterSaleIdentity(),
    bearer = credentials();
  if (!bearer.accessToken) throw new ApiError("请重新登录", 401);
  const value = await request<unknown>(
    `/admin-api/trade/after-sale/${path}`,
    { ...bearer, timeoutMs: 15000 },
    init,
  );
  if (
    actor !== afterSaleIdentity() ||
    bearer.accessToken !== credentials().accessToken
  )
    throw new ApiError("身份已变化，请重新查询原售后", 409, !!init.method);
  return value;
}
// Reuse the same merchant/Bearer/identity boundary for the financial aftersale slice.
export { call as afterSaleRequest };
export async function afterSalePage(
  pageNo: number,
  no: string,
  status: string,
  overdueOnly: boolean,
) {
  if (
    !integer(pageNo, 1) ||
    (no && !/^AS[0-9a-f]{32}$/.test(no)) ||
    (status && !states.includes(Number(status)))
  )
    throw new ApiError("请检查售后编号或状态", 400);
  const query = new URLSearchParams({
    pageNo: String(pageNo),
    pageSize: "20",
    overdueOnly: String(overdueOnly),
  });
  if (no) query.set("no", no);
  if (status) query.set("status", status);
  const value = await call(`page?${query}`);
  if (
    !record(value) ||
    !integer(value.total) ||
    !Array.isArray(value.list) ||
    value.list.length > 20 ||
    !value.list.every(summary) ||
    new Set(value.list.map((x) => x.id)).size !== value.list.length
  )
    throw new ApiError("售后列表不完整，请重新查询", -2);
  return value as PageResult<AfterSaleSummary>;
}
export async function afterSaleDetail(id: number) {
  if (!integer(id, 1)) throw new ApiError("售后编号不正确", 400);
  const value = await call(`get-detail?id=${id}`);
  if (!detail(value) || value.id !== id)
    throw new ApiError("售后详情不完整，请重新查询", -2);
  return value;
}
export async function afterSaleLogs(id: number, pageNo: number) {
  if (!integer(id, 1) || !integer(pageNo, 1))
    throw new ApiError("售后记录分页不正确", 400);
  const value = await call(
    `log-page?${new URLSearchParams({ id: String(id), pageNo: String(pageNo), pageSize: "20" })}`,
  );
  if (
    !record(value) ||
    !integer(value.total) ||
    !Array.isArray(value.list) ||
    value.list.length > 20 ||
    !value.list.every(log) ||
    new Set(value.list.map((x) => x.id)).size !== value.list.length
  )
    throw new ApiError("售后记录不完整，请重新查询", -2);
  return value as PageResult<AfterSaleLog>;
}
function decision(x: unknown): x is Decision {
  return (
    record(x) &&
    integer(x.id, 1) &&
    typeof x.status === "number" &&
    [20, 35, 40, 62].includes(x.status) &&
    integer(x.authVersion, 1)
  );
}
export async function afterSaleDecisionResult(marker: PendingDecision) {
  const value = await call(
    `decision-result?requestId=${encodeURIComponent(marker.requestId)}`,
  );
  if (value === null) return null;
  if (
    !decision(value) ||
    value.id !== marker.id ||
    value.status !== marker.toStatus ||
    value.authVersion !== marker.expectedVersion + 1
  )
    throw new ApiError("原操作结果不完整，请保留待确认记录", -2);
  return value;
}
export async function afterSaleCommand(action: Action, body: Command) {
  if (
    !["agree", "disagree", "receive"].includes(action) ||
    !integer(body.id, 1) ||
    !integer(body.expectedVersion) ||
    body.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !uuid(body.requestId) ||
    !body.reason.trim() ||
    body.reason.length > 500 ||
    /[\x00-\x1f\x7f]/.test(body.reason)
  )
    throw new ApiError("请检查售后决定及原因", 400);
  const value = await call(action, {
    method: "POST",
    body: JSON.stringify(body),
  });
  const targets =
    action === "agree"
      ? [20, 40]
      : action === "disagree"
        ? [62]
        : [body.accepted ? 40 : 35];
  if (
    !decision(value) ||
    value.id !== body.id ||
    value.authVersion !== body.expectedVersion + 1 ||
    !targets.includes(value.status)
  )
    throw new ApiError("处理结果待确认，请查询原操作", -2, true);
  return value;
}
export async function afterSaleAreas() {
  const value = await call("area-options");
  if (
    !Array.isArray(value) ||
    value.length > 10000 ||
    !value.every(
      (x) =>
        record(x) &&
        integer(x.id, 1) &&
        integer(x.parentId) &&
        text(x.name, 100) &&
        typeof x.type === "number" &&
        [2, 3, 4].includes(x.type),
    ) ||
    new Set(value.map((x) => x.id)).size !== value.length
  )
    throw new ApiError("地区选项不完整，请重新读取", -2);
  const result = value as AreaOption[],
    index = new Map(result.map((x) => [x.id, x]));
  if (
    result.some(
      (x) =>
        x.parentId !== 0 &&
        (!index.has(x.parentId) || index.get(x.parentId)!.type >= x.type),
    )
  )
    throw new ApiError("地区信息不完整", -2);
  return result;
}
export async function afterSaleReturnAddress(id: number) {
  if (!integer(id, 1)) throw new ApiError("售后编号不正确", 400);
  const value = await call(`return-address?id=${id}`);
  if (
    !record(value) ||
    !text(value.name, 50) ||
    !value.name.trim() ||
    typeof value.mobile !== "string" ||
    !/^1[3-9][0-9]{9}$/.test(value.mobile) ||
    !integer(value.areaId, 1) ||
    !text(value.detail, 255) ||
    !value.detail.trim()
  )
    throw new ApiError("寄回地址不完整，请重新查询", -2);
  return value as ReturnAddress;
}

// One actor-scoped recovery marker, never the command body, credentials, contact/address or reason.
function pendingKey() {
  return `qinghe:aftersale-pending:v1:${afterSaleIdentity()}`;
}
export function readPendingDecision(): PendingDecision | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(pendingKey());
  } catch {
    throw new ApiError("无法读取待确认记录，请检查浏览器隐私设置", -2);
  }
  if (raw === null) return null;
  let x: unknown;
  try {
    x = raw.length <= 512 ? JSON.parse(raw) : null;
  } catch {
    x = null;
  }
  if (
    !record(x) ||
    Object.keys(x).sort().join(",") !==
      "action,expectedVersion,id,no,requestId,toStatus" ||
    !integer(x.id, 1) ||
    typeof x.no !== "string" ||
    !/^AS[0-9a-f]{32}$/.test(x.no) ||
    !uuid(x.requestId) ||
    !["agree", "disagree", "receive"].includes(String(x.action)) ||
    !integer(x.expectedVersion) ||
    x.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    typeof x.toStatus !== "number" ||
    !(
      x.action === "agree"
        ? [20, 40]
        : x.action === "disagree"
          ? [62]
          : [35, 40]
    ).includes(x.toStatus)
  )
    throw new ApiError("待确认记录无法读取，请先核对售后历史", -2);
  return x as PendingDecision;
}
export function savePendingDecision(value: PendingDecision) {
  if (readPendingDecision() !== null)
    throw new ApiError("请先查询上一笔待确认操作", 409);
  try {
    sessionStorage.setItem(
      pendingKey(),
      JSON.stringify({
        id: value.id,
        no: value.no,
        action: value.action,
        requestId: value.requestId,
        expectedVersion: value.expectedVersion,
        toStatus: value.toStatus,
      }),
    );
  } catch {
    throw new ApiError("无法保存处理记录，请检查浏览器隐私设置后重试", -2);
  }
}
export function removePendingDecision(value: PendingDecision) {
  const old = readPendingDecision();
  if (old && old.requestId !== value.requestId)
    throw new ApiError("待确认操作已变化，请重新查询", 409);
  try {
    sessionStorage.removeItem(pendingKey());
  } catch {
    throw new ApiError("暂时无法清除待确认记录，请再查询一次", -2);
  }
}
