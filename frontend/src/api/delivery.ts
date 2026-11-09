import { request, ApiError, type PageResult } from "./http";
import { context, credentials, identities, demo } from "./session";
export const deliveryPermission = "trade:delivery:express-template:";
export type DeliveryRow = {
  id: number;
  name: string;
  chargeMode: number;
  status: number;
  sort: number;
  authVersion: number;
  createTime?: string;
};
export type ChargeRule = {
  id?: number;
  defaultRule: boolean;
  areaIds: number[];
  startCount: string;
  startPrice: number;
  extraCount: string;
  extraPrice: number;
};
export type FreeRule = {
  id?: number;
  defaultRule: boolean;
  areaIds: number[];
  freePrice: number;
  freeCount: number;
};
export type DeliveryDetail = DeliveryRow & {
  charges: ChargeRule[];
  frees: FreeRule[];
};
export type AreaOption = {
  id: number;
  parentId: number;
  name: string;
  type: number;
};
export function deliveryIdentity() {
  const user = identities[context.realm];
  return `${context.realm}:${user?.tenantId ?? ""}:${user?.userId ?? ""}`;
}
async function call<T>(path: string, init: RequestInit = {}) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口管理配送模板", 403);
  const identity = deliveryIdentity();
  const result = await request<T>(
    `/admin-api/trade/delivery/express-template/${path}`,
    credentials(),
    init,
  );
  if (identity !== deliveryIdentity())
    throw new ApiError(
      "登录身份已变化，请重新读取",
      409,
      init.method !== undefined,
    );
  return result;
}
const integer = (value: unknown, min = 0) =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= min;
function row(value: unknown): value is DeliveryRow {
  if (!value || typeof value !== "object") return false;
  const item = value as DeliveryRow;
  return (
    integer(item.id, 1) &&
    integer(item.authVersion) &&
    typeof item.name === "string" &&
    [1, 2].includes(item.chargeMode) &&
    [0, 1].includes(item.status) &&
    integer(item.sort)
  );
}
function region(rule: {
  id?: number;
  defaultRule: boolean;
  areaIds: number[];
}) {
  return (
    integer(rule.id, 1) &&
    typeof rule.defaultRule === "boolean" &&
    Array.isArray(rule.areaIds) &&
    rule.areaIds.every((id) => integer(id, 1))
  );
}
export async function deliveryPage(pageNo: number, name: string) {
  const result = await call<PageResult<DeliveryRow>>(
    `page?${new URLSearchParams({ pageNo: String(pageNo), pageSize: "20", name })}`,
  );
  if (
    !result ||
    !integer(result.total) ||
    !Array.isArray(result.list) ||
    !result.list.every(row)
  )
    throw new ApiError("配送模板列表不完整", -2);
  return result;
}
export async function deliveryGet(id: number) {
  if (!integer(id, 1)) throw new ApiError("模板编号不正确", 400);
  const result = await call<DeliveryDetail>(`get?id=${id}`);
  if (
    !row(result) ||
    !Array.isArray(result.charges) ||
    !Array.isArray(result.frees) ||
    !result.charges.every(
      (rule) =>
        region(rule) &&
        typeof rule.startCount === "string" &&
        typeof rule.extraCount === "string" &&
        integer(rule.startPrice) &&
        integer(rule.extraPrice),
    ) ||
    !result.frees.every(
      (rule) =>
        region(rule) && integer(rule.freePrice) && integer(rule.freeCount),
    )
  )
    throw new ApiError("配送模板详情不完整，请确认接口版本", -2);
  return result;
}
export async function deliveryAreas() {
  const result = await call<AreaOption[]>("area-options");
  if (
    !Array.isArray(result) ||
    result.length > 10000 ||
    !result.every(
      (item) =>
        integer(item.id, 1) &&
        integer(item.parentId) &&
        typeof item.name === "string" &&
        [2, 3, 4].includes(item.type),
    ) ||
    new Set(result.map((item) => item.id)).size !== result.length
  )
    throw new ApiError("地区选项不完整", -2);
  const index = new Map(result.map((item) => [item.id, item]));
  for (const item of result) {
    if (item.parentId !== 0) {
      const parent = index.get(item.parentId);
      if (!parent || parent.type >= item.type)
        throw new ApiError("地区父链不正确", -2);
    }
  }
  return result;
}
export async function deliverySave(
  body: Record<string, unknown>,
  editing: boolean,
) {
  const result = await call<unknown>(editing ? "update" : "create", {
    method: editing ? "PUT" : "POST",
    body: JSON.stringify(body),
  });
  if (editing ? result !== true : !integer(result, 1))
    throw new ApiError("保存结果待确认，请重新读取列表", -2, true);
}
export async function deliveryDelete(value: DeliveryRow) {
  if (!row(value)) throw new ApiError("模板版本不完整，请重新读取", 400);
  const result = await call<unknown>(
    `delete?id=${value.id}&expectedVersion=${value.authVersion}`,
    { method: "DELETE" },
  );
  if (result !== true)
    throw new ApiError("删除结果待确认，请重新读取列表", -2, true);
}
