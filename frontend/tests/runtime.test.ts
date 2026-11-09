import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "vite";
test("API runtime never invokes demo transactions and recovers uncertain writes", async (t) => {
  const server = await createServer({
    mode: "test",
    server: { middlewareMode: true, hmr: false, ws: false },
    appType: "custom",
  });
  const savedFetch = globalThis.fetch;
  const savedStorage = Object.getOwnPropertyDescriptor(
    globalThis,
    "sessionStorage",
  );
  const savedWindow = Object.getOwnPropertyDescriptor(globalThis, "window");
  const memory = new Map<string, string>();
  Object.defineProperty(globalThis, "sessionStorage", {
    configurable: true,
    value: {
      getItem: (k: string) => memory.get(k) || null,
      setItem: (k: string, v: string) => memory.set(k, v),
      removeItem: (k: string) => memory.delete(k),
    },
  });
  Object.defineProperty(globalThis, "window", {
    configurable: true,
    value: new EventTarget(),
  });
  t.after(async () => {
    globalThis.fetch = savedFetch;
    if (savedStorage)
      Object.defineProperty(globalThis, "sessionStorage", savedStorage);
    else Reflect.deleteProperty(globalThis, "sessionStorage");
    if (savedWindow) Object.defineProperty(globalThis, "window", savedWindow);
    else Reflect.deleteProperty(globalThis, "window");
    await server.close();
  });
  const store = await server.ssrLoadModule("/src/data/store.ts");
  const auth = await server.ssrLoadModule("/src/api/session.ts");
  auth.context.realm = "merchant";
  auth.identities.merchant = {
    realm: "merchant",
    userId: 1,
    name: "操作员",
    tenantId: "100",
    tenantCode: "qinghe",
    role: "商品运营",
    permissions: ["product"],
    expiresAt: Date.now() + 600000,
  };
  store.bindIdentity();
  const reply = (data: unknown) =>
    new Response(JSON.stringify({ code: 0, data, msg: "" }), {
      headers: { "Content-Type": "application/json" },
    });
  await t.test(
    "API initializes empty; revoked or foreign commands cannot send writes",
    async () => {
      await store.initializeData();
      assert.equal(store.db.products.length, 0);
      let calls = 0;
      globalThis.fetch = async () => {
        calls++;
        return reply(null);
      };
      await assert.rejects(
        store.command("tenant.status", {}, () => {
          throw new Error("demo invoked");
        }),
      );
      await assert.rejects(
        store.command("aftersale.refund", {}, () => {
          throw new Error("demo invoked");
        }),
      );
      assert.equal(calls, 0);
    },
  );
  await t.test(
    "API success hydrates only server state and ignores the supplied preview callback",
    async () => {
      let preview = 0;
      globalThis.fetch = async (_url, init) => {
        if (init?.method === "POST") {
          assert.equal(memory.size, 1);
          assert.ok(new Headers(init.headers).get("Idempotency-Key"));
          return reply({ id: "server-id" });
        }
        return reply({ tenantCode: "qinghe", state: { products: [] } });
      };
      assert.deepEqual(
        await store.command("product.save", {}, () => {
          preview++;
          return { id: "fake" };
        }),
        { id: "server-id" },
      );
      assert.equal(preview, 0);
      assert.equal(memory.size, 0);
      assert.equal(store.db.products.length, 0);
    },
  );
  await t.test(
    "unknown outcome survives refresh; a second click sends no second write",
    async () => {
      let sent = 0;
      globalThis.fetch = async () => {
        sent++;
        throw new TypeError("offline");
      };
      await assert.rejects(
        store.command("product.save", {}, () => {
          throw new Error("demo invoked");
        }),
      );
      assert.ok(store.ui.uncertain);
      assert.equal(memory.size, 1);
      store.ui.uncertain = "";
      store.restorePending();
      assert.ok(store.ui.uncertain);
      await assert.rejects(store.command("product.save", {}, () => null));
      assert.equal(sent, 1);
      globalThis.fetch = async () => reply({ status: "processing" });
      await assert.rejects(store.resolvePending());
      assert.ok(store.ui.uncertain);
      globalThis.fetch = async (url) =>
        String(url).includes("operations/")
          ? reply({ status: "failed" })
          : reply({ tenantCode: "qinghe", state: {} });
      await store.resolvePending();
      assert.equal(store.ui.uncertain, "");
      assert.equal(memory.size, 0);
    },
  );
  await t.test(
    "another tenant response is rejected before display",
    async () => {
      globalThis.fetch = async () =>
        reply({
          tenantCode: "shanye",
          state: { products: [{ tenant: "shanye", id: 201 }] },
        });
      await store.loadPage("products");
      assert.equal(store.db.products.length, 0);
      assert.match(store.ui.error, /异常/);
    },
  );
  await t.test(
    "seckill API mode sends a durable request without creating a local order",
    async () => {
      const seckill = await server.ssrLoadModule("/src/data/seckill.ts");
      auth.context.realm = "member";
      auth.context.tenant = "qinghe";
      auth.identities.member = {
        ...auth.identities.merchant,
        realm: "member",
        permissions: [],
      };
      store.bindIdentity();
      let writes = 0;
      globalThis.fetch = async (url, init) => {
        if (init?.method === "POST") {
          writes++;
          assert.match(String(url), /seckill.join/);
          const body = JSON.parse(String(init.body));
          assert.equal(body.requestKey, "client-request");
          return reply("durable-application");
        }
        return reply({
          tenantCode: "qinghe",
          state: {},
          serverTime: Date.now(),
        });
      };
      assert.equal(
        await seckill.joinSeckill({
          activityId: "activity",
          version: 1,
          spec: "M",
          quantity: 1,
          addressId: 1,
          requestKey: "client-request",
          quoteToken: "quote",
        }),
        "durable-application",
      );
      assert.equal(writes, 1);
      assert.equal(store.db.orders.length, 0);
      assert.equal(store.db.seckillRequests.length, 0);
      globalThis.fetch = async () =>
        reply({
          token: "q",
          subtotal: 7900,
          shipping: 800,
          total: 8700,
          expiresAt: "invalid",
        });
      await assert.rejects(
        seckill.getSeckillQuote({
          activityId: "activity",
          version: 1,
          spec: "M",
          quantity: 1,
          addressId: 1,
        }),
        /报价/,
      );
      globalThis.fetch = async () =>
        reply({ tenantCode: "qinghe", state: {}, serverTime: "invalid" });
      await store.loadPage("seckill");
      assert.match(store.ui.error, /时间/);
    },
  );
});
