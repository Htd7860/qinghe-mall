import { request, ApiError } from "./http";
/** Target lookup only. Identity still requires real member authentication; no token is sent here. */
export type Storefront = {
  tenantId: number;
  tenantCode: string;
  name: string;
  canPurchase: boolean;
  tenantVersion: number;
  entryVersion: number | null;
  bindingVersion: number | null;
  localCodeLookup: boolean;
};
export async function resolveStorefront(store?: string) {
  if (store !== undefined && !/^[a-z0-9-]{3,32}$/.test(store))
    throw new ApiError("店铺编码格式不正确", 400);
  const result = await request<Storefront>(
    `/app-api/storefront/resolve${store ? `?store=${encodeURIComponent(store)}` : ""}`,
  );
  const version = (value: unknown) =>
    typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
  if (
    !result ||
    !Number.isSafeInteger(result.tenantId) ||
    result.tenantId <= 0 ||
    !/^[a-z0-9-]{3,32}$/.test(result.tenantCode) ||
    typeof result.name !== "string" ||
    typeof result.canPurchase !== "boolean" ||
    !version(result.tenantVersion) ||
    typeof result.localCodeLookup !== "boolean" ||
    (result.localCodeLookup
      ? result.entryVersion !== null || result.bindingVersion !== null
      : !version(result.entryVersion) || !version(result.bindingVersion)) ||
    (store !== undefined && result.tenantCode !== store)
  )
    throw new ApiError("商城入口信息不完整或与当前店铺不匹配", -2);
  return result;
}
