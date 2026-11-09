export type ActivityStatus =
  | "草稿"
  | "发布准备中"
  | "已发布"
  | "停止接单"
  | "结束清算中"
  | "已结束"
  | "发布失败";
export type SeckillSku = {
  spec: string;
  price: number;
  planned?: number;
  allocated: number;
  available: number;
  held: number;
  sold: number;
  returned: number;
};
export type SeckillActivity = {
  id: string;
  tenant: string;
  name: string;
  productId: number;
  productName: string;
  image: string;
  startsAt: string;
  endsAt: string;
  singleLimit: number;
  memberLimit: number;
  status: ActivityStatus;
  version: number;
  skus: SeckillSku[];
  createdAt: string;
  reason: string;
};
export type SeckillRequest = {
  id: string;
  tenant: string;
  memberId: number;
  activityId: string;
  activityVersion: number;
  productId: number;
  spec: string;
  quantity: number;
  price: number;
  shipping: number;
  total: number;
  address: { name: string; phone: string; region: string; detail: string };
  status: "排队中" | "订单已创建" | "失败" | "已取消" | "待确认";
  reason: string;
  orderId: string | null;
  createdAt: string;
  updatedAt: string;
};
export type ActivityInput = Pick<
  SeckillActivity,
  "name" | "productId" | "startsAt" | "endsAt" | "singleLimit" | "memberLimit"
