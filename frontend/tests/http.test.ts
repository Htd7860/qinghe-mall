import test from "node:test";
import assert from "node:assert/strict";
import { request, ApiError } from "../src/api/http.ts";
const json = (data: unknown, code = 0) =>
  new Response(JSON.stringify({ code, data, msg: "被拒绝" }), {
    headers: {
      "Content-Type": "application/json",
      "X-Request-Id": "test-trace",
    },
  });
test("request boundary: protocol, failure semantics, credentials and no automatic write retry", async (t) => {
  const original = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = original;
  });
  await t.test(
    "explicit Bearer/tenant/idempotency headers; no cookies or CSRF header",
    async () => {
      globalThis.fetch = async (url, init) => {
        assert.equal(url, "/api/admin-api/order");
        assert.equal(init?.credentials, "omit");
        assert.equal(init?.cache, "no-store");
        assert.equal(init?.redirect, "error");
        const h = new Headers(init?.headers);
        assert.equal(h.get("tenant-id"), "100");
        assert.equal(h.get("X-CSRF-Token"), null);
        assert.equal(h.get("Cookie"), null);
        assert.equal(h.get("Idempotency-Key"), "key");
        assert.equal(h.get("Authorization"), "Bearer at_http_test");
        return json({ id: "A" });
      };
      assert.deepEqual(
        await request(
          "/admin-api/order",
          {
            tenantId: "100",
            accessToken: "at_http_test",
            idempotencyKey: "key",
          },
          {
            method: "POST",
            body: "{}",
            credentials: "include",
            headers: {
              Cookie: "legacy=ignored",
              "X-CSRF-Token": "ignored",
              Authorization: "Bearer ignored",
            },
          },
        ),
        { id: "A" },
      );
    },
  );
  await t.test(
    "anonymous calls have no inherited Authorization and bad tokens never reach fetch",
    async () => {
      let calls = 0;
      globalThis.fetch = async (_url, init) => {
        calls++;
        assert.equal(new Headers(init?.headers).get("Authorization"), null);
        assert.equal(init?.credentials, "omit");
        return json(true);
      };
      await request(
        "/public",
        {},
        { headers: { Authorization: "Bearer stale" } },
      );
      for (const token of ["bad token", "one,two", "a".repeat(129)])
        await assert.rejects(
          request("/protected", { accessToken: token }),
          (e: ApiError) => e.code === 401,
        );
      assert.equal(calls, 1);
    },
  );
  await t.test(
    "HTML fallback and malformed envelope are never treated as success",
    async () => {
      for (const response of [
        new Response("<html>index</html>", {
          headers: { "Content-Type": "text/html" },
        }),
        new Response('{"data":true}', {
          headers: { "Content-Type": "application/json" },
        }),
      ]) {
        globalThis.fetch = async () => response;
        await assert.rejects(
          request("/save", {}, { method: "POST" }),
          (e: ApiError) => e.code === -2 && e.outcomeUnknown,
        );
      }
    },
  );
  await t.test("401/403/429 remain explicit rejected requests", async () => {
    for (const status of [401, 403, 429]) {
      globalThis.fetch = async () => new Response(null, { status });
      await assert.rejects(
        request("/save", {}, { method: "POST" }),
        (e: ApiError) => e.code === status && !e.outcomeUnknown,
      );
    }
    globalThis.fetch = async () => json(null, 401);
    await assert.rejects(request("/session"), (e: ApiError) => e.code === 401);
  });
  await t.test(
    "network failure/5xx writes have unknown outcomes and are sent once",
    async () => {
      let count = 0;
      globalThis.fetch = async () => {
        count++;
        throw new TypeError("offline");
      };
      await assert.rejects(
        request("/save", {}, { method: "POST" }),
        (e: ApiError) => e.outcomeUnknown,
      );
      assert.equal(count, 1);
      globalThis.fetch = async () => new Response(null, { status: 503 });
      await assert.rejects(
        request("/save", {}, { method: "POST" }),
        (e: ApiError) => e.outcomeUnknown && e.code === 503,
      );
      await assert.rejects(
        request("/read"),
        (e: ApiError) => !e.outcomeUnknown,
      );
    },
  );
  await t.test("timeout and pre-aborted signal cancel fetch", async () => {
    globalThis.fetch = async (_url, init) =>
      new Promise((_resolve, reject) => {
        if (init?.signal?.aborted) {
          reject(new DOMException("aborted", "AbortError"));
          return;
        }
        init?.signal?.addEventListener(
          "abort",
          () => reject(new DOMException("aborted", "AbortError")),
          { once: true },
        );
      });
    await assert.rejects(
      request("/slow", { timeoutMs: 10 }, { method: "POST" }),
      (e: ApiError) => e.outcomeUnknown,
    );
    const controller = new AbortController();
    controller.abort();
    await assert.rejects(
      request("/read", { signal: controller.signal }),
      (e: ApiError) => !e.outcomeUnknown,
    );
  });
  await t.test(
    "invalid request targets rejected before any fetch",
    async () => {
      let count = 0;
      globalThis.fetch = async () => {
        count++;
        return json(true);
      };
      for (const path of [
        "https://evil.test/",
        "//evil.test/",
        "/a\\b",
        "/a\nb",
      ])
        await assert.rejects(request(path));
      await assert.rejects(request("/a", { base: "https://evil.test" }));
      assert.equal(count, 0);
    },
  );
});
