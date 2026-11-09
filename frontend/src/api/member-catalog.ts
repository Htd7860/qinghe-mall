import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";
import { memberIdentity } from "./member-profile";
import type { SkuProperty } from "./product";

export type CatalogProduct = {
  id: number;
  name: string;
  introduction: string;
  categoryId: number;
  categoryName: string;
  brandId: number;
  brandName: string;
  picUrl: string;
  specType: boolean;
  price: number;
  marketPrice: number;
  stock: number;
  salesCount: number;
  canPurchase: boolean;
};
export type CatalogSku = {
  id: number;
  properties: SkuProperty[];
  price: number;
  marketPrice: number;
  picUrl: string;
  stock: number;
};
export type CatalogDetail = CatalogProduct & {
  description: string;
  sliderPicUrls: string[];
  displayProperties: SkuProperty[];
  skus: CatalogSku[];
};
export type CatalogOptions = {
  categories: { id: number; parentId: number; name: string; picUrl: string }[];
  brands: { id: number; name: string; picUrl: string }[];
};
export type CatalogQuery = {
  pageNo: number;
  keyword: string;
  categoryId: number | null;
  brandId: number | null;
  minPrice: number | null;
  maxPrice: number | null;
  sort: "default" | "priceAsc" | "priceDesc" | "salesDesc" | "newest";
};
export type CatalogCartAdd = {
  spuId: number;
  skuId: number;
  count: number;
  expectedVersion: number;
};
const integer = (
  v: unknown,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v >= min && v <= max;
const text = (v: unknown, max: number, blank = false): v is string =>
  typeof v === "string" && (blank || !!v.trim()) && v.length <= max;
async function browse(path: string) {
  if (demo || context.realm !== "member")
    throw new ApiError("请从商城入口浏览", 403);
  if (identities.member && identities.member.tenantCode !== context.tenant)
    throw new ApiError("当前身份不属于此店铺", 403);
  const actor = memberIdentity(),
    token = credentials().accessToken,
    auth = identities.member ? credentials() : {};
  if (identities.member && !token) throw new ApiError("请重新登录", 401);
  const value = await request<unknown>(
    `/app-api/product/${path}${path.includes("?") ? "&" : "?"}store=${encodeURIComponent(context.tenant)}`,
    auth,
  );
  if (actor !== memberIdentity() || token !== credentials().accessToken)
    throw new ApiError("店铺或登录身份已变化，请重新读取", 409);
  return value;
}
function product(value: unknown): value is CatalogProduct {
  if (!value || typeof value !== "object") return false;
  const p = value as CatalogProduct;
  return (
    integer(p.id, 1) &&
    text(p.name, 128) &&
    text(p.introduction, 512, true) &&
    integer(p.categoryId, 1) &&
    text(p.categoryName, 100) &&
    integer(p.brandId, 1) &&
    text(p.brandName, 100) &&
    text(p.picUrl, 1024) &&
    typeof p.specType === "boolean" &&
    integer(p.price, 1, 2000000000) &&
    integer(p.marketPrice, 0, 2000000000) &&
    integer(p.stock, 0, 2147483647) &&
    integer(p.salesCount, 0, 2147483647) &&
    typeof p.canPurchase === "boolean" &&
    (!p.canPurchase || p.stock > 0)
  );
}
function properties(
  value: unknown,
  max: number,
  allowDefault: boolean,
): value is SkuProperty[] {
  if (!Array.isArray(value) || value.length > max) return false;
  return value.every(
    (p: SkuProperty) =>
      !!p &&
      text(p.propertyName, 100) &&
      text(p.valueName, 100) &&
      ((integer(p.propertyId, 1) && integer(p.valueId, 1)) ||
        (allowDefault &&
          value.length === 1 &&
          p.propertyId === 0 &&
          p.valueId === 0)),
  );
}
export async function catalogPage(query: CatalogQuery) {
  return catalogListing(query, false);
}
/** Keyword search is explicit; a rejected/unavailable search never falls back to the catalog. */
export async function catalogSearchPage(query: CatalogQuery) {
  return catalogListing(query, true);
}
async function catalogListing(query: CatalogQuery, search: boolean) {
  if (
    !integer(query.pageNo, 1, search ? 500 : 10000) ||
    !text(query.keyword, 64, true) ||
    /[\x00-\x1f\x7f]/.test(query.keyword) ||
    [query.categoryId, query.brandId].some(
      (x) => x !== null && !integer(x, 1),
    ) ||
    [query.minPrice, query.maxPrice].some(
      (x) => x !== null && !integer(x, 0, 2000000000),
    ) ||
    (query.minPrice !== null &&
      query.maxPrice !== null &&
      query.minPrice > query.maxPrice) ||
    !["default", "priceAsc", "priceDesc", "salesDesc", "newest"].includes(
      query.sort,
    )
  )
    throw new ApiError("商品筛选条件不正确", 400);
  const params = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: "20",
  });
  if (query.keyword.trim()) params.set("keyword", query.keyword.trim());
  for (const k of ["categoryId", "brandId", "minPrice", "maxPrice"] as const)
    if (query[k] !== null) params.set(k, String(query[k]));
  if (query.sort !== "default") {
    params.set(
      "sortField",
      query.sort.startsWith("price")
        ? "price"
        : query.sort === "salesDesc"
          ? "salesCount"
          : "createTime",
    );
    params.set("sortAsc", String(query.sort === "priceAsc"));
  }
  let result: unknown;
  try {
    result = await browse(`spu/${search ? "search-page" : "page"}?${params}`);
  } catch (e) {
    if (search && e instanceof ApiError && e.code === 503)
      throw new ApiError(
        "搜索暂不可用或商品资料更新中，请稍后重试，也可以清除关键词继续浏览。",
        e.code,
        false,
        e.requestId,
      );
    throw e;
  }
  const p = result as PageResult<CatalogProduct>;
  if (
    !p ||
    !integer(p.total, 0, 2147483647) ||
    !Array.isArray(p.list) ||
    p.list.length > 20 ||
    p.list.length > p.total ||
    p.list.length !==
      Math.min(20, Math.max(0, p.total - (query.pageNo - 1) * 20)) ||
    !p.list.every(product) ||
    new Set(p.list.map((x) => x.id)).size !== p.list.length
  )
    throw new ApiError("商品列表不完整，请重新读取", -2);
  return p;
}
export async function catalogDetail(id: number) {
  if (!integer(id, 1)) throw new ApiError("商品编号不正确", 400);
  const value = await browse(`spu/get-detail?id=${id}`),
    p = value as CatalogDetail;
  if (
    !product(p) ||
    p.id !== id ||
    !text(p.description, 200000, true) ||
    !Array.isArray(p.sliderPicUrls) ||
    p.sliderPicUrls.length < 1 ||
    p.sliderPicUrls.length > 20 ||
    !p.sliderPicUrls.every((x) => text(x, 1024)) ||
    !properties(p.displayProperties, 100, false) ||
    !Array.isArray(p.skus) ||
    p.skus.length < 1 ||
    p.skus.length > 2000 ||
    (!p.specType && p.skus.length !== 1) ||
    new Set(p.skus.map((s) => s?.id)).size !== p.skus.length ||
    !p.skus.every(
      (s) =>
        !!s &&
        integer(s.id, 1) &&
        properties(s.properties, 20, !p.specType) &&
        (!p.specType || s.properties.length > 0) &&
        integer(s.price, 1, 2000000000) &&
        integer(s.marketPrice, 0, 2000000000) &&
        text(s.picUrl, 1024) &&
        integer(s.stock, 0, 2147483647),
    )
  )
    throw new ApiError("商品详情不完整，请重新读取", -2);
  return p;
}
export async function catalogOptions() {
  const value = await browse("catalog/options"),
    p = value as CatalogOptions;
  if (
    !p ||
    !Array.isArray(p.categories) ||
    p.categories.length > 2000 ||
    !Array.isArray(p.brands) ||
    p.brands.length > 500 ||
    !p.categories.every(
      (c) =>
        !!c &&
        integer(c.id, 1) &&
        integer(c.parentId) &&
        text(c.name, 100) &&
        text(c.picUrl, 1024),
    ) ||
    !p.brands.every(
      (b) =>
        !!b &&
        integer(b.id, 1) &&
        text(b.name, 100) &&
        text(b.picUrl, 1024, true),
    ) ||
    new Set(p.categories.map((c) => c.id)).size !== p.categories.length ||
    new Set(p.brands.map((b) => b.id)).size !== p.brands.length
  )
    throw new ApiError("商品筛选项不完整", -2);
  const parents = new Map(p.categories.map((c) => [c.id, c.parentId]));
  for (const c of p.categories) {
    const seen = new Set<number>();
    let id: number | undefined = c.id;
    while (id !== 0) {
      if (id === undefined || seen.has(id) || seen.size >= 3)
        throw new ApiError("商品分类层级不完整", -2);
      seen.add(id);
      id = parents.get(id);
    }
  }
  return p;
}
/** Image display only. No token or authenticated fetch is attached to product pictures. */
export function catalogImage(value: string) {
  if (!value || /[\\\x00-\x20\x7f]/.test(value) || value.startsWith("//"))
    return "";
  try {
    const url = new URL(value, location.origin);
    if (
      url.username ||
      url.password ||
      !["http:", "https:"].includes(url.protocol)
    )
      return "";
    return value.startsWith("/") || /^https?:\/\//i.test(value) ? value : "";
  } catch {
    return "";
  }
}
function pendingKey() {
  if (
    demo ||
    context.realm !== "member" ||
    !identities.member ||
    identities.member.tenantCode !== context.tenant
  )
    throw new ApiError("请先登录当前商城", 401);
  return `qinghe:catalog-cart-add:v1:${memberIdentity()}`;
}
function validAdd(value: unknown): value is CatalogCartAdd {
  if (!value || typeof value !== "object") return false;
  const p = value as CatalogCartAdd;
  return (
    integer(p.spuId, 1) &&
    integer(p.skuId, 1) &&
    integer(p.count, 1, 9999) &&
    integer(p.expectedVersion, 0, Number.MAX_SAFE_INTEGER - 1)
  );
}
export function pendingCatalogAdd(): CatalogCartAdd | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(pendingKey());
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError("无法读取上次添加记录，请先核对购物车", -2);
  }
  if (raw === null) return null;
  let value: unknown;
  try {
    value = raw.length <= 1000 ? JSON.parse(raw) : null;
  } catch {
    value = null;
  }
  if (!validAdd(value))
    throw new ApiError("上次添加记录不完整，请保留并核对购物车", -2);
  return value;
}
export function saveCatalogAdd(value: CatalogCartAdd) {
  if (!validAdd(value)) throw new ApiError("添加参数不正确", 400);
  if (pendingCatalogAdd()) throw new ApiError("请先核对上次添加结果", 409);
  try {
    sessionStorage.setItem(pendingKey(), JSON.stringify(value));
  } catch {
    throw new ApiError("无法保存添加记录，本次尚未发送", -2);
  }
}
export function clearCatalogAdd(value: CatalogCartAdd) {
  const old = pendingCatalogAdd();
  if (!old || JSON.stringify(old) !== JSON.stringify(value))
    throw new ApiError("上次添加记录已变化，请重新读取", 409);
  try {
    sessionStorage.removeItem(pendingKey());
  } catch {
    throw new ApiError("无法清理添加记录，请先核对购物车", -2);
  }
}
