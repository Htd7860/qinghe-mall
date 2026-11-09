export type Realm = "merchant" | "platform" | "member";
export type Identity = {
  realm: Realm;
  userId: number;
  name: string;
  tenantId: string;
  tenantCode: string;
  role: string;
  permissions: string[];
  expiresAt: number;
};
export function platformHome(permissions: string[]) {
  for (const [permission, path] of [
    ["system:tenant:query", "/platform/tenants"],
    ["system:tenant-package:query", "/platform/packages"],
    ["system:user:query", "/platform/staff"],
    ["system:role:query", "/platform/roles"],
    ["system:dept:query", "/platform/departments"],
    ["system:menu:query", "/platform/menus"],
    ["promotion:seckill-operation:query", "/platform/seckill"],
  ])
    if (permissions.includes(permission!)) return path!;
  return "/platform/tenants";
}
export function merchantHome(permissions: string[]) {
  for (const [permission, path] of [
    ["system:user:query", "/admin/staff"],
    ["system:role:query", "/admin/roles"],
    ["system:dept:query", "/admin/departments"],
    ["product:spu:query", "/admin/products"],
    ["trade:order:query", "/admin/orders"],
    ["trade:after-sale:query", "/admin/after-sales"],
    ["product:category:query", "/admin/catalog/categories"],
    ["product:brand:query", "/admin/catalog/brands"],
    ["product:property:query", "/admin/catalog/properties"],
    ["trade:delivery:express-template:query", "/admin/shipping/templates"],
    ["order", "/admin/overview"],
    ["product", "/admin/products"],
    ["member", "/admin/members"],
    ["marketing", "/admin/coupons"],
    ["staff", "/admin/staff"],
    ["settings", "/admin/settings"],
    ["promotion:coupon-template:query", "/admin/coupons"],
    ["promotion:seckill-activity:query", "/admin/seckill"],
  ])
    if (permissions.includes(permission!)) return path!;
  return "/admin/overview";
}
export function validIdentity(
  value: unknown,
  realm: Realm,
  tenant?: string,
): value is Identity {
  if (!value || typeof value !== "object") return false;
  const s = value as Identity;
  return (
    s.realm === realm &&
    Number.isSafeInteger(s.userId) &&
    s.userId > 0 &&
    typeof s.name === "string" &&
    typeof s.role === "string" &&
    typeof s.tenantId === "string" &&
    typeof s.tenantCode === "string" &&
    (realm === "platform" || (!!s.tenantId && !!s.tenantCode)) &&
    (!tenant || s.tenantCode === tenant) &&
    Array.isArray(s.permissions) &&
    s.permissions.every((p) => typeof p === "string") &&
    Number.isFinite(s.expiresAt) &&
    s.expiresAt > Date.now()
  );
}
export function safeReturn(value: unknown, realm: Realm, tenant = ""): string {
  const fallback =
    realm === "platform"
      ? "/platform/tenants"
      : realm === "merchant"
        ? "/admin/overview"
        : `/shop/${encodeURIComponent(tenant)}/account`;
  if (typeof value !== "string" || /[\\\r\n]/.test(value)) return fallback;
  const prefix =
    realm === "platform"
      ? "/platform/"
      : realm === "merchant"
        ? "/admin/"
        : `/shop/${encodeURIComponent(tenant)}/`;
  if (
    /(?:^|\/)\.{1,2}(?:\/|$)|%(?:2e|2f|5c)/i.test(value.split("?")[0] || "") ||
    !value.startsWith(prefix) ||
    value.split("?")[0]?.endsWith("/login")
  )
    return fallback;
  return value;
}
export function hasPermission(
  identity: Identity | null,
  realm: Realm,
  permission: string,
  tenant?: string,
) {
  return (
    !!identity &&
    validIdentity(identity, realm, tenant) &&
    identity.permissions.includes(permission)
  );
}
export function safePaymentUrl(
  value: string,
  origin: string,
  allowedOrigins: string[] = [],
): string {
  const url = new URL(value, origin);
  if (
    url.username ||
    url.password ||
    (url.protocol !== "https:" && url.origin !== origin) ||
    ![origin, ...allowedOrigins].includes(url.origin)
  )
    throw new Error("支付地址暂不可用，请联系店铺");
  return url.href;
}
