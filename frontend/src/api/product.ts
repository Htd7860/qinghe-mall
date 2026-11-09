import { request, ApiError, type PageResult } from "./http";
import { context, credentials, identities, demo } from "./session";
export type SkuProperty = {
  propertyId: number;
  propertyName: string;
  valueId: number;
  valueName: string;
};
export type ProductSku = {
  id: number;
  price: number;
  marketPrice: number;
  costPrice: number;
  stock: number;
  status: number;
  authVersion: number;
  barCode: string | null;
  picUrl: string;
  weight: string | null;
  volume: string | null;
  properties: SkuProperty[];
};
export type ProductRow = {
  id: number;
  name: string;
  status: number;
  authVersion: number;
  price: number;
  stock: number;
  brandId: number;
  categoryId: number;
  picUrl: string;
  ownerUserId: number;
  ownerDeptId: number | null;
  sort: number;
  specType: boolean;
};
export type ProductDetail = ProductRow & {
  keyword: string | null;
  introduction: string | null;
  description: string;
  sliderPicUrls: string[];
  deliveryTemplateId: number;
  displayProperties: SkuProperty[];
  skus: ProductSku[];
};
export type StockRequest = {
  spuId: number;
  operationKey: string;
  reason: string;
  items: { skuId: number; delta: number }[];
};
export type StockResult = {
  id: number;
  operationKey: string;
  kind: string;
  spuId: number;
  createTime: string;
  items: {
    skuId: number;
    delta: number;
    beforeStock: number;
    afterStock: number;
  }[];
};
const integer = (value: unknown, min = 0): value is number =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= min;
export function productIdentity() {
  const user = identities[context.realm];
  return `${context.realm}:${user?.tenantId ?? ""}:${user?.userId ?? ""}`;
}
async function call<T>(path: string, init: RequestInit = {}) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口管理商品", 403);
  const identity = productIdentity();
  const result = await request<T>(
    `/admin-api/product/${path}`,
    { ...credentials(), timeoutMs: 20000 },
    init,
  );
  if (identity !== productIdentity())
    throw new ApiError(
      "登录身份已变化，请重新读取",
      409,
      init.method !== undefined,
    );
  return result;
}
function row(value: unknown): value is ProductRow {
  if (!value || typeof value !== "object") return false;
  const item = value as ProductRow;
  return (
    integer(item.id, 1) &&
    integer(item.authVersion) &&
    typeof item.name === "string" &&
    [-1, 0, 1].includes(item.status) &&
    integer(item.price) &&
    integer(item.stock) &&
    integer(item.ownerUserId, 1)
  );
}
function property(value: SkuProperty) {
  return (
    value &&
    integer(value.propertyId) &&
    integer(value.valueId) &&
    typeof value.propertyName === "string" &&
    typeof value.valueName === "string"
  );
}
export async function productPage(
  pageNo: number,
  tabType: number | undefined,
  name: string,
) {
  const query = new URLSearchParams({
    pageNo: String(pageNo),
    pageSize: "20",
    name,
  });
  if (tabType !== undefined) query.set("tabType", String(tabType));
  const result = await call<PageResult<ProductRow>>(`spu/page?${query}`);
  if (
    !result ||
    !integer(result.total) ||
    !Array.isArray(result.list) ||
    !result.list.every(row)
  )
    throw new ApiError("商品列表不完整", -2);
  return result;
}
export async function productCounts(name: string) {
  const result = await call<Record<string, number>>(
    `spu/get-count?${new URLSearchParams({ name })}`,
  );
  if (!result || ![0, 1, 2, 3, 4].every((tab) => integer(result[String(tab)])))
    throw new ApiError("商品数量统计不完整", -2);
  return result;
}
export async function productDetail(id: number) {
  if (!integer(id, 1)) throw new ApiError("商品编号不正确", 400);
  const result = await call<ProductDetail>(`spu/get-detail?id=${id}`);
  if (
    !row(result) ||
    typeof result.description !== "string" ||
    ![result.keyword, result.introduction].every(
      (value) => value === null || typeof value === "string",
    ) ||
    !integer(result.categoryId, 1) ||
    !integer(result.brandId, 1) ||
    !integer(result.deliveryTemplateId, 1) ||
    typeof result.picUrl !== "string" ||
    typeof result.specType !== "boolean" ||
    !integer(result.sort) ||
    !Array.isArray(result.sliderPicUrls) ||
    !result.sliderPicUrls.every((value) => typeof value === "string") ||
    !Array.isArray(result.displayProperties) ||
    !result.displayProperties.every(property) ||
    !Array.isArray(result.skus) ||
    !result.skus.every(
      (sku) =>
        integer(sku.id, 1) &&
        integer(sku.stock) &&
        integer(sku.price, 1) &&
        integer(sku.marketPrice) &&
        integer(sku.costPrice) &&
        (sku.barCode === null || typeof sku.barCode === "string") &&
        typeof sku.picUrl === "string" &&
        [sku.weight, sku.volume].every(
          (value) =>
            value === null ||
            (typeof value === "string" &&
              /^\d{1,12}(?:\.\d{1,6})?$/.test(value)),
        ) &&
        integer(sku.authVersion) &&
        [0, 1].includes(sku.status) &&
        Array.isArray(sku.properties) &&
        sku.properties.every(property),
    )
  )
    throw new ApiError("商品详情不完整", -2);
  return result;
}
export async function productStatus(value: ProductRow, status: number) {
  const result = await call<unknown>("spu/update-status", {
    method: "PUT",
    body: JSON.stringify({
      id: value.id,
      expectedVersion: value.authVersion,
      status,
    }),
  });
  if (result !== true)
    throw new ApiError("状态修改结果待确认，请重新读取", -2, true);
}
export async function productDelete(value: ProductRow) {
  const result = await call<unknown>(
    `spu/delete?id=${value.id}&expectedVersion=${value.authVersion}`,
    { method: "DELETE" },
  );
  if (result !== true)
    throw new ApiError("删除结果待确认，请重新读取", -2, true);
}
function validStock(value: StockResult, body: StockRequest) {
  return (
    value &&
    integer(value.id, 1) &&
    value.spuId === body.spuId &&
    value.operationKey === body.operationKey &&
    value.kind === "ADJUST" &&
    Array.isArray(value.items) &&
    value.items.length === body.items.length &&
    new Set(value.items.map((item) => item.skuId)).size ===
      value.items.length &&
    value.items.every(
      (item) =>
        integer(item.skuId, 1) &&
        integer(item.beforeStock) &&
        integer(item.afterStock) &&
        Number.isSafeInteger(item.delta) &&
        item.afterStock - item.beforeStock === item.delta &&
        body.items.some(
          (expected) =>
            expected.skuId === item.skuId && expected.delta === item.delta,
        ),
    )
  );
}
export async function stockAdjust(body: StockRequest) {
  const result = await call<StockResult>("stock/adjust", {
    method: "POST",
    body: JSON.stringify(body),
  });
  if (!validStock(result, body))
    throw new ApiError("库存操作结果不完整，请按原业务键查询", -2, true);
  return result;
}
export async function stockOperation(body: StockRequest) {
  const result = await call<StockResult>(
    `stock/operation?${new URLSearchParams({ spuId: String(body.spuId), operationKey: body.operationKey })}`,
  );
  if (!validStock(result, body))
    throw new ApiError("库存操作结果不完整，请保持原业务键继续查询", -2, true);
  return result;
}

