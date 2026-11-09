import { context } from "../api/session";
import { db, ui, log, assertTrading, assertTenant, can } from "./store";
import {
  assertJoin,
  validateActivity,
  activityState,
  type ActivityInput,
  type SeckillActivity,
  type SeckillRequest,
} from "../seckill";
import { timestamp, type Order } from "../domain";
export function initializeSeckillDemo() {
  if (db.seckillActivities.length) {
    for (const a of db.seckillActivities)
      for (const s of a.skus) {
        s.planned ??= s.allocated;
        if (["草稿", "发布失败"].includes(a.status))
          s.allocated = s.available = 0;
      }
    return;
  }
  for (const r of db.roles.filter((r) => r.code === "tenant_admin"))
    r.permissions.push(
      ...["seckill.view", "seckill.manage", "seckill.publish"].filter(
        (p) => !r.permissions.includes(p),
      ),
    );
  const now = Date.now(),
    iso = (n: number) => new Date(now + n).toISOString();
  const p = db.products.find((p) => p.id === 101)!,
    sku = p.skus![0]!,
    qty = Math.min(10, sku.stock);
  if (!qty) return;
  sku.stock -= qty;
  p.stock -= qty;
  db.seckillActivities.push({
    id: "SK-QH-1001",
    tenant: "qinghe",
    name: "日常好物 · 限量上新",
    productId: p.id,
    productName: p.name,
    image: p.image,
    startsAt: iso(-3600000),
    endsAt: iso(7200000),
    singleLimit: 1,
    memberLimit: 1,
    status: "已发布",
    version: 1,
    skus: [
      {
        spec: sku.spec,
        price: 7900,
        allocated: qty,
        available: qty,
        held: 0,
        sold: 0,
        returned: 0,
      },
    ],
    createdAt: timestamp(),
    reason: "",
  });
  const knit = db.products.find((p) => p.id === 102)!;
  db.seckillActivities.push({
    id: "SK-QH-1002",
    tenant: "qinghe",
    name: "明日衣橱 · 羊毛专场",
    productId: knit.id,
    productName: knit.name,
    image: knit.image,
    startsAt: iso(86400000),
    endsAt: iso(90000000),
    singleLimit: 1,
    memberLimit: 2,
    status: "草稿",
    version: 1,
    skus: [
      {
        spec: knit.skus![0]!.spec,
        price: 19900,
        planned: 5,
        allocated: 0,
        available: 0,
        held: 0,
        sold: 0,
        returned: 0,
      },
    ],
    createdAt: timestamp(),
    reason: "",
  });
}
function poolActivity(id: string) {
  const a = db.seckillActivities.find((a) => a.id === id);
  if (!a) throw new Error("活动不存在");
  if (context.realm !== "platform") assertTenant(a.tenant);
  return a;
}
export function demoSaveActivity(input: ActivityInput, id?: string) {
  assertTrading();
  validateActivity(input, db.seckillActivities, ui.tenant, id);
  const product = db.products.find(
    (p) =>
      p.id === input.productId &&
      p.tenant === ui.tenant &&
      p.status === "上架中",
  );
  if (
    !product ||
    input.skus.some((s) => !product.skus?.some((p) => p.spec === s.spec))
  )
    throw new Error("请选择本店上架商品及有效规格");
  const a = id ? poolActivity(id) : undefined;
  if (a && !["草稿", "发布失败"].includes(a.status))
    throw new Error("请先撤回活动再编辑");
  if (Date.parse(input.startsAt) <= Date.now())
    throw new Error("新活动开始时间应晚于当前时间");
  const next: SeckillActivity = {
    ...input,
    id: a?.id || `SK-${crypto.randomUUID()}`,
    tenant: ui.tenant,
    productName: product.name,
    image: product.image,
    status: "草稿",
    version: (a?.version || 0) + 1,
    createdAt: a?.createdAt || timestamp(),
    reason: "",
    skus: input.skus.map((s) => ({
      ...s,
      planned: s.allocated,
      allocated: 0,
      available: 0,
      held: 0,
      sold: 0,
      returned: 0,
    })),
  };
  if (a) Object.assign(a, next);
  else db.seckillActivities.unshift(next);
  log("保存秒杀活动", next.name);
  return next.id;
}
function releaseAvailable(a: SeckillActivity) {
  const product = db.products.find(
    (p) => p.id === a.productId && p.tenant === a.tenant,
  )!;
  for (const s of a.skus) {
    const sku = product?.skus?.find((x) => x.spec === s.spec);
    if (!sku) throw new Error("原商品规格缺失，请核查库存");
  }
  for (const s of a.skus) {
    const sku = product.skus!.find((x) => x.spec === s.spec)!;
    sku.stock += s.available;
    product.stock += s.available;
    s.returned += s.available;
    s.available = 0;
  }
}
export function demoTransition(id: string, operation: string, reason: string) {
  const a = poolActivity(id);
  if (operation === "publish") {
    assertTrading();
    if (a.status !== "草稿" && a.status !== "发布失败")
      throw new Error("活动状态已变化");
    validateActivity(
      {
        ...a,
        skus: a.skus.map((s) => ({
          ...s,
          allocated: s.planned ?? s.allocated,
        })),
      },
      db.seckillActivities,
      a.tenant,
      a.id,
    );
    if (Date.now() >= Date.parse(a.startsAt))
      throw new Error("开始时间已过，请修改活动时间");
    const p = db.products.find(
      (p) =>
        p.id === a.productId && p.tenant === a.tenant && p.status === "上架中",
    );
    if (
      !p ||
      a.skus.some(
        (s) =>
          (p.skus?.find((x) => x.spec === s.spec)?.stock || 0) <
          (s.planned ?? s.allocated),
      )
    )
      throw new Error("商品已下架或规格库存不足，活动未发布");
    for (const s of a.skus) {
      const sku = p.skus!.find((x) => x.spec === s.spec)!;
      s.allocated = s.planned ?? s.allocated;
      sku.stock -= s.allocated;
      p.stock -= s.allocated;
      s.available = s.allocated;
      s.returned = 0;
    }
    a.status = "已发布";
  } else if (operation === "withdraw") {
    if (activityState(a, Date.now()) !== "待开始")
      throw new Error("只有尚未开始的活动可以撤回");
    releaseAvailable(a);
    a.status = "草稿";
    for (const s of a.skus) {
      s.returned = 0;
      s.available = s.allocated = 0;
    }
  } else if (operation === "stop") {
    if (!reason.trim()) throw new Error("请填写停止原因");
    if (!["已发布", "发布准备中"].includes(a.status))
      throw new Error("活动已停止或结束");
    a.status = "停止接单";
    a.reason = reason.trim();
  } else {
    if (!["停止接单", "结束清算中"].includes(activityState(a, Date.now())))
      throw new Error("活动尚未进入清算阶段");
    if (
      db.seckillRequests.some(
        (r) => r.activityId === id && ["排队中", "待确认"].includes(r.status),
      )
    )
      throw new Error("仍有申请处理中，请先核查");
    if (a.skus.some((s) => s.held > 0))
      throw new Error("仍有待付款订单，暂不能结束清算");
    releaseAvailable(a);
    a.status = "已结束";
  }
  a.version++;
  log(
    operation === "publish"
      ? "发布秒杀活动"
      : operation === "stop"
        ? "停止秒杀活动"
        : operation === "withdraw"
          ? "撤回秒杀活动"
          : "清算秒杀活动",
    a.name,
  );
  return a.id;
}
export function demoJoin(input: {
  activityId: string;
  version: number;
  spec: string;
  quantity: number;
  addressId: number;
  requestKey: string;
}) {
  assertTrading();
  const old = db.seckillRequests.find(
    (r) =>
      r.id === input.requestKey &&
      r.tenant === ui.tenant &&
      r.memberId === ui.memberId,
  );
  if (old) {
    if (
      old.activityId !== input.activityId ||
      old.spec !== input.spec ||
      old.quantity !== input.quantity ||
      old.address.name !==
        db.addresses.find((a) => a.id === input.addressId)?.name ||
      old.address.detail !==
        db.addresses.find((a) => a.id === input.addressId)?.detail
    )
      throw new Error("请勿使用同一请求提交不同商品");
    return old.id;
  }
  const a = poolActivity(input.activityId);
  if (a.version !== input.version) throw new Error("活动已更新，请刷新后重试");
  if (
    !db.products.some(
      (p) =>
        p.id === a.productId && p.status === "上架中" && p.tenant === a.tenant,
    )
  )
    throw new Error("商品已下架");
  const address = db.addresses.find(
    (d) =>
      d.id === input.addressId &&
      d.memberId === ui.memberId &&
      d.tenant === ui.tenant,
  );
  if (!address) throw new Error("请先添加并选择收货地址");
  const used = db.seckillRequests
    .filter(
      (r) =>
        r.activityId === a.id &&
        r.tenant === ui.tenant &&
        r.memberId === ui.memberId &&
        !["失败", "已取消"].includes(r.status),
    )
    .reduce((n, r) => n + r.quantity, 0);
  const sku = assertJoin(a, input.spec, input.quantity, used, Date.now());
  const template = db.shipping.find((s) => s.tenant === ui.tenant && s.enabled);
  if (!template) throw new Error("店铺暂不支持配送");
  const subtotal = sku.price * input.quantity,
    shipping =
      template.freeAbove > 0 && subtotal >= template.freeAbove
        ? 0
        : template.first +
          Math.max(0, input.quantity - 1) * template.additional;
  const r: SeckillRequest = {
    id: input.requestKey,
    tenant: ui.tenant,
    memberId: ui.memberId,
    activityId: a.id,
    activityVersion: a.version,
    productId: a.productId,
    spec: input.spec,
    quantity: input.quantity,
    price: sku.price,
    shipping,
    total: subtotal + shipping,
    address: {
      name: address.name,
      phone: address.phone,
      region: address.region,
      detail: address.detail,
    },
    status: "排队中",
    reason: "",
    orderId: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  sku.available -= input.quantity;
  sku.held += input.quantity;
  db.seckillRequests.unshift(r);
  return r.id;
}
export function demoRequest(id: string, platform = false) {
  const r = db.seckillRequests.find(
    (r) =>
      r.id === id &&
      (platform || r.tenant === ui.tenant) &&
      (can("seckill.view") || platform || r.memberId === ui.memberId),
  );
  if (!r) throw new Error("抢购记录不存在");
  const a = db.seckillActivities.find(
    (a) => a.id === r.activityId && a.tenant === r.tenant,
  )!;
  if (r.status === "排队中" && Date.now() - Date.parse(r.createdAt) >= 1500) {
    const sku = a.skus.find((s) => s.spec === r.spec)!;
    const tenant = db.tenants.find((t) => t.code === r.tenant);
    const canContinue =
      tenant?.status === "正常" &&
      tenant.expire >= new Date().toLocaleDateString("sv-SE") &&
      db.products.some(
        (p) =>
          p.id === r.productId &&
          p.tenant === r.tenant &&
          p.status === "上架中",
      );
    if (
      (activityState(a, Date.now()) !== "进行中" &&
        activityState(a, Date.now()) !== "已抢完") ||
      a.version !== r.activityVersion ||
      !canContinue
    ) {
      r.status = "失败";
      r.reason = "活动已停止接单，未生成订单";
      sku.held -= r.quantity;
      if (a.status === "已发布" && Date.now() < Date.parse(a.endsAt))
        sku.available += r.quantity;
      else {
        const p = db.products.find(
          (p) => p.id === a.productId && p.tenant === a.tenant,
        )!;
        p.stock += r.quantity;
        p.skus!.find((s) => s.spec === r.spec)!.stock += r.quantity;
        sku.returned += r.quantity;
      }
    } else {
      const id = `SKO-${crypto.randomUUID()}`;
      const order: Order = {
        id,
        tenant: r.tenant,
        memberId: r.memberId,
        buyer: r.address.name,
        phone: r.address.phone,
        address: `${r.address.region} ${r.address.detail}`,
        items: [
          {
            productId: r.productId,
            name: a.productName,
            image: a.image,
            spec: r.spec,
            price: r.price,
            quantity: r.quantity,
            discount: 0,
            refunded: 0,
            returnedQuantity: 0,
          },
        ],
        total: r.total,
        shipping: r.shipping,
        coupon: null,
        status: "待付款",
        created: timestamp(),
        carrier: "",
        tracking: "",
        history: ["秒杀订单已创建"],
        seckillRequestId: r.id,
      };
      db.orders.unshift(order);
      r.orderId = id;
      r.status = "订单已创建";
    }
    r.updatedAt = new Date().toISOString();
  }
  return structuredClone(JSON.parse(JSON.stringify(r))) as SeckillRequest;
}
export function demoSeckillPaid(id: string) {
  const r = db.seckillRequests.find((r) => r.id === id)!;
  const a = db.seckillActivities.find((a) => a.id === r.activityId)!;
  const sku = a.skus.find((s) => s.spec === r.spec)!;
  sku.held -= r.quantity;
  sku.sold += r.quantity;
}
export function demoSeckillCancelled(id: string) {
  const r = db.seckillRequests.find((r) => r.id === id)!;
  const a = db.seckillActivities.find((a) => a.id === r.activityId)!;
  const sku = a.skus.find((s) => s.spec === r.spec)!;
  if (r.status === "已取消") return;
  sku.held -= r.quantity;
  r.status = "已取消";
  r.reason = "未支付订单已取消";
  if (a.status === "已发布" && Date.now() < Date.parse(a.endsAt))
    sku.available += r.quantity;
  else {
    const p = db.products.find(
      (p) => p.id === a.productId && p.tenant === a.tenant,
    )!;
    p.stock += r.quantity;
    p.skus!.find((s) => s.spec === r.spec)!.stock += r.quantity;
    sku.returned += r.quantity;
  }
}
