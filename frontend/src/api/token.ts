import type { Realm } from "./policy";

/** Browser DTO times are epoch milliseconds; credentials never enter the Identity/view model. */
export type TokenPair = {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  refreshExpiresAt: number;
};
export type StoredTokens = TokenPair & { tenantCode: string };

export function validTokenPair(
  value: unknown,
  allowExpiredAccess = false,
): value is TokenPair {
  if (!value || typeof value !== "object") return false;
  const pair = value as TokenPair;
  const valid = (token: unknown) =>
    typeof token === "string" &&
    token.length > 0 &&
    token.length <= 128 &&
    !/[\s,\x00-\x1f\x7f]/.test(token);
  return (
    valid(pair.accessToken) &&
    valid(pair.refreshToken) &&
    Number.isFinite(pair.expiresAt) &&
    pair.expiresAt > 0 &&
    (allowExpiredAccess || pair.expiresAt > Date.now()) &&
    Number.isFinite(pair.refreshExpiresAt) &&
    pair.refreshExpiresAt > Date.now() &&
    pair.refreshExpiresAt >= pair.expiresAt
  );
}

const key = (realm: Realm) => `qinghe-auth:bearer:v1:${realm}`;

export function readTokens(realm: Realm, tenant?: string): StoredTokens | null {
  try {
    const text = sessionStorage.getItem(key(realm));
    if (!text) return null;
    const pair: StoredTokens = JSON.parse(text);
    // Expired access may still be refreshed; never extend persisted timestamps here.
    if (
      !validTokenPair(pair, true) ||
      typeof pair.tenantCode !== "string" ||
      (realm !== "platform" && !pair.tenantCode)
    ) {
      sessionStorage.removeItem(key(realm));
      return null;
    }
    if (tenant && pair.tenantCode !== tenant) return null;
    return pair;
  } catch {
    return null;
  }
}

export function saveTokens(realm: Realm, tenantCode: string, pair: TokenPair) {
  if (!validTokenPair(pair) || (realm !== "platform" && !tenantCode))
    throw new Error("登录凭据无效，请重新登录");
  sessionStorage.setItem(
    key(realm),
    JSON.stringify({
      tenantCode,
      accessToken: pair.accessToken,
      refreshToken: pair.refreshToken,
      expiresAt: pair.expiresAt,
      refreshExpiresAt: pair.refreshExpiresAt,
    }),
  );
}

export function removeTokens(realm: Realm) {
  sessionStorage.removeItem(key(realm));
}
