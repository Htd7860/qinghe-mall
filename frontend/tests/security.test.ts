import { assertReport } from "../src/api/report.ts";
import test from "node:test";
import assert from "node:assert/strict";
import {
  hasPermission,
  safePaymentUrl,
  safeReturn,
  validIdentity,
  type Identity,
} from "../src/api/policy.ts";
import { assertViewScope, permitsAction } from "../src/api/view-policy.ts";
const identity = (): Identity => ({
  realm: "merchant",
  userId: 1,
  name: "员工",
  tenantId: "100",
  tenantCode: "qinghe",
  role: "商品运营",
  permissions: ["product"],
  expiresAt: Date.now() + 60000,
});
test("identity is bound to realm, tenant, expiry and explicit grants", () => {
  assert.ok(validIdentity(identity(), "merchant", "qinghe"));
  assert.equal(validIdentity(identity(), "platform"), false);
  assert.equal(validIdentity(identity(), "merchant", "shanye"), false);
  assert.equal(
    validIdentity({ ...identity(), expiresAt: 1 }, "merchant"),
    false,
  );
  assert.equal(
    hasPermission(
      { ...identity(), role: "商家管理员", permissions: [] },
      "merchant",
      "refund",
      "qinghe",
    ),
    false,
  );
  assert.equal(
    validIdentity(
      { ...identity(), userId: Number.MAX_SAFE_INTEGER + 1 },
      "merchant",
    ),
    false,
  );
  assert.equal(
    validIdentity({ ...identity(), permissions: [1] }, "merchant"),
    false,
  );
});
test("redirect stays in the authenticated realm and store", () => {
  assert.equal(
    safeReturn("/admin/orders?q=1", "merchant"),
    "/admin/orders?q=1",
  );
  for (const value of [
    "https://evil.test",
    "//evil.test",
    "/platform/tenants",
    "/admin/../platform/tenants",
    "/admin/%2e%2e/platform/tenants",
    "/admin/\\evil",
    "/admin/login?redirect=/platform/tenants",
  ])
    assert.equal(safeReturn(value, "merchant"), "/admin/overview");
  assert.equal(
    safeReturn("/shop/shanye/orders", "member", "qinghe"),
    "/shop/qinghe/account",
  );
});
test("merchant and platform cannot borrow each other's actions", () => {
  assert.ok(permitsAction("merchant", "product.save", ["product"]));
  assert.equal(permitsAction("merchant", "aftersale.refund", ["order"]), false);
  assert.equal(permitsAction("merchant", "tenant.status", ["tenants"]), false);
  assert.equal(permitsAction("member", "order.ship", ["order"]), false);
  assert.equal(permitsAction("platform", "product.save", ["product"]), false);
  assert.equal(permitsAction("merchant", "unknown", ["*"]), false);
});
test("view projection rejects another tenant and another consumer before hydration", () => {
  assert.doesNotThrow(() =>
    assertViewScope(
      {
        tenantCode: "qinghe",
        state: {
          orders: [{ id: "A", tenant: "qinghe", memberId: 1 }],
          settings: { qinghe: {} },
        },
      },
      "member",
      "qinghe",
      1,
    ),
  );
  for (const state of [
    { products: [{ tenant: "shanye" }] },
    { orders: [{ tenant: "qinghe", memberId: 2 }] },
    { settings: { shanye: {} } },
    { staff: [{ tenant: "qinghe" }] },
    { members: [{ tenant: "qinghe", id: 2 }] },
    { products: [{ tenant: "qinghe", nested: { accessToken: "secret" } }] },
    { afterSales: [{ tenant: "qinghe", orderId: "not-owned" }] },
    { tenants: [{ code: "shanye" }] },
  ])
    assert.throws(() =>
      assertViewScope({ tenantCode: "qinghe", state }, "member", "qinghe", 1),
    );
  assert.throws(() =>
    assertViewScope(
      {
        tenantCode: "qinghe",
        state: { orders: [{ tenant: "qinghe", memberId: 1 }] },
      },
      "member",
      "qinghe",
      0,
    ),
  );
});
test("payment redirect requires same origin or explicitly allowed HTTPS origin", () => {
  assert.equal(
    safePaymentUrl("/pay/1", "https://shop.test"),
    "https://shop.test/pay/1",
  );
  assert.equal(
    safePaymentUrl("https://pay.test/1", "https://shop.test", [
      "https://pay.test",
    ]),
    "https://pay.test/1",
  );
  for (const url of [
    "javascript:alert(1)",
    "https://evil.test/1",
    "http://pay.test/1",
    "https://user:pass@shop.test/1",
  ])
    assert.throws(() => safePaymentUrl(url, "https://shop.test"));
});

test("financial overview rejects incomplete or imprecise server aggregates", () => {
  const report = {
    asOf: "2026-10-19T10:00:00+08:00",
    paymentAmount: 18600,
    paidCount: 1,
    refundAmount: 8900,
    refundCount: 1,
    memberCount: 5,
    pendingShip: 1,
    pendingAfterSale: 0,
    lowStock: 1,
    failedJobs: 0,
    trend: Array.from({ length: 7 }, (_, i) => ({
      date: `2026-10-${13 + i}`,
      amount: 100,
    })),
  };
  assert.doesNotThrow(() => assertReport(report));
  assert.throws(() => assertReport({ ...report, paymentAmount: 1.5 }));
  assert.throws(() => assertReport({ ...report, trend: [] }));
  assert.throws(() => assertReport({ ...report, paidCount: undefined }));
});
