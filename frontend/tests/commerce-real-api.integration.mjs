import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { readFileSync, writeFileSync } from "node:fs";
import { createServer } from "vite";
if (process.env.QINGHE_COMMERCE_FRONTEND_TEST_ENABLED !== "true")
  throw new Error(
    "Explicit QINGHE_COMMERCE_FRONTEND_TEST_ENABLED=true required",
  );
if (!process.env.COMMERCE_PASSWORD)
  throw new Error(
    "Synthetic fixture password must be provided in process environment",
  );
const state = JSON.parse(process.env.COMMERCE_TEST_CASES || "{}");
const checks = [];
const memory = new Map();
globalThis.sessionStorage = {
  getItem: (k) => memory.get(k) ?? null,
  setItem: (k, v) => memory.set(k, String(v)),
  removeItem: (k) => memory.delete(k),
};
globalThis.window = new EventTarget();
const nativeFetch = globalThis.fetch;
globalThis.fetch = (url, init) => {
  assert.equal(typeof url, "string");
  assert.ok(url.startsWith("/api/"), "Only local commerce HTTP allowed");
  return nativeFetch("http://127.0.0.1:25180" + url, init);
};
const server = await createServer({
  root: fileURLToPath(new URL("../", import.meta.url)),
  mode: "test",
  configFile: false,
  cacheDir:
    process.env.COMMERCE_TEST_CACHE_DIR ||
    fileURLToPath(
      new URL("../node_modules/.vite-commerce-integration/", import.meta.url),
    ),
  server: { middlewareMode: true, hmr: false, ws: false },
  appType: "custom",
});
try {
  const auth = await server.ssrLoadModule("/src/api/session.ts");
  auth.context.realm = "member";
  auth.context.tenant = "commerce-alpha";
  await auth.signIn("member", {
    tenant: "commerce-alpha",
    username: "13800000001",
    password: process.env.COMMERCE_PASSWORD,
  });
  checks.push("real frontend member signIn and identity");
  const orders = await server.ssrLoadModule("/src/api/member-order.ts");
  const payment = await server.ssrLoadModule("/src/api/member-payment.ts");
  const sales = await server.ssrLoadModule("/src/api/member-aftersale.ts");
  for (const id of [
    state.flow.orderId,
    state.couponRefundFlow.created.order.id,
    state.paymentCloseFlow.race.id,
  ]) {
    const order = await orders.readMemberOrder(id);
    assert.equal(order.id, id);
    assert.ok(order.items.every((x) => Array.isArray(x.properties)));
    checks.push("frontend validates original order detail " + id);
    const p = await payment.readMemberPayment(id);
    assert.equal(p.order.id, id);
    checks.push("frontend validates payment " + id);
  }
  for (const id of [
    state.flow.afterSale.id,
    state.flow.returnFlow.applied.id,
    state.unknownRefundFlow.applied.id,
    state.couponRefundFlow.sale.id,
  ]) {
    const sale = await sales.readMemberAfterSale(id);
    assert.equal(sale.id, id);
    checks.push("frontend validates member after-sale " + id);
  }
  auth.context.realm = "merchant";
  auth.context.tenant = "commerce-alpha";
  await auth.signIn("merchant", {
    tenant: "commerce-alpha",
    username: "fixtureadmin",
    password: process.env.COMMERCE_PASSWORD,
  });
  checks.push("real frontend merchant signIn and identity");
  const financial = await server.ssrLoadModule("/src/api/merchant-refund.ts");
  for (const plan of [
    state.flow.refundPlan,
    state.flow.returnFlow.plan,
    state.unknownRefundFlow.plan,
    state.couponRefundFlow.plan,
  ]) {
    const result = await financial.financialRefundResult(
      plan.id,
      plan.refundNo,
    );
    assert.equal(result.status, "SUCCESS");
    assert.equal(result.totalPrice, plan.totalPrice);
    checks.push("frontend validates original financial refund " + plan.id);
  }

  writeFileSync(
    process.env.COMMERCE_TEST_RESULT_PATH ||
      (() => {
        throw Error("COMMERCE_TEST_RESULT_PATH required");
      })(),
    JSON.stringify(
      {
        mode: "actual frontend API modules with in-memory DOM/storage and real HTTP, not browser UI",
        checks,
      },
      null,
      2,
    ),
  );
  console.log(
    JSON.stringify({ frontendRealApiChecks: checks.length, passed: true }),
  );
} catch (e) {
  console.log(
    JSON.stringify({
      passed: false,
      errorType: e.constructor.name,
      message: e.message,
      code: e.code,
      stage: checks.at(-1),
      checksCompleted: checks.length,
    }),
  );
  process.exitCode = 1;
} finally {
  await server.close();
}