export type EditorOption = {
  id: number;
  name: string;
  parentId?: number;
  status?: number;
  type?: number;
  propertyId?: number;
  chargeMode?: number;
  authVersion?: number;
};
export type ProductEditorOptions = {
  categories: EditorOption[];
  brands: EditorOption[];
  properties: EditorOption[];
  templates: EditorOption[];
  maxSkus: number;
  maxDimensions: number;
};
function option(value: EditorOption) {
  return value && integer(value.id, 1) && typeof value.name === "string";
}
async function dependency<T>(path: string) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口编辑商品", 403);
  const identity = productIdentity();
  const result = await request<T>(path, credentials());
  if (identity !== productIdentity())
    throw new ApiError("登录身份已变化，请重新读取", 409);
  return result;
}
export async function productEditorOptions(): Promise<ProductEditorOptions> {
  const [categories, brands, properties, templates, limits] = await Promise.all(
    [
      call<EditorOption[]>("category/list"),
      call<EditorOption[]>("brand/list-all-simple"),
      call<EditorOption[]>("property/simple-list"),
      dependency<EditorOption[]>(
        "/admin-api/trade/delivery/express-template/list-all-simple",
      ),
      call<{ maxSkus: number; maxDimensions: number }>("spu/editor-options"),
    ],
  );
  if (
    ![categories, brands, properties, templates].every(
      (list) =>
        Array.isArray(list) &&
        list.every(option) &&
        new Set(list.map((value) => value.id)).size === list.length,
    ) ||
    !categories.every(
      (value) => integer(value.parentId) && [0, 1].includes(value.status!),
    ) ||
    !properties.every(
      (value) => [1, 2].includes(value.type!) && [0, 1].includes(value.status!),
    ) ||
    !templates.every((value) => [1, 2].includes(value.chargeMode!)) ||
    !limits ||
    !integer(limits.maxSkus, 1) ||
    limits.maxSkus > 2000 ||
    limits.maxDimensions !== 20
  )
    throw new ApiError("商品编辑选项不完整，请重新读取", -2);
  return {
    categories,
    brands,
    properties,
    templates,
    maxSkus: limits.maxSkus,
    maxDimensions: limits.maxDimensions,
  };
}
export async function productPropertyValues(propertyId: number) {
  if (!integer(propertyId, 1)) throw new ApiError("属性编号不正确", 400);
  const result = await call<EditorOption[]>(
    `property/value/simple-list?propertyId=${propertyId}`,
  );
  if (
    !Array.isArray(result) ||
    !result.every(
      (value) =>
        option(value) &&
        value.propertyId === propertyId &&
        [0, 1].includes(value.status!),
    ) ||
    new Set(result.map((value) => value.id)).size !== result.length
  )
    throw new ApiError("属性值选项不完整", -2);
  return result;
}
export async function productSave(
  body: Record<string, unknown>,
  editing: boolean,
) {
  const result = await call<unknown>(editing ? "spu/update" : "spu/create", {
    method: editing ? "PUT" : "POST",
    body: JSON.stringify(body),
  });
  if (editing ? result !== true : !integer(result, 1))
    throw new ApiError("商品保存结果待确认，请重新读取列表", -2, true);
  return result;
}

