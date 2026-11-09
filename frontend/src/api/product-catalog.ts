import { request, ApiError, type PageResult } from "./http";
import { context, credentials, identities, demo } from "./session";

export type CatalogKind = "categories" | "brands" | "properties" | "values";
export type CatalogRow = {
  id: number;
  name: string;
  status: number;
  authVersion: number;
  parentId?: number;
  propertyId?: number;
  type?: number;
  picUrl?: string;
  sort?: number;
  description?: string;
  remark?: string;
};
export const catalogSpec = {
  categories: { title: "商品分类", path: "category", permission: "category" },
  brands: { title: "商品品牌", path: "brand", permission: "brand" },
  properties: { title: "规格属性", path: "property", permission: "property" },
  values: { title: "属性值", path: "property/value", permission: "property" },
} as const;

export function catalogIdentity() {
  const identity = identities[context.realm];
  return `${context.realm}:${identity?.tenantId ?? ""}:${identity?.userId ?? ""}`;
}
function valid(row: unknown): row is CatalogRow {
  if (!row || typeof row !== "object") return false;
  const value = row as CatalogRow;
  return (
    Number.isSafeInteger(value.id) &&
    value.id > 0 &&
    Number.isSafeInteger(value.authVersion) &&
    value.authVersion >= 0 &&
    typeof value.name === "string" &&
    [0, 1].includes(value.status)
  );
}
async function call<T>(
  kind: CatalogKind,
  suffix: string,
  init: RequestInit = {},
) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口管理商品资料", 403);
  const identity = catalogIdentity();
  const result = await request<T>(
    `/admin-api/product/${catalogSpec[kind].path}/${suffix}`,
    credentials(),
    init,
  );
  if (identity !== catalogIdentity())
    throw new ApiError(
      "登录身份已变化，请重新读取",
      409,
      init.method !== undefined,
    );
  return result;
}
export async function catalogList(
  kind: CatalogKind,
  pageNo: number,
  name: string,
  propertyId?: number,
) {
  if (kind === "categories") {
    const result = await call<unknown>(kind, "list");
    if (!Array.isArray(result) || !result.every(valid))
      throw new ApiError("分类数据不完整", -2);
    return { list: result, total: result.length } as PageResult<CatalogRow>;
  }
  const query = new URLSearchParams({
    pageNo: String(pageNo),
    pageSize: "20",
    name,
  });
  if (kind === "values") {
    if (!Number.isSafeInteger(propertyId) || Number(propertyId) <= 0)
      throw new ApiError("请先选择属性", 400);
    query.set("propertyId", String(propertyId));
  }
  const result = await call<PageResult<CatalogRow>>(kind, `page?${query}`);
  if (
    !result ||
    !Array.isArray(result.list) ||
    !result.list.every(valid) ||
    !Number.isSafeInteger(result.total) ||
    result.total < 0
  )
    throw new ApiError("商品资料数据不完整", -2);
  return result;
}
export async function catalogGet(kind: CatalogKind, id: number) {
  if (!Number.isSafeInteger(id) || id <= 0)
    throw new ApiError("记录编号不正确", 400);
  const result = await call<unknown>(kind, `get?id=${id}`);
  if (!valid(result)) throw new ApiError("记录已不存在，请重新读取", -2);
  return result;
}
export async function catalogSave(
  kind: CatalogKind,
  body: Record<string, unknown>,
  editing: boolean,
) {
  const result = await call<unknown>(kind, editing ? "update" : "create", {
    method: editing ? "PUT" : "POST",
    body: JSON.stringify(body),
  });
  if (
    editing
      ? result !== true
      : !Number.isSafeInteger(result) || Number(result) <= 0
  )
    throw new ApiError("保存结果待确认，请重新读取列表", -2, true);
}
export async function catalogDelete(kind: CatalogKind, row: CatalogRow) {
  if (!valid(row)) throw new ApiError("记录版本不完整，请重新读取", 400);
  const result = await call<unknown>(
    kind,
    `delete?id=${row.id}&expectedVersion=${row.authVersion}`,
    { method: "DELETE" },
  );
  if (result !== true)
    throw new ApiError("删除结果待确认，请重新读取列表", -2, true);
}
