import { reactive } from "vue";
import { resolveStorefront } from "./storefront";
import { request, ApiError } from "./http";
import { validIdentity, type Identity, type Realm } from "./policy";
import {
  readTokens,
  saveTokens,
  removeTokens,
  validTokenPair,
  type StoredTokens,
  type TokenPair,
} from "./token";
export const demo = import.meta.env.MODE === "prototype";
export const identities = reactive<Record<Realm, Identity | null>>({
  merchant: null,
  platform: null,
  member: null,
});
export const context = reactive({ realm: "merchant" as Realm, tenant: "" });
const loginVersions: Record<Realm, number> = {
  member: 0,
  merchant: 0,
  platform: 0,
};
export function cancelSignIn(realm: Realm) {
  loginVersions[realm]++;
}
export function prefix(realm = context.realm) {
  return realm === "member"
    ? "/app-api"
    : realm === "platform"
      ? "/platform-api"
      : "/admin-api";
}
export function credentials() {
  const s = identities[context.realm];
  const pair = s ? readTokens(context.realm, s.tenantCode) : null;
  return { tenantId: s?.tenantId, accessToken: pair?.accessToken };
}

const refreshing = new Map<
  Realm,
  { accessToken: string; work: Promise<StoredTokens> }
>();
async function refreshTokens(realm: Realm, previous: StoredTokens) {
  const pending = refreshing.get(realm);
  if (pending?.accessToken === previous.accessToken) return pending.work;
  const work = (async () => {
    const pair = await request<TokenPair>(
      `${prefix(realm)}/auth/refresh-token${realm === "member" ? `?store=${encodeURIComponent(previous.tenantCode)}` : ""}`,
      {},
      {
        method: "POST",
        body: JSON.stringify({ refreshToken: previous.refreshToken }),
      },
    );
    if (!validTokenPair(pair))
      throw new ApiError("登录凭据无效，请重新登录", 401);
    if (
      realm === "member" &&
      (pair.refreshToken !== previous.refreshToken ||
        pair.refreshExpiresAt !== previous.refreshExpiresAt)
    )
      throw new ApiError("刷新结果与原登录不一致，请重新查询登录状态", 503);
    // A delayed refresh must not overwrite credentials from a later login/logout.
    if (readTokens(realm)?.accessToken !== previous.accessToken)
      throw new ApiError("登录状态已变更，请重试", 409);
    saveTokens(realm, previous.tenantCode, pair);
    return { ...pair, tenantCode: previous.tenantCode };
  })();
  refreshing.set(realm, { accessToken: previous.accessToken, work });
  try {
    return await work;
  } finally {
    if (refreshing.get(realm)?.work === work) refreshing.delete(realm);
  }
}

async function loadIdentity(
  realm: Realm,
  tenant: string | undefined,
  accessToken: string,
) {
  const identity = await request<unknown>(
    `${prefix(realm)}/auth/session${tenant ? `?store=${encodeURIComponent(tenant)}` : ""}`,
    { accessToken },
  );
  if (!validIdentity(identity, realm, tenant))
    throw new ApiError("登录信息无效，请重新登录", 401);
  return identity;
}

export async function restore(realm: Realm, tenant?: string) {
  if (demo)
    return identities[realm] && validIdentity(identities[realm], realm, tenant)
      ? identities[realm]
      : null;
  let pair = readTokens(realm, tenant);
  if (!pair) {
    identities[realm] = null;
    return null;
  }
  try {
    let refreshed = false;
    if (pair.expiresAt <= Date.now()) {
      pair = await refreshTokens(realm, pair);
      refreshed = true;
    }
    let s: Identity;
    try {
      s = await loadIdentity(
        realm,
        tenant || pair.tenantCode,
        pair.accessToken,
      );
    } catch (e) {
      if (!(e instanceof ApiError) || e.code !== 401 || refreshed) throw e;
      pair = await refreshTokens(realm, pair);
      s = await loadIdentity(
        realm,
        tenant || pair.tenantCode,
        pair.accessToken,
      );
    }
    if (readTokens(realm)?.accessToken !== pair.accessToken)
      throw new ApiError("登录状态已变更，请重试", 409);
    identities[realm] = s;
    return s;
  } catch (e) {
    // A delayed old request must not clear a more recent login.
    const latest = readTokens(realm);
    if (latest && latest.accessToken !== pair.accessToken) throw e;
    identities[realm] = null;
    if (e instanceof ApiError && e.code === 401) {
      if (readTokens(realm)?.accessToken === pair.accessToken)
        removeTokens(realm);
      return null;
    }
    throw e;
  }
}
export async function signIn(
  realm: Realm,
  input: {
    tenant?: string;
    username: string;
    password: string;
  },
) {
  if (demo) throw new Error("演示登录需通过独立的演示入口");
  const version = ++loginVersions[realm];
  const requireCurrent = () => {
    if (version !== loginVersions[realm])
      throw new ApiError("登录操作已取消，请重新登录", 409);
  };
  // Consumer credentials are submitted only after the actual Host and requested store resolve consistently.
  const store =
    realm === "member" ? await resolveStorefront(input.tenant) : undefined;
  const loginInput = store ? { ...input, tenant: store.tenantCode } : input;
  requireCurrent();
  const body =
    realm === "member"
      ? {
          tenant: loginInput.tenant,
          mobile: input.username,
          password: input.password,
        }
      : loginInput;
  const pair = await request<TokenPair>(
    `${prefix(realm)}/auth/login`,
    {},
    { method: "POST", body: JSON.stringify(body) },
  );
  if (!validTokenPair(pair))
    throw new ApiError("登录凭据无效，请重新登录", 401);
  const s = await loadIdentity(realm, loginInput.tenant, pair.accessToken);
  requireCurrent();
  saveTokens(realm, s.tenantCode, pair);
  identities[realm] = s;
  return s;
}
export async function signOut(realm = context.realm) {
  cancelSignIn(realm);
  const current = identities[realm];
  const pair = demo
    ? null
    : readTokens(realm, realm === "member" ? context.tenant : undefined);
  if (!demo && pair) {
    const completed = await request<boolean>(
      `${prefix(realm)}/auth/logout`,
      { accessToken: pair.accessToken, tenantId: current?.tenantId },
      { method: "POST" },
    );
    if (completed !== true)
      throw new ApiError("退出结果未确认，请重试退出", 503);
  }
  if (!demo && pair) {
    // A refresh may replace access while logout revokes their shared parent session.
    // Preserve a later independent login, but clear a refreshed pair from this revoked session.
    const latest = readTokens(realm);
    if (
      latest &&
      latest.accessToken !== pair.accessToken &&
      latest.refreshToken !== pair.refreshToken
    )
      return;
    removeTokens(realm);
  }
  identities[realm] = null;
}
