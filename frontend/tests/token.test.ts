import test from "node:test";
import assert from "node:assert/strict";
import {
  readTokens,
  saveTokens,
  removeTokens,
  validTokenPair,
} from "../src/api/token.ts";

test("Bearer token storage isolates realms and stores only credentials for the current tab", (t) => {
  const saved = Object.getOwnPropertyDescriptor(globalThis, "sessionStorage");
  const memory = new Map<string, string>();
  Object.defineProperty(globalThis, "sessionStorage", {
    configurable: true,
    value: {
      getItem: (k: string) => memory.get(k) ?? null,
      setItem: (k: string, v: string) => memory.set(k, v),
      removeItem: (k: string) => memory.delete(k),
    },
  });
  t.after(() => {
    if (saved) Object.defineProperty(globalThis, "sessionStorage", saved);
    else Reflect.deleteProperty(globalThis, "sessionStorage");
  });
  const pair = {
    accessToken: "at_test_storage",
    refreshToken: "rt_test_storage",
    expiresAt: Date.now() + 60_000,
    refreshExpiresAt: Date.now() + 600_000,
  };
  assert.equal(validTokenPair(pair), true);
  saveTokens("merchant", "qinghe", {
    ...pair,
    password: "must-not-persist",
  } as typeof pair);
  assert.equal(readTokens("merchant", "qinghe")?.accessToken, pair.accessToken);
  assert.equal(readTokens("platform"), null);
  assert.equal(readTokens("member", "qinghe"), null);
  assert.equal(readTokens("merchant", "other-store"), null);
  assert.equal(
    [...memory.values()].some((v) => v.includes("must-not-persist")),
    false,
  );
  saveTokens("platform", "", { ...pair, accessToken: "at_platform" });
  removeTokens("merchant");
  assert.equal(readTokens("merchant"), null);
  assert.equal(readTokens("platform")?.accessToken, "at_platform");

  const memberKey = "qinghe-auth:bearer:v1:member";
  memory.set(
    memberKey,
    JSON.stringify({ ...pair, tenantCode: "qinghe", expiresAt: 1 }),
  );
  assert.equal(readTokens("member", "qinghe")?.refreshToken, pair.refreshToken);
  assert.equal(validTokenPair({ ...pair, expiresAt: 1 }), false);
  memory.set(
    memberKey,
    JSON.stringify({ ...pair, tenantCode: "qinghe", refreshExpiresAt: 1 }),
  );
  assert.equal(readTokens("member"), null);
  assert.equal(memory.has(memberKey), false);
  memory.set(memberKey, "broken json");
  assert.equal(readTokens("member"), null);
  for (const token of ["", "one two", "one,two", "a".repeat(129)])
    assert.equal(validTokenPair({ ...pair, accessToken: token }), false);
  assert.throws(() => saveTokens("member", "", pair));
  assert.equal(
    validTokenPair({ ...pair, refreshExpiresAt: pair.expiresAt - 1 }),
    false,
  );
});
