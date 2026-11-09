import type { Realm } from "./policy.ts";
const privateFields = [
  "orders",
  "members",
  "addresses",
  "ownedCoupons",
  "notifications",
  "cart",
  "afterSales",
  "seckillRequests",
];
const employeeFields = [
  "staff",
  "roles",
  "audit",
  "inventory",
  "jobs",
  "campaigns",
  "packages",
];
export function assertViewScope(
  value: unknown,
  realm: Realm,
  tenant: string,
  memberId: number,
): asserts value is { tenantCode: string; state: Record<string, unknown> } {
  if (!value || typeof value !== "object")
    throw new Error("页面数据异常，请重试");
  const view = value as { tenantCode: string; state: Record<string, unknown> };
  if (
    !view.state ||
    typeof view.state !== "object" ||
    Array.isArray(view.state) ||
    (realm !== "platform" && view.tenantCode !== tenant)
  )
    throw new Error("页面数据异常，请重试");
  for (const [key, data] of Object.entries(view.state)) {
    if (key === "settings") {
      if (
        !data ||
        typeof data !== "object" ||
        Array.isArray(data) ||
        (realm !== "platform" && Object.keys(data).some((k) => k !== tenant))
      )
        throw new Error("页面数据异常，请重试");
      continue;
    }
    if (!Array.isArray(data)) throw new Error("页面数据异常，请重试");
    for (const row of data) {
      if (
        !row ||
        typeof row !== "object" ||
        Array.isArray(row) ||
        (realm !== "platform" &&
          (("tenant" in row && row.tenant !== tenant) ||
            (key === "tenants" && row.code !== tenant)))
      )
        throw new Error("页面数据异常，请重试");
      if (realm === "member") {
        if (
          employeeFields.includes(key) ||
          (!memberId && privateFields.includes(key)) ||
          (key === "members" && row.id !== memberId) ||
          ("memberId" in row && row.memberId !== memberId)
        )
          throw new Error("页面数据异常，请重试");
        if (
          key === "afterSales" &&
          !(
            view.state.orders as { id: string; memberId: number }[] | undefined
          )?.some((o) => o.id === row.orderId && o.memberId === memberId)
        )
          throw new Error("页面数据异常，请重试");
      }
    }
  }
  const detectSecret = (node: unknown): boolean =>
    !!node &&
    typeof node === "object" &&
    Object.entries(node).some(
      ([key, nested]) =>
        /password|secret|accessToken|refreshToken|authorization/i.test(key) ||
        detectSecret(nested),
    );
  if (detectSecret(view.state)) throw new Error("页面数据异常，请重试");
}

export const actionPermissions: Record<string, string> = {
  "seckill.save": "seckill.manage",
  "seckill.publish": "seckill.publish",
  "seckill.withdraw": "seckill.publish",
  "seckill.stop": "seckill.publish",
  "seckill.reconcile": "seckill.publish",
  "product.save": "product",
  "product.status": "product",
  "product.takeDown": "product",
  "inventory.adjust": "product",
  "order.ship": "order",
  "aftersale.review": "order",
  "aftersale.inspect": "order",
  "aftersale.refund": "refund",
  "member.tags": "member",
  "coupon.create": "marketing",
  "coupon.status": "marketing",
  "campaign.create": "marketing",
  "campaign.execute": "marketing",
  "campaign.stop": "marketing",
  "store.save": "settings",
  "shipping.create": "settings",
  "shipping.status": "settings",
  "operation.retry": "settings",
  "staff.create": "staff",
  "staff.status": "staff",
  "role.status": "staff",
  "role.permissions": "staff",
};
export const memberActions = new Set([
  "seckill.join",
  "cart.add",
  "cart.quantity",
  "cart.remove",
  "cart.select",
  "coupon.claim",
  "order.create",
  "order.cancel",
  "order.receive",
  "payment.start",
  "aftersale.apply",
  "aftersale.returnShipment",
  "aftersale.cancel",
  "address.save",
  "address.delete",
  "member.preferences",
]);
export const platformPermissions: Record<string, string> = {
  "seckill.platformStop": "seckill",
  "tenant.create": "tenants",
  "tenant.update": "tenants",
  "tenant.status": "tenants",
  "package.status": "packages",
};
export function permitsAction(
  realm: Realm,
  action: string,
  permissions: string[],
) {
  if (realm === "member") return memberActions.has(action);
  const required = (
    realm === "platform" ? platformPermissions : actionPermissions
  )[action];
  return !!required && permissions.includes(required);
}
