import { ApiError, request } from "./http";
import { context, credentials, demo, identities } from "./session";

export type MemberProfile = {
  id: number;
  nickname: string;
  mobileMasked: string;
  marketingOptOut: boolean;
  authVersion: number;
};
export type MemberAddress = {
  id: number;
  name: string;
  mobile: string;
  areaId: number;
  areaName: string;
  detailAddress: string;
  defaultStatus: boolean;
  authVersion: number;
};
export type AddressInput = Pick<
  MemberAddress,
  "name" | "mobile" | "areaId" | "detailAddress" | "defaultStatus"
>;
export type MemberArea = {
  id: number;
  parentId: number;
  name: string;
  type: number;
};
const integer = (x: unknown, min = 0) =>
  typeof x === "number" && Number.isSafeInteger(x) && x >= min;
export function memberIdentity() {
  const member = identities.member;
  return `${context.realm}:${context.tenant}:${member?.tenantId || ""}:${member?.userId || ""}`;
}
async function call<T>(path: string, init: RequestInit = {}) {
  const member = identities.member;
  if (
    demo ||
    context.realm !== "member" ||
    !member ||
    member.tenantCode !== context.tenant
  )
    throw new ApiError("请从当前商城登录", 401);
  const identity = memberIdentity();
  const bearer = credentials();
  if (!bearer.accessToken) throw new ApiError("请重新登录", 401);
  const result = await request<T>(
    `/app-api/member/${path}${path.includes("?") ? "&" : "?"}store=${encodeURIComponent(member.tenantCode)}`,
    bearer,
    init,
  );
  if (identity !== memberIdentity())
    throw new ApiError("登录身份已变化，请重新读取", 409, !!init.method);
  return result;
}
function profile(value: unknown): value is MemberProfile {
  if (!value || typeof value !== "object") return false;
  const x = value as MemberProfile;
  return (
    integer(x.id, 1) &&
    x.id === identities.member?.userId &&
    typeof x.nickname === "string" &&
    /^1[3-9][0-9]\*{4}[0-9]{4}$/.test(x.mobileMasked) &&
    typeof x.marketingOptOut === "boolean" &&
    integer(x.authVersion)
  );
}
function address(value: unknown): value is MemberAddress {
  if (!value || typeof value !== "object") return false;
  const x = value as MemberAddress;
  return (
    integer(x.id, 1) &&
    integer(x.authVersion) &&
    integer(x.areaId, 1) &&
    x.areaId <= 2147483647 &&
    typeof x.name === "string" &&
    !!x.name.trim() &&
    x.name.length <= 50 &&
    /^1[3-9][0-9]{9}$/.test(x.mobile) &&
    typeof x.areaName === "string" &&
    typeof x.detailAddress === "string" &&
    !!x.detailAddress.trim() &&
    x.detailAddress.length <= 255 &&
    typeof x.defaultStatus === "boolean"
  );
}
export async function readMemberProfile() {
  const result = await call<unknown>("user/get");
  if (!profile(result)) throw new ApiError("会员资料响应不完整", -2);
  return result;
}
export async function updateMemberPreference(
  marketingOptOut: boolean,
  expectedVersion: number,
) {
  const result = await call<unknown>("user/preferences", {
    method: "PUT",
    body: JSON.stringify({ marketingOptOut, expectedVersion }),
  });
  if (
    !profile(result) ||
    result.marketingOptOut !== marketingOptOut ||
    ![expectedVersion, expectedVersion + 1].includes(result.authVersion)
  )
    throw new ApiError("偏好保存结果待确认，请重新读取", 503, true);
  return result;
}
export async function listMemberAddresses() {
  const result = await call<unknown>("address/list");
  if (
    !Array.isArray(result) ||
    !result.every(address) ||
    new Set(result.map((x) => x.id)).size !== result.length ||
    result.filter((x) => x.defaultStatus).length > 1
  )
    throw new ApiError("地址响应不完整，请重新读取", -2);
  return result as MemberAddress[];
}
export async function saveMemberAddress(
  input: AddressInput,
  current?: MemberAddress,
) {
  const body = {
    name: input.name.trim(),
    mobile: input.mobile,
    areaId: input.areaId,
    detailAddress: input.detailAddress.trim(),
    defaultStatus: input.defaultStatus,
  };
  if (
    !body.name ||
    body.name.length > 50 ||
    !/^1[3-9][0-9]{9}$/.test(body.mobile) ||
    !integer(body.areaId, 1) ||
    !body.detailAddress ||
    body.detailAddress.length > 255
  )
    throw new ApiError("请填写有效的收货信息和地区", 400);
  const result = await call<unknown>(
    current ? "address/update" : "address/create",
    {
      method: current ? "PUT" : "POST",
      body: JSON.stringify(
        current
          ? { ...body, id: current.id, expectedVersion: current.authVersion }
          : body,
      ),
    },
  );
  if (current ? result !== true : !integer(result, 1))
    throw new ApiError("地址保存结果待确认，请重新读取", 503, true);
}
export async function deleteMemberAddress(row: MemberAddress) {
  const result = await call<unknown>(
    `address/delete?id=${row.id}&expectedVersion=${row.authVersion}`,
    { method: "DELETE" },
  );
  if (result !== true)
    throw new ApiError("地址删除结果待确认，请重新读取", 503, true);
}
export async function memberAreas() {
  const result = await call<unknown>("address/area-options");
  if (
    !Array.isArray(result) ||
    result.length > 5000 ||
    !result.every(
      (v: MemberArea) =>
        v &&
        integer(v.id, 1) &&
        integer(v.parentId) &&
        typeof v.name === "string" &&
        [2, 3, 4].includes(v.type),
    )
  )
    throw new ApiError("地区选项响应不完整", -2);
  const rows = result as MemberArea[];
  const map = new Map(rows.map((x) => [x.id, x]));
  if (
    map.size !== rows.length ||
    rows.some((x) =>
      x.parentId === 0
        ? x.type !== 2
        : !map.has(x.parentId) || map.get(x.parentId)!.type >= x.type,
    )
  )
    throw new ApiError("地区层级响应不完整", -2);
  return rows;
}