> & { skus: Pick<SeckillSku, "spec" | "price" | "allocated">[] };
export type SeckillQuote = {
  token: string;
  subtotal: number;
  shipping: number;
  total: number;
  expiresAt: number;
};
export const instant = (value: string) => Date.parse(value);
export function activityState(a: SeckillActivity, now: number) {
  if (!Number.isFinite(now)) return "正在校时";
  if (a.status !== "已发布") return a.status;
  if (now < instant(a.startsAt)) return "待开始";
  if (now >= instant(a.endsAt)) return "结束清算中";
  if (a.skus.every((s) => s.available === 0)) return "已抢完";
  return "进行中";
}
export function validateActivity(
  input: ActivityInput,
  other: SeckillActivity[],
  tenant: string,
  id?: string,
) {
  if (
    !input.name.trim() ||
    input.name.length > 60 ||
    !Number.isSafeInteger(input.productId)
  )
    throw new Error("请填写活动名称并选择商品");
  if (
    !Number.isFinite(instant(input.startsAt)) ||
    !Number.isFinite(instant(input.endsAt)) ||
    instant(input.startsAt) >= instant(input.endsAt)
  )
    throw new Error("结束时间必须晚于开始时间");
  if (
    ![input.singleLimit, input.memberLimit].every(
      (n) => Number.isSafeInteger(n) && n > 0,
    ) ||
    input.singleLimit > input.memberLimit
  )
    throw new Error("每单限购应不大于每人累计限购，且均为正整数");
  if (
    !input.skus.length ||
    new Set(input.skus.map((s) => s.spec)).size !== input.skus.length ||
    input.skus.some(
      (s) =>
        !s.spec ||
        !Number.isSafeInteger(s.price) ||
        s.price <= 0 ||
        !Number.isSafeInteger(s.allocated) ||
        s.allocated < 1,
    )
  )
    throw new Error("请为活动规格设置有效价格和正整数库存");
  if (
    other.some(
      (a) =>
        a.id !== id &&
        a.tenant === tenant &&
        a.productId === input.productId &&
        !["草稿", "发布失败", "已结束"].includes(a.status) &&
        instant(input.startsAt) < instant(a.endsAt) &&
        instant(input.endsAt) > instant(a.startsAt) &&
        a.skus.some((s) => input.skus.some((x) => x.spec === s.spec)),
    )
  )
    throw new Error("该规格已有时间重叠的活动");
}
export function assertPool(s: SeckillSku) {
  if (
    ![s.allocated, s.available, s.held, s.sold, s.returned].every(
      (n) => Number.isSafeInteger(n) && n >= 0,
    ) ||
    s.allocated !== s.available + s.held + s.sold + s.returned
  )
    throw new Error("活动库存记录异常，请联系店铺");
}
export function assertJoin(
  a: SeckillActivity,
  spec: string,
  quantity: number,
  used: number,
  now: number,
) {
  if (activityState(a, now) !== "进行中")
    throw new Error(
      activityState(a, now) === "待开始" ? "活动尚未开始" : "活动暂不可抢购",
    );
  const sku = a.skus.find((s) => s.spec === spec);
  if (!sku) throw new Error("请选择活动规格");
  assertPool(sku);
  if (
    !Number.isSafeInteger(quantity) ||
    quantity < 1 ||
    quantity > a.singleLimit
  )
    throw new Error(`每单最多购买${a.singleLimit}件`);
  if (used + quantity > a.memberLimit)
    throw new Error("已达到本场累计限购数量");
  if (quantity > sku.available) throw new Error("该规格已抢完");
  return sku;
}
export function assertSeckillProjection(
  activities: unknown,
  requests: unknown,
  tenant: string,
  memberId: number,
  platform: boolean,
  member: boolean,
): void {
  if (!Array.isArray(activities) || !Array.isArray(requests))
    throw new Error("活动响应异常");
  for (const a of activities as SeckillActivity[]) {
    if (
      !a ||
      typeof a.id !== "string" ||
      !a.id ||
      typeof a.tenant !== "string" ||
      !a.tenant ||
      (!platform && a.tenant !== tenant) ||
      !Array.isArray(a.skus) ||
      !a.skus.length ||
      typeof a.name !== "string" ||
      typeof a.productName !== "string" ||
      ![a.productId, a.version, a.singleLimit, a.memberLimit].every(
        (n) => Number.isSafeInteger(n) && n > 0,
      ) ||
      a.singleLimit > a.memberLimit ||
      !Number.isFinite(instant(a.startsAt)) ||
      !Number.isFinite(instant(a.endsAt)) ||
      instant(a.startsAt) >= instant(a.endsAt) ||
      ![
        "草稿",
        "发布准备中",
        "已发布",
        "停止接单",
        "结束清算中",
        "已结束",
        "发布失败",
      ].includes(a.status)
    )
      throw new Error("活动响应异常");
    if (member && ["草稿", "发布准备中", "发布失败"].includes(a.status))
      throw new Error("活动响应异常");
    for (const sku of a.skus) {
      assertPool(sku);
      if (!Number.isSafeInteger(sku.price) || sku.price <= 0)
        throw new Error("活动响应异常");
    }
  }
  for (const r of requests as SeckillRequest[]) {
    if (
      !r ||
      typeof r.id !== "string" ||
      !r.id ||
      (!platform && r.tenant !== tenant) ||
      (member && (!memberId || r.memberId !== memberId)) ||
      ![r.memberId, r.productId, r.quantity, r.price, r.activityVersion].every(
        (n) => Number.isSafeInteger(n) && n > 0,
      ) ||
      ![r.shipping, r.total].every((n) => Number.isSafeInteger(n) && n >= 0) ||
      r.total !== r.price * r.quantity + r.shipping ||
      typeof r.spec !== "string" ||
      !r.spec ||
      typeof r.activityId !== "string" ||
      !r.activityId ||
      !r.address ||
      ![
        r.address.name,
        r.address.phone,
        r.address.region,
        r.address.detail,
      ].every((s) => typeof s === "string") ||
      !Number.isFinite(instant(r.createdAt)) ||
      !Number.isFinite(instant(r.updatedAt)) ||
      typeof r.reason !== "string" ||
      (r.orderId !== null && typeof r.orderId !== "string") ||
      (r.status === "订单已创建" && !r.orderId) ||
      !["排队中", "订单已创建", "失败", "已取消", "待确认"].includes(r.status)
    )
      throw new Error("抢购记录响应异常");
  }
}
