import { request, ApiError, type PageResult } from "./http";
import { context, credentials, identities, prefix, demo } from "./session";
import { ref } from "vue";
export type ManagementKind =
  "staff" | "roles" | "departments" | "tenants" | "packages" | "menus";
export type ManagementRow = Record<string, unknown> & {
  id: number;
  authVersion: number;
  status: number;
  deleted: boolean;
  name?: string;
  username?: string;
  nickname?: string;
  type?: number;
};
export type NavigationItem = {
  id: number;
  name: string;
  parentId: number;
  type: number;
  sort: number;
  path: string;
  icon: string;
  permission: string;
};
export type MenuOption = {
  id: number;
  name: string;
  parentId: number;
  type: number;
  sort: number;
  permission: string;
  status?: number;
};
export type PackageImpact = {
  packageId: number;
  expectedVersion: number;
  impactToken: string;
  affectedTenants: number;
  changedRoles: number;
  addedBindings: number;
  removedBindings: number;
  tenants: {
    tenantId: number;
    code: string;
    expectedVersion: number;
    deleted: boolean;
    changedRoles: number;
    addedBindings: number;
    removedBindings: number;
  }[];
};
export type PackageChange = {
  operationId: string;
  packageId: number;
  actorUserId: number;
  fromVersion: number;
  toVersion: number;
  changed: boolean;
  impact: Record<string, unknown>;
  createTime: string;
};
export const navigationItems = ref<NavigationItem[]>([]);
export async function refreshNavigation() {
  const realm = context.realm,
    userId = identities[realm]?.userId;
  const items = await navigation();
  if (realm === context.realm && userId === identities[realm]?.userId)
    navigationItems.value = items;
}
export const managementSpec = {
  staff: { title: "员工管理", endpoint: "user", permission: "system:user" },
  roles: { title: "角色管理", endpoint: "role", permission: "system:role" },
  departments: {
    title: "部门管理",
    endpoint: "dept",
    permission: "system:dept",
  },
  tenants: {
    title: "租户管理",
    endpoint: "tenant",
    permission: "system:tenant",
  },
  packages: {
    title: "套餐管理",
    endpoint: "tenant-package",
    permission: "system:tenant-package",
  },
  menus: { title: "菜单资源", endpoint: "menu", permission: "system:menu" },
} as const;
export function allowed(permission: string) {
  return !!identities[context.realm]?.permissions.includes(permission);
}
function requireBackend() {
  if (demo || context.realm === "member")
    throw new ApiError("当前入口不支持后台管理操作", 403);
}
export async function managementGet<T>(
  path: string,
  query: Record<string, string | number | boolean | undefined> = {},
) {
  requireBackend();
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(query))
    if (value !== undefined && value !== "") search.set(key, String(value));
  return request<T>(
    `${prefix()}/system/${path}${search.size ? `?${search}` : ""}`,
    credentials(),
  );
}
export async function managementPost<T>(path: string, body: unknown) {
  requireBackend();
  return request<T>(
    `${prefix()}/system/${path}`,
    { ...credentials(), timeoutMs: 40000 },
    { method: "POST", body: JSON.stringify(body) },
  );
}
function row(value: unknown): value is ManagementRow {
  if (!value || typeof value !== "object") return false;
  const candidate = value as ManagementRow;
  return (
    Number.isSafeInteger(candidate.id) &&
    candidate.id > 0 &&
    Number.isSafeInteger(candidate.authVersion) &&
    candidate.authVersion >= 0 &&
    [0, 1].includes(candidate.status) &&
    typeof candidate.deleted === "boolean"
  );
}
export async function managementList(
  kind: ManagementKind,
  query: {
    pageNo: number;
    pageSize: number;
    deleted: boolean;
    name?: string;
    username?: string;
    realm?: string;
    status?: number;
  },
) {
  const base = managementSpec[kind].endpoint;
  const result: unknown = await managementGet(
    `${base}/${kind === "departments" || kind === "menus" ? "list" : "page"}`,
    query,
  );
  if (kind === "departments" || kind === "menus") {
    if (!Array.isArray(result) || !result.every(row))
      throw new ApiError("列表数据不完整，请重新读取", -2);
    return { list: result, total: result.length };
  }
  const page = result as PageResult<ManagementRow>;
  if (
    !page ||
    !Number.isSafeInteger(page.total) ||
    page.total < 0 ||
    !Array.isArray(page.list) ||
    !page.list.every(row)
  )
    throw new ApiError("分页数据不完整，请重新读取", -2);
  return page;
}
export async function navigation() {
  const rows: unknown = await managementGet("menu/navigation");
  if (
    !Array.isArray(rows) ||
    rows.some((value: unknown) => {
      if (!value || typeof value !== "object") return true;
      const m = value as NavigationItem;
      return (
        !Number.isSafeInteger(m.id) ||
        m.id <= 0 ||
        !Number.isSafeInteger(m.parentId) ||
        m.parentId < 0 ||
        !Number.isSafeInteger(m.sort) ||
        typeof m.name !== "string" ||
        typeof m.path !== "string" ||
        typeof m.icon !== "string" ||
        typeof m.permission !== "string" ||
        ![1, 2].includes(m.type)
      );
    })
  )
    throw new ApiError("导航数据无效，请重新登录", -2);
  return rows as NavigationItem[];
}
export function positiveId(value: unknown, label = "编号") {
  const id = Number(value);
  if (!Number.isSafeInteger(id) || id <= 0)
    throw new ApiError(`${label}必须为正整数`, 400);
  return id;
}
