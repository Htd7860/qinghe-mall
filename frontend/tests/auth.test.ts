import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "vite";

test("Bearer browser auth: login, restoration, refresh and logout use explicit tokens", async (t) => {
  const server = await createServer({
    mode: "test",
    server: { middlewareMode: true, hmr: false, ws: false },
    appType: "custom",
  });
  const originalFetch = globalThis.fetch;
  const originalStorage = Object.getOwnPropertyDescriptor(
    globalThis,
    "sessionStorage",
  );
  const memory = new Map<string, string>();
  Object.defineProperty(globalThis, "sessionStorage", {
    configurable: true,
    value: {
      getItem: (k: string) => memory.get(k) ?? null,
      setItem: (k: string, v: string) => memory.set(k, v),
      removeItem: (k: string) => memory.delete(k),
    },
  });
  t.after(async () => {
    globalThis.fetch = originalFetch;
    if (originalStorage)
      Object.defineProperty(globalThis, "sessionStorage", originalStorage);
    else Reflect.deleteProperty(globalThis, "sessionStorage");
    await server.close();
  });
  const auth = await server.ssrLoadModule("/src/api/session.ts");
  const tokens = await server.ssrLoadModule("/src/api/token.ts");
  const pair = (name = "first") => ({
    accessToken: `at_${name}`,
    refreshToken: "rt_browser_fixture",
    expiresAt: Date.now() + 60_000,
    refreshExpiresAt: Date.now() + 600_000,
  });
  const identity = () => ({
    realm: "merchant",
    userId: 101,
    name: "员工",
    tenantId: "10",
    tenantCode: "qinghe",
    role: "运营",
    permissions: ["product"],
    expiresAt: Date.now() + 60_000,
  });
  const reply = (data: unknown) =>
    new Response(JSON.stringify({ code: 0, data, msg: "" }), {
      headers: { "Content-Type": "application/json" },
    });
  const reset = () => {
    memory.clear();
    auth.identities.merchant = null;
    auth.identities.platform = null;
    auth.identities.member = null;
    auth.context.realm = "merchant";
    auth.context.tenant = "";
  };

  await t.test(
    "login receives tokens, fetches identity with Bearer, then persists no password or CSRF",
    async () => {
      reset();
      const calls: string[] = [];
      globalThis.fetch = async (url, init) => {
        calls.push(String(url));
        const headers = new Headers(init?.headers);
        assert.equal(init?.credentials, "omit");
        assert.equal(headers.get("X-CSRF-Token"), null);
        if (String(url).endsWith("/login")) {
          assert.equal(headers.get("Authorization"), null);
          assert.equal(
            JSON.parse(String(init?.body)).password,
            "fictional-password",
          );
          return reply(pair());
        }
        assert.equal(headers.get("Authorization"), "Bearer at_first");
        return reply(identity());
      };
      const current = await auth.signIn("merchant", {
        tenant: "qinghe",
        username: "admin1",
        password: "fictional-password",
      });
      assert.equal(current.userId, 101);
      assert.deepEqual(calls, [
        "/api/admin-api/auth/login",
        "/api/admin-api/auth/session?store=qinghe",
      ]);
      assert.equal(tokens.readTokens("merchant")?.accessToken, "at_first");
      assert.equal(
        [...memory.values()].some((v) =>
          /fictional-password|csrf|permissions/.test(v),
        ),
        false,
      );
      assert.equal("accessToken" in auth.identities.merchant, false);
    },
  );

  await t.test(
    "restoration uses stored Bearer and cannot borrow platform or other store credentials",
    async () => {
      auth.identities.merchant = null;
      let calls = 0;
      globalThis.fetch = async (_url, init) => {
        calls++;
        assert.equal(
          new Headers(init?.headers).get("Authorization"),
          "Bearer at_first",
        );
        return reply(identity());
      };
      assert.equal((await auth.restore("merchant"))?.tenantCode, "qinghe");
      assert.equal(await auth.restore("platform"), null);
      tokens.saveTokens("member", "qinghe", pair("member"));
      assert.equal(await auth.restore("member", "other-store"), null);
      assert.equal(calls, 1);
    },
  );

  await t.test(
    "expired access refreshes once for concurrent restores without replaying business writes",
    async () => {
      reset();
      memory.set(
        "qinghe-auth:bearer:v1:merchant",
        JSON.stringify({ ...pair(), tenantCode: "qinghe", expiresAt: 1 }),
      );
      let refreshCalls = 0;
      globalThis.fetch = async (url, init) => {
        if (String(url).endsWith("/refresh-token")) {
          refreshCalls++;
          assert.equal(new Headers(init?.headers).get("Authorization"), null);
          assert.deepEqual(JSON.parse(String(init?.body)), {
            refreshToken: "rt_browser_fixture",
          });
          return reply(pair("refreshed"));
        }
        assert.equal(
          new Headers(init?.headers).get("Authorization"),
          "Bearer at_refreshed",
        );
        return reply(identity());
      };
      const restored = await Promise.all([
        auth.restore("merchant"),
        auth.restore("merchant"),
      ]);
      assert.equal(
        restored.every((s) => s?.userId === 101),
        true,
      );
      assert.equal(refreshCalls, 1);
      assert.equal(auth.credentials().accessToken, "at_refreshed");
    },
  );

  await t.test(
    "401 on identity read refreshes and retries only the read once",
    async () => {
      reset();
      tokens.saveTokens("merchant", "qinghe", pair());
      let reads = 0;
      let refreshes = 0;
      globalThis.fetch = async (url) => {
        if (String(url).endsWith("/refresh-token")) {
          refreshes++;
          return reply(pair("new"));
        }
        reads++;
        return reads === 1
          ? new Response(null, { status: 401 })
          : reply(identity());
      };
      assert.equal((await auth.restore("merchant"))?.userId, 101);
      assert.equal(reads, 2);
      assert.equal(refreshes, 1);
    },
  );

  await t.test(
    "revoked refresh clears credentials, infrastructure failure preserves retry information",
    async () => {
      reset();
      const expired = { ...pair(), expiresAt: 1, tenantCode: "qinghe" };
      memory.set("qinghe-auth:bearer:v1:merchant", JSON.stringify(expired));
      globalThis.fetch = async () => new Response(null, { status: 401 });
      assert.equal(await auth.restore("merchant"), null);
      assert.equal(tokens.readTokens("merchant"), null);
      memory.set("qinghe-auth:bearer:v1:merchant", JSON.stringify(expired));
      globalThis.fetch = async () => new Response(null, { status: 503 });
      await assert.rejects(auth.restore("merchant"));
      assert.equal(
        tokens.readTokens("merchant")?.refreshToken,
        expired.refreshToken,
      );
    },
  );

  await t.test(
    "foreign identity is rejected before login credentials are persisted",
    async () => {
      reset();
      globalThis.fetch = async (url) =>
        reply(
          String(url).endsWith("/login")
            ? pair()
            : { ...identity(), realm: "platform" },
        );
      await assert.rejects(
        auth.signIn("merchant", {
          tenant: "qinghe",
          username: "admin1",
          password: "fictional-password",
        }),
      );
      assert.equal(tokens.readTokens("merchant"), null);
      assert.equal(auth.identities.merchant, null);
    },
  );

  await t.test(
    "logout only clears local auth after server revocation succeeds",
    async () => {
      reset();
      tokens.saveTokens("merchant", "qinghe", pair());
      tokens.saveTokens("platform", "", pair("platform"));
      auth.identities.merchant = identity();
      globalThis.fetch = async () => new Response(null, { status: 503 });
      await assert.rejects(auth.signOut("merchant"));
      assert.equal(tokens.readTokens("merchant")?.accessToken, "at_first");
      assert.ok(auth.identities.merchant);
      globalThis.fetch = async (url, init) => {
        assert.equal(String(url), "/api/admin-api/auth/logout");
        assert.equal(
          new Headers(init?.headers).get("Authorization"),
          "Bearer at_first",
        );
        return reply(true);
      };
      await auth.signOut("merchant");
      assert.equal(tokens.readTokens("merchant"), null);
      assert.equal(auth.identities.merchant, null);
      assert.equal(tokens.readTokens("platform")?.accessToken, "at_platform");
    },
  );

  await t.test(
    "late refresh must not replace credentials from a newer login",
    async () => {
      reset();
      memory.set(
        "qinghe-auth:bearer:v1:merchant",
        JSON.stringify({ ...pair(), expiresAt: 1, tenantCode: "qinghe" }),
      );
      globalThis.fetch = async () => {
        tokens.saveTokens("merchant", "qinghe", pair("later-login"));
        auth.identities.merchant = identity();
        return reply(pair("stale-refresh"));
      };
      await assert.rejects(auth.restore("merchant"));
      assert.equal(
        tokens.readTokens("merchant")?.accessToken,
        "at_later-login",
      );
      assert.ok(auth.identities.merchant);
    },
  );
});