export type ProductOwner = {
  tenantId: number;
  ownerUserId: number;
  ownerName: string;
  ownerDeptId: number | null;
  ownerDeptName: string | null;
  userVersion: number;
  deptVersion: number | null;
};
export async function productOwner(userId: number) {
  if (!integer(userId, 1)) throw new ApiError("员工编号不正确", 400);
  const result = await call<ProductOwner>(`spu/owner-option?userId=${userId}`);
  if (
    !result ||
    result.ownerUserId !== userId ||
    !integer(result.tenantId, 1) ||
    result.tenantId !== Number(identities.merchant?.tenantId) ||
    typeof result.ownerName !== "string" ||
    !integer(result.userVersion) ||
    (result.ownerDeptId !== null &&
      (!integer(result.ownerDeptId, 1) ||
        !integer(result.deptVersion) ||
        typeof result.ownerDeptName !== "string")) ||
    (result.ownerDeptId === null && result.deptVersion !== null)
  )
    throw new ApiError("目标员工归属不完整，请重新核对", -2);
  return result;
}
export async function productTransfer(body: {
  id: number;
  expectedVersion: number;
  targetUserId: number;
  expectedOwnerVersion: number;
  reason: string;
}) {
  const result = await call<unknown>("spu/transfer-owner", {
    method: "PUT",
    body: JSON.stringify(body),
  });
  if (result !== true)
    throw new ApiError("归属转移结果待确认，请重新读取", -2, true);
}

