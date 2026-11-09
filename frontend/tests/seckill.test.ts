import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "vite";
import {
  activityState,
  assertJoin,
  assertPool,
  assertSeckillProjection,
  validateActivity,
  type SeckillActivity,
  type SeckillRequest,
} from "../src/seckill.ts";
const startsAt = "2026-10-20T10:00:00+08:00",
  endsAt = "2026-10-20T11:00:00+08:00";
const a: SeckillActivity = {
  id: "a",
  tenant: "qinghe",
  name: "限量上新",
  productId: 101,
  productName: "新品",
  image: "",
  startsAt,
  endsAt,
  singleLimit: 1,
  memberLimit: 2,
  status: "已发布",
  version: 1,
  skus: [
    {
      spec: "M",
      price: 7900,
      allocated: 3,
      available: 3,
      held: 0,
      sold: 0,
      returned: 0,
    },
  ],
  createdAt: startsAt,
  reason: "",
};
test("seckill uses the half-open server-time window and distinguishes sold out", () => {
  assert.equal(activityState(a, Date.parse(startsAt) - 1), "待开始");
  assert.equal(activityState(a, Date.parse(startsAt)), "进行中");
  assert.equal(activityState(a, Date.parse(endsAt)), "结束清算中");
  assert.equal(
    activityState(
      { ...a, skus: [{ ...a.skus[0]!, available: 0, held: 3 }] },
      Date.parse(startsAt),
    ),
    "已抢完",
  );
  assert.throws(() => assertJoin(a, "M", 1, 0, Date.parse(startsAt) - 1));
  assert.throws(() => assertJoin(a, "M", 1, 0, Date.parse(endsAt)));
});
test("seckill validates overlap within a tenant, limits and balanced stock", () => {
  validateActivity(
    { ...a, startsAt: endsAt, endsAt: "2026-10-20T12:00:00+08:00" },
    [a],
    "qinghe",
  );
  validateActivity(a, [a], "shanye");
  assert.throws(() => validateActivity(a, [a], "qinghe"), /重叠/);
  assert.throws(
    () => validateActivity({ ...a, singleLimit: 3 }, [], "qinghe"),
    /限购/,
  );
  assert.throws(
    () => assertJoin(a, "M", 1, 2, Date.parse(startsAt)),
    /累计限购/,
  );
  assert.throws(() => assertJoin(a, "M", 1.5, 0, Date.parse(startsAt)), /最多/);
  assert.throws(() => assertPool({ ...a.skus[0]!, held: 1 }), /库存/);
  assert.throws(
    () => assertPool({ ...a.skus[0]!, available: -1, held: 4 }),
    /库存/,
  );
});
test("seckill rejects another member, another tenant, malformed money and missing order binding", () => {
  const r: SeckillRequest = {
    id: "request",
    tenant: "qinghe",
    memberId: 1,
    activityId: "a",
    activityVersion: 1,
    productId: 101,
    spec: "M",
    quantity: 1,
    price: 7900,
    shipping: 800,
    total: 8700,
    address: {
      name: "客户",
      phone: "13800000000",
      region: "测试区",
      detail: "测试地址",
    },
    status: "排队中",
    reason: "",
    orderId: null,
    createdAt: startsAt,
    updatedAt: startsAt,
  };
  assertSeckillProjection([a], [r], "qinghe", 1, false, true);
  assert.throws(() =>
    assertSeckillProjection([a], [r], "shanye", 1, false, true),
  );
  assert.throws(() =>
    assertSeckillProjection([a], [r], "qinghe", 2, false, true),
  );
  assert.throws(() =>
    assertSeckillProjection(
      [{ ...a, status: "草稿" }],
      [],
      "qinghe",
      1,
      false,
      true,
    ),
  );
  assert.throws(() =>
    assertSeckillProjection([], [{ ...r, total: 1 }], "qinghe", 1, false, true),
  );
  assert.throws(() =>
    assertSeckillProjection(
      [],
      [{ ...r, status: "订单已创建" }],
      "qinghe",
      1,
      false,
      true,
    ),
  );
});
test("prototype lifecycle preserves exclusive stock, idempotency, limits, cancellation and paid quota", async (t) => {
  const server = await createServer({
    mode: "prototype",
    server: { middlewareMode: true, hmr: false, ws: false },
    appType: "custom",
  });
  const saved = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: { getItem: () => null, setItem: () => {} },
  });
  t.after(async () => {
    if (saved) Object.defineProperty(globalThis, "localStorage", saved);
    else Reflect.deleteProperty(globalThis, "localStorage");
    await server.close();
  });
  const store = await server.ssrLoadModule("/src/data/store.ts"),
    auth = await server.ssrLoadModule("/src/api/session.ts"),
    fixture = await server.ssrLoadModule("/src/data/seckill-demo.ts");
  await store.initializeData();
  const demoAuth = await server.ssrLoadModule("/src/data/demo-auth.ts");
  const merchant = () => {
    auth.context.realm = "merchant";
    demoAuth.demoSignIn("merchant", "qinghe", "admin", "Qinghe!2026");
    store.bindIdentity();
  };
  const member = () => {
    auth.context.realm = "member";
    auth.context.tenant = "qinghe";
    demoAuth.demoSignIn("member", "qinghe", "13800138001", "DemoMember!2026");
    store.bindIdentity();
  };
  merchant();
  const product = store.db.products.find((p: any) => p.id === 102),
    draft = store.db.seckillActivities.find((x: any) => x.id === "SK-QH-1002"),
    ordinary = product.stock;
  assert.equal(draft.skus[0].allocated, 0);
  fixture.demoTransition(draft.id, "publish", "");
  assert.equal(product.stock, ordinary - 5);
  assert.throws(() => fixture.demoTransition(draft.id, "publish", ""));
  fixture.demoTransition(draft.id, "withdraw", "");
  assert.equal(product.stock, ordinary);
  assert.equal(draft.skus[0].available, 0);
  assert.throws(() => fixture.demoTransition(draft.id, "withdraw", ""));
  member();
  const active = store.db.seckillActivities.find(
      (x: any) => x.id === "SK-QH-1001",
    ),
    sku = active.skus[0],
    pool = sku.available;
  const address = store.db.addresses.find(
    (x: any) => x.memberId === store.ui.memberId && x.tenant === "qinghe",
  );
  const input = {
    activityId: active.id,
    version: active.version,
    spec: sku.spec,
    quantity: 1,
    addressId: address.id,
    requestKey: crypto.randomUUID(),
  };
  const first = fixture.demoJoin(input);
  assert.equal(fixture.demoJoin(input), first);
  assert.equal(sku.available, pool - 1);
  assert.equal(sku.held, 1);
  assert.throws(
    () => fixture.demoJoin({ ...input, requestKey: crypto.randomUUID() }),
    /限购/,
  );
  const request = store.db.seckillRequests.find((r: any) => r.id === first);
  request.createdAt = new Date(Date.now() - 2000).toISOString();
  const completed = fixture.demoRequest(first);
  assert.ok(completed.orderId);
  assert.equal(completed.status, "订单已创建");
  assert.equal(fixture.demoRequest(first).orderId, completed.orderId);
  assert.equal(
    store.db.orders.filter((o: any) => o.seckillRequestId === first).length,
    1,
  );
  const physical = store.db.products.find((p: any) => p.id === 101),
    beforeCancel = physical.stock;
  await store.cancelOrder(completed.orderId);
  assert.equal(sku.available, pool);
  assert.equal(sku.held, 0);
  assert.equal(physical.stock, beforeCancel);
  assertPool(sku);
  const second = fixture.demoJoin({
    ...input,
    requestKey: crypto.randomUUID(),
  });
  const next = store.db.seckillRequests.find((r: any) => r.id === second);
  next.createdAt = new Date(Date.now() - 2000).toISOString();
  const nextOrder = fixture.demoRequest(second).orderId;
  const paymentClicks = await Promise.allSettled([
    store.payOrder(nextOrder),
    store.payOrder(nextOrder),
  ]);
  assert.equal(paymentClicks.filter((r) => r.status === "fulfilled").length, 1);
  assert.equal(sku.held, 0);
  assert.equal(sku.sold, 1);
  assertPool(sku);
  assert.throws(
    () => fixture.demoJoin({ ...input, requestKey: crypto.randomUUID() }),
    /限购/,
  );
  merchant();
  fixture.demoTransition(active.id, "stop", "运营停止");
  fixture.demoTransition(active.id, "reconcile", "");
  assert.equal(sku.available, 0);
  assert.equal(sku.returned, pool - 1);
  assertPool(sku);
  assert.throws(() => fixture.demoTransition(active.id, "reconcile", ""));
  fixture.demoTransition(draft.id, "publish", "");
  draft.startsAt = new Date(Date.now() - 1000).toISOString();
  member();
  const pending = fixture.demoJoin({
    ...input,
    activityId: draft.id,
    version: draft.version,
    spec: draft.skus[0].spec,
    requestKey: crypto.randomUUID(),
  });
  store.db.seckillRequests.find((r: any) => r.id === pending).createdAt =
    new Date(Date.now() - 2000).toISOString();
  merchant();
  fixture.demoTransition(draft.id, "stop", "停止新品活动");
  assert.throws(
    () => fixture.demoTransition(draft.id, "reconcile", ""),
    /申请处理中/,
  );
  member();
  assert.equal(fixture.demoRequest(pending).status, "失败");
  assert.equal(draft.skus[0].held, 0);
  merchant();
  fixture.demoTransition(draft.id, "reconcile", "");
  assertPool(draft.skus[0]);
  assert.equal(product.stock, ordinary);
});