/** Fixed same-origin export endpoint; no public URL or cookie authentication. */
export async function productExport(
  tabType: number | undefined,
  name: string,
  signal?: AbortSignal,
) {
  if (demo || context.realm !== "merchant" || !identities.merchant)
    throw new ApiError("请从商家入口导出商品", 403);
  const identity = productIdentity(),
    current = credentials();
  if (
    !current.accessToken ||
    current.accessToken.length > 128 ||
    /[\s,\x00-\x1f\x7f]/.test(current.accessToken)
  )
    throw new ApiError("请重新登录后导出", 401);
  const query = new URLSearchParams({ name });
  if (tabType !== undefined) query.set("tabType", String(tabType));
  const controller = new AbortController(),
    abort = () => controller.abort(),
    timer = setTimeout(abort, 45000);
  signal?.addEventListener("abort", abort, { once: true });
  try {
    if (signal?.aborted) controller.abort();
    const response = await fetch(
      `/api/admin-api/product/spu/export-excel?${query}`,
      {
        headers: {
          Authorization: `Bearer ${current.accessToken}`,
          "tenant-id": String(current.tenantId),
          Accept:
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/json",
        },
        credentials: "omit",
        cache: "no-store",
        redirect: "error",
        signal: controller.signal,
      },
    );
    if (identity !== productIdentity())
      throw new ApiError("登录身份已变化，请重新读取", 409);
    const type = response.headers.get("Content-Type") || "";
    if (type.includes("application/json")) {
      const detail: unknown = await response.json().catch(() => null);
      if (detail && typeof detail === "object") {
        const result = detail as { code?: unknown; msg?: unknown };
        if (
          typeof result.code === "number" &&
          result.code !== 0 &&
          [400, 401, 403, 409, 429].includes(result.code)
        )
          throw new ApiError(
            typeof result.msg === "string" && result.msg.length <= 200
              ? result.msg
              : "商品导出未完成",
            result.code,
          );
      }
      throw new ApiError("商品导出未完成，请稍后重试", response.status || -2);
    }
    if (!response.ok)
      throw new ApiError(
        response.status === 401
          ? "登录已失效，请重新登录"
          : response.status === 403
            ? "没有商品导出权限"
            : response.status === 429
              ? "商品导出繁忙，请稍后重试"
              : "商品导出未完成，请稍后重试",
        response.status,
      );
    if (
      !type.startsWith(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      )
    )
      throw new ApiError("商品导出响应格式不正确", -2);
    const expectedBytes = Number(response.headers.get("Content-Length")),
      rowHeader = response.headers.get("X-Export-Rows"),
      rows = Number(rowHeader),
      id = response.headers.get("X-Export-Id") || "",
      hash = response.headers.get("X-Export-Sha256") || "";
    if (
      !integer(expectedBytes, 1) ||
      expectedBytes > 33554432 ||
      rowHeader === null ||
      !/^\d+$/.test(rowHeader) ||
      !integer(rows) ||
      rows > 20000 ||
      !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(
        id,
      ) ||
      !/^[a-f0-9]{64}$/.test(hash)
    )
      throw new ApiError("商品导出信息不完整", -2);
    if (!response.body) throw new ApiError("商品导出文件为空", -2);
    const reader = response.body.getReader(),
      parts: Uint8Array[] = [];
    let size = 0;
    try {
      while (true) {
        const part = await reader.read();
        if (part.done) break;
        size += part.value.byteLength;
        if (size > expectedBytes || size > 33554432) {
          await reader.cancel();
          throw new ApiError("商品导出文件超过预期大小", -2);
        }
        parts.push(part.value);
      }
    } finally {
      reader.releaseLock();
    }
    if (size !== expectedBytes)
      throw new ApiError("商品导出文件不完整，请重新导出", -2);
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const part of parts) {
      bytes.set(part, offset);
      offset += part.byteLength;
    }
    const actualHash = [
      ...new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
    ]
      .map((value) => value.toString(16).padStart(2, "0"))
      .join("");
    if (actualHash !== hash || bytes[0] !== 0x50 || bytes[1] !== 0x4b)
      throw new ApiError("商品导出文件校验失败，请重新导出", -2);
    if (identity !== productIdentity())
      throw new ApiError("登录身份已变化，已丢弃导出文件", 409);
    return { blob: new Blob([bytes], { type }), rows, id };
  } catch (cause) {
    if (cause instanceof ApiError) throw cause;
    throw new ApiError("导出连接中断或已取消，未保存文件；可重新发起导出", -1);
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", abort);
  }
}
