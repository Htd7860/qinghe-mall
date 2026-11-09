<script setup lang="ts">
import {
  computed,
  onMounted,
  onBeforeUnmount,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Modal from "../components/Modal.vue";
import FileAssetsModal from "../components/FileAssetsModal.vue";
import Empty from "../components/Empty.vue";
import { ApiError } from "../api/http";
import { context, identities } from "../api/session";
import {
  allowed,
  managementSpec,
  managementList,
  managementGet,
  managementPost,
  positiveId,
  type ManagementKind,
  type ManagementRow,
  type MenuOption,
  type PackageImpact,
  type PackageChange,
  navigationItems,
} from "../api/management";
const route = useRoute();
const fileAssetsOpen = ref(false);
watch(
  () =>
    `${context.realm}:${identities[context.realm]?.tenantId}:${identities[context.realm]?.userId}`,
  () => {
    fileAssetsOpen.value = false;
  },
);
const kind = computed(
  () => String(route.meta.managementKind) as ManagementKind,
);
const spec = computed(() => managementSpec[kind.value]);
const rows = ref<ManagementRow[]>([]),
  total = ref(0),
  page = ref(1),
  deleted = ref(false),
  search = ref("");
const menuRealm = ref("merchant"),
  statusFilter = ref(""),
  busy = ref(false),
  loading = ref(false);
const error = ref(""),
  notice = ref(""),
  editor = ref(false),
  editRow = ref<ManagementRow>();
const uncertain = ref(false),
  modalError = ref("");
const expiryEdited = ref(false);
const form = reactive<Record<string, string | number | boolean>>({});
const departments = ref<ManagementRow[]>([]),
  packages = ref<ManagementRow[]>([]);
const resourceOptions = ref<MenuOption[]>([]),
  selected = ref<number[]>([]);
const orderedResourceOptions = computed(() => {
  const children = new Map<number, MenuOption[]>();
  for (const option of resourceOptions.value) {
    const group = children.get(option.parentId) || [];
    group.push(option);
    children.set(option.parentId, group);
  }
  for (const group of children.values())
    group.sort((a, b) => a.sort - b.sort || a.id - b.id);
  const result: MenuOption[] = [],
    visited = new Set<number>();
  const visit = (root: MenuOption) => {
    const stack = [root];
    while (stack.length) {
      const option = stack.pop()!;
      if (visited.has(option.id)) continue;
      visited.add(option.id);
      result.push(option);
      const descendants = children.get(option.id) || [];
      for (let i = descendants.length - 1; i >= 0; i--)
        stack.push(descendants[i]!);
    }
  };
  for (const root of children.get(0) || []) visit(root);
  // Keep malformed orphan records visible; validation remains on the server.
  for (const option of resourceOptions.value) visit(option);
  return result;
});
const impact = ref<PackageImpact>(),
  changeHistory = ref<PackageChange[]>([]);
const operationId = ref("");
const confirmation = ref<{
  row: ManagementRow;
  action: "update-status" | "delete" | "restore";
}>();
const grant = ref<{
  row: ManagementRow;
  type: "menus" | "roles" | "scope" | "password";
}>();
const roleOptions = ref<ManagementRow[]>([]),
  password = ref(""),
  dataScope = ref(5);
let generation = 0;
onBeforeUnmount(() => {
  generation++;
});
const platform = computed(() => context.realm === "platform");
const links = computed(
  () =>
    (platform.value
      ? ["staff", "roles", "departments", "tenants", "packages", "menus"]
      : ["staff", "roles", "departments"]
    ).filter(
      (k) =>
        allowed(`${managementSpec[k as ManagementKind].permission}:query`) &&
        navigationItems.value.some(
          (item) =>
            item.type === 2 &&
            [k, `/${platform.value ? "platform" : "admin"}/${k}`].includes(
              item.path,
            ),
        ),
    ) as ManagementKind[],
);
const can = (action: string) => allowed(`${spec.value.permission}:${action}`);
const label = (row: ManagementRow) =>
  String(row.name || row.nickname || row.username || row.id);
const numericArray = (value: unknown) =>
  Array.isArray(value)
    ? value.filter(
        (id): id is number => Number.isSafeInteger(id) && Number(id) > 0,
      )
    : [];
const versioned = (row: ManagementRow) => ({
  id: row.id,
  expectedVersion: row.authVersion,
});
const paginated = computed(
  () => !["departments", "menus"].includes(kind.value),
);
const displayed = computed(() =>
  paginated.value
    ? rows.value
    : rows.value.filter(
        (row) => !search.value || label(row).includes(search.value),
      ),
);
function message(e: unknown) {
  return e instanceof Error ? e.message : "操作未完成，请重新读取后重试";
}
async function load() {
  const current = ++generation;
  loading.value = true;
  error.value = "";
  try {
    const result = await managementList(kind.value, {
      pageNo: page.value,
      pageSize: 20,
      deleted: deleted.value,
      ...(kind.value === "staff"
        ? { username: search.value }
        : { name: search.value }),
      ...(kind.value === "menus" ? { realm: menuRealm.value } : {}),
      ...(statusFilter.value === ""
        ? {}
        : { status: Number(statusFilter.value) }),
    });
    if (current !== generation) return;
    rows.value = result.list;
    total.value = result.total;
  } catch (e) {
    if (current === generation) {
      rows.value = [];
      total.value = 0;
      error.value = message(e);
    }
  } finally {
    if (current === generation) loading.value = false;
  }
}
type Field = {
  key: string;
  label: string;
  type?: string;
  required?: boolean;
  max?: number;
  min?: number;
  options?: { value: string | number; label: string }[];
  locked?: boolean;
};
const states = [
  { value: 0, label: "启用" },
  { value: 1, label: "停用" },
];
const fields = computed<Field[]>(() => {
  const name: Field = {
    key: "name",
    label: "名称",
    required: true,
    max: kind.value === "tenants" ? 128 : kind.value === "menus" ? 50 : 30,
  };
  const status: Field = {
    key: "status",
    label: "状态",
    type: "select",
    options: states,
  };
  if (kind.value === "staff")
    return [
      {
        key: "username",
        label: "登录账号",
        required: true,
        max: 30,
        locked: !!editRow.value,
      },
      { key: "nickname", label: "姓名", required: true, max: 30 },
      ...(!editRow.value
        ? [
            {
              key: "password",
              label: "初始密码",
              type: "password",
              required: true,
              max: 100,
            },
          ]
        : []),
      {
        key: "deptId",
        label: "所属部门",
        type: "select",
        options: [
          { value: "", label: "未分配部门" },
          ...departments.value
            .filter((d) => d.status === 0)
            .map((d) => ({ value: d.id, label: label(d) })),
        ],
      },
      status,
      { key: "email", label: "邮箱", type: "email", max: 50 },
      { key: "mobile", label: "手机号", max: 11 },
      {
        key: "sex",
        label: "性别",
        type: "select",
        options: [
          { value: 0, label: "未设置" },
          { value: 1, label: "男" },
          { value: 2, label: "女" },
        ],
      },
      { key: "remark", label: "备注", type: "textarea", max: 500 },
    ];
  if (kind.value === "roles")
    return [
      name,
      {
        key: "code",
        label: "角色标识",
        required: true,
        max: 100,
        locked: !!editRow.value,
      },
      { key: "sort", label: "排序", type: "number", min: 0, max: 10000 },
      status,
      { key: "remark", label: "备注", type: "textarea", max: 500 },
    ];
  if (kind.value === "departments")
    return [
      name,
      {
        key: "parentId",
        label: "上级部门",
        type: "select",
        options: [
          { value: 0, label: "根部门" },
          ...departments.value
            .filter((d) => d.id !== editRow.value?.id && d.status === 0)
            .map((d) => ({ value: d.id, label: label(d) })),
        ],
      },
      { key: "sort", label: "排序", type: "number", min: 0, max: 10000 },
      status,
    ];
  if (kind.value === "tenants")
    return [
      name,
      {
        key: "code",
        label: "商家编码",
        required: true,
        max: 32,
        locked: !!editRow.value,
      },
      {
        key: "packageId",
        label: "租户套餐",
        type: "select",
        required: true,
        options: packages.value
          .filter((p) => p.status === 0 || p.id === Number(form.packageId))
          .map((p) => ({
            value: p.id,
            label: `${label(p)}${p.status ? "（已停用，仅保留当前关联）" : ""}`,
          })),
      },
      {
        key: "accountCount",
        label: "账号额度（含管理员与停用账号）",
        type: "number",
        required: true,
        min: 1,
        max: 1000000,
      },
      {
        key: "expireTime",
        label: "到期时间",
        type: "datetime-local",
        required: true,
      },
      status,
      { key: "contactName", label: "联系人", max: 64 },
      { key: "contactMobile", label: "联系电话", max: 32 },
      ...(!editRow.value
        ? [
            {
              key: "adminUsername",
              label: "管理员账号",
              required: true,
              max: 30,
            },
            { key: "adminNickname", label: "管理员姓名", max: 30 },
            {
              key: "adminPassword",
              label: "管理员初始密码",
              type: "password",
              required: true,
              max: 100,
            },
          ]
        : []),
    ];
  if (kind.value === "packages")
    return [
      name,
      status,
      { key: "remark", label: "说明", type: "textarea", max: 256 },
    ];
  return [
    name,
    {
      key: "realm",
      label: "所属端",
      type: "select",
      locked: !!editRow.value,
      options: [
        { value: "merchant", label: "商家" },
        { value: "platform", label: "平台" },
      ],
    },
    {
      key: "type",
      label: "资源类型",
      type: "select",
      options: [
        { value: 1, label: "目录" },
        { value: 2, label: "页面菜单" },
        { value: 3, label: "按钮 / 接口权限" },
      ],
    },
    {
      key: "parentId",
      label: "上级资源",
      type: "select",
      options: [
        { value: 0, label: "根目录" },
        ...resourceOptions.value
          .filter((m) => m.id !== editRow.value?.id && m.type !== 3)
          .map((m) => ({ value: m.id, label: m.name })),
      ],
    },
    { key: "permission", label: "权限标识（目录留空）", max: 100 },
    { key: "path", label: "页面路径", max: 200 },
    { key: "component", label: "页面组件标识", max: 255 },
    { key: "componentName", label: "组件名称", max: 255 },
    { key: "icon", label: "图标标识", max: 100 },
    { key: "sort", label: "排序", type: "number", min: 0, max: 10000 },
    status,
    { key: "visible", label: "显示在导航", type: "checkbox" },
    { key: "keepAlive", label: "保留页面状态", type: "checkbox" },
    { key: "alwaysShow", label: "始终显示目录", type: "checkbox" },
  ];
});
function changeField(field: Field, event: Event) {
  const target = event.target as HTMLInputElement;
  form[field.key] =
    field.type === "checkbox"
      ? target.checked
      : field.type === "number"
        ? Number(target.value)
        : target.value;
  if (field.key === "expireTime") expiryEdited.value = true;
  impact.value = undefined;
}
async function openEdit(row?: ManagementRow) {
  if (busy.value) return;
  busy.value = true;
  modalError.value = "";
  error.value = "";
  impact.value = undefined;
  changeHistory.value = [];
  try {
    if (["staff", "departments"].includes(kind.value))
      departments.value =
        await managementGet<ManagementRow[]>("dept/simple-list");
    if (kind.value === "tenants")
      packages.value = await managementGet<ManagementRow[]>(
        "tenant-package/simple-list",
      );
    if (kind.value === "menus")
      resourceOptions.value = await managementGet<MenuOption[]>("menu/list", {
        realm: menuRealm.value,
        deleted: false,
      });
    if (kind.value === "packages")
      resourceOptions.value = await managementGet<MenuOption[]>(
        "tenant-package/menu-options",
      );
    editRow.value = row;
    expiryEdited.value = false;
    for (const key of Object.keys(form)) delete form[key];
    Object.assign(form, {
      name: "",
      username: "",
      nickname: "",
      status: 0,
      sort: 0,
      remark: "",
      deptId: "",
      parentId: 0,
      email: "",
      mobile: "",
      sex: 0,
      code: "",
      accountCount: 10,
      packageId: "",
      expireTime: "",
      contactName: "",
      contactMobile: "",
      adminUsername: "",
      adminNickname: "",
      adminPassword: "",
      password: "",
      realm: menuRealm.value,
      type: 1,
      permission: "",
      path: "",
      component: "",
      componentName: "",
      icon: "",
      visible: true,
      keepAlive: false,
      alwaysShow: false,
    });
    if (row)
      for (const field of fields.value) {
        const value = row[field.key];
        if (
          typeof value === "string" ||
          typeof value === "number" ||
          typeof value === "boolean"
        )
          form[field.key] = value;
        else if (field.key === "deptId") form.deptId = "";
      }
    if (typeof form.expireTime === "string")
      form.expireTime = form.expireTime.slice(0, 19);
    if (row?.deptId && !departments.value.some((d) => d.id === row.deptId)) {
      departments.value.push({
        id: positiveId(row.deptId),
        name: `部门 #${row.deptId}`,
        status: 0,
        authVersion: 0,
        deleted: false,
      });
    }
    selected.value = numericArray(row?.menuIds);
    operationId.value = "";
    uncertain.value = false;
    editor.value = true;
    if (row && kind.value === "packages")
      changeHistory.value = await managementGet<PackageChange[]>(
        "tenant-package/change-history",
        { packageId: row.id },
      );
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
function body() {
  const out: Record<string, unknown> = editRow.value
    ? versioned(editRow.value)
    : {};
  for (const field of fields.value) {
    const value = form[field.key];
    if (field.required && (value === "" || value === undefined))
      throw new ApiError(`请填写${field.label}`, 400);
    if (["deptId", "packageId", "parentId"].includes(field.key))
      out[field.key] = value === "" ? null : Number(value);
    else out[field.key] = value;
    if (
      (field.type === "number" ||
        [
          "deptId",
          "packageId",
          "parentId",
          "sort",
          "status",
          "sex",
          "type",
        ].includes(field.key)) &&
      out[field.key] !== null
    ) {
      const n = Number(out[field.key]);
      if (!Number.isSafeInteger(n))
        throw new ApiError(`${field.label}必须为整数`, 400);
      out[field.key] = n;
    }
  }
  if (kind.value === "packages")
    out.menuIds = [...selected.value].sort((a, b) => a - b);
  if (kind.value === "tenants" && editRow.value && !expiryEdited.value)
    out.expireTime = editRow.value.expireTime;
  return out;
}
async function preview() {
  if (!editRow.value || busy.value) return;
  busy.value = true;
  modalError.value = "";
  try {
    impact.value = await managementPost<PackageImpact>(
      "tenant-package/preview-change",
      { ...versioned(editRow.value), menuIds: selected.value },
    );
  } catch (e) {
    modalError.value = message(e);
  } finally {
    busy.value = false;
  }
}
const pendingKey = () =>
  `qinghe:package-change:${context.realm}:${identities[context.realm]?.userId}`;
const pending = ref<Record<string, unknown>>();
function persistPending(value: Record<string, unknown>) {
  try {
    sessionStorage.setItem(pendingKey(), JSON.stringify(value));
    pending.value = value;
  } catch {
    throw new ApiError("无法保存操作编号，请检查浏览器存储后重试", 400);
  }
}
function clearPending() {
  try {
    sessionStorage.removeItem(pendingKey());
  } catch {
    /* Result is already known. */
  }
  pending.value = undefined;
}
async function recover(retry = false) {
  if (!pending.value || busy.value) return;
  busy.value = true;
  error.value = "";
  let retriedOriginal = false;
  try {
    const id = String(pending.value.operationId);
    let result = await managementGet<PackageChange | null>(
      "tenant-package/change-result",
      { operationId: id },
    );
    if (!result && retry) {
      retriedOriginal = true;
      result = await managementPost<PackageChange>(
        "tenant-package/update",
        pending.value,
      );
    }
    if (!result) {
      notice.value = "结果尚未确认。可使用原操作编号重新确认；不要创建新操作。";
      return;
    }
    clearPending();
    uncertain.value = false;
    editor.value = false;
    notice.value = `套餐变更已完成，版本 ${result.fromVersion} → ${result.toVersion}`;
    await load();
  } catch (e) {
    error.value = message(e);
    if (
      retriedOriginal &&
      e instanceof ApiError &&
      [400, 404, 409].includes(e.code) &&
      !e.outcomeUnknown
    ) {
      clearPending();
      uncertain.value = false;
      editor.value = false;
      notice.value =
        "原提交未完成，最新校验已拒绝本次重试。请重新读取并预览后发起新操作。";
    }
  } finally {
    busy.value = false;
  }
}
async function save() {
  if (busy.value || uncertain.value || pending.value) return;
  busy.value = true;
  modalError.value = "";
  let packageWrite = false;
  try {
    const payload = body();
    if (kind.value === "packages" && editRow.value) {
      if (!impact.value) throw new ApiError("请先预览套餐影响范围", 400);
      operationId.value ||= crypto.randomUUID();
      payload.operationId = operationId.value;
      payload.impactToken = impact.value.impactToken;
      persistPending(payload);
      packageWrite = true;
    }
    await managementPost(
      `${spec.value.endpoint}/${editRow.value ? "update" : "create"}`,
      payload,
    );
    if (packageWrite) clearPending();
    editor.value = false;
    password.value = "";
    form.password = "";
    form.adminPassword = "";
    notice.value = "已保存";
    await load();
  } catch (e) {
    modalError.value = message(e);
    if (e instanceof ApiError && e.outcomeUnknown) {
      uncertain.value = true;
      notice.value = "处理结果待确认，请先核对列表或查询原操作结果。";
    } else if (packageWrite) {
      clearPending();
      operationId.value = "";
      impact.value = undefined;
    }
  } finally {
    busy.value = false;
  }
}
function closeEditor() {
  if (busy.value) return;
  editor.value = false;
  form.password = "";
  form.adminPassword = "";
}
async function perform() {
  const action = confirmation.value;
  if (!action || busy.value) return;
  busy.value = true;
  modalError.value = "";
  try {
    await managementPost(`${spec.value.endpoint}/${action.action}`, {
      ...versioned(action.row),
      ...(action.action === "update-status"
        ? { status: action.row.status === 0 ? 1 : 0 }
        : {}),
    });
    confirmation.value = undefined;
    notice.value = "操作已完成";
    await load();
  } catch (e) {
    modalError.value = message(e);
    if (e instanceof ApiError && e.outcomeUnknown) {
      confirmation.value = undefined;
      notice.value = "结果待确认，请重新读取列表，核对状态与版本后再操作。";
      await load();
    }
  } finally {
    busy.value = false;
  }
}
async function openGrant(
  row: ManagementRow,
  type: "menus" | "roles" | "scope" | "password",
) {
  if (busy.value) return;
  busy.value = true;
  modalError.value = "";
  error.value = "";
  password.value = "";
  try {
    selected.value = [];
    if (type === "menus") {
      resourceOptions.value =
        await managementGet<MenuOption[]>("menu/simple-list");
      selected.value = await managementGet<number[]>(
        "permission/list-role-menus",
        { roleId: row.id },
      );
    } else if (type === "roles") {
      roleOptions.value =
        await managementGet<ManagementRow[]>("role/simple-list");
      selected.value = await managementGet<number[]>(
        "permission/list-user-roles",
        { userId: row.id },
      );
    } else if (type === "scope") {
      departments.value =
        await managementGet<ManagementRow[]>("dept/simple-list");
      dataScope.value = Number(row.dataScope || 5);
      selected.value = numericArray(row.dataScopeDeptIds);
    }
    grant.value = { row, type };
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
function choose(id: number, checked: boolean, tree: boolean) {
  const values = new Set(selected.value);
  if (checked) {
    values.add(id);
    if (tree) {
      let current = resourceOptions.value.find((m) => m.id === id);
      const visited = new Set<number>();
      while (
        current &&
        current.parentId > 0 &&
        !visited.has(current.parentId)
      ) {
        visited.add(current.parentId);
        values.add(current.parentId);
        const parentId: number = current.parentId;
        current = resourceOptions.value.find((m) => m.id === parentId);
      }
    }
  } else {
    values.delete(id);
    if (tree) {
      for (const option of resourceOptions.value) {
        let current: MenuOption | undefined = option;
        const visited = new Set<number>();
        while (
          current &&
          current.parentId > 0 &&
          !visited.has(current.parentId)
        ) {
          if (current.parentId === id) {
            values.delete(option.id);
            break;
          }
          visited.add(current.parentId);
          const parentId: number = current.parentId;
          current = resourceOptions.value.find((m) => m.id === parentId);
        }
      }
    }
  }
  selected.value = [...values];
  impact.value = undefined;
}
function checkOption(id: number, event: Event, tree = false) {
  choose(id, (event.target as HTMLInputElement).checked, tree);
}
function depth(option: MenuOption) {
  let n = 0,
    current = option;
  const seen = new Set<number>();
  while (current.parentId > 0 && !seen.has(current.parentId)) {
    seen.add(current.parentId);
    const parent = resourceOptions.value.find((m) => m.id === current.parentId);
    if (!parent) break;
    current = parent;
    n++;
  }
  return Math.min(n, 8);
}
async function saveGrant() {
  const current = grant.value;
  if (!current || busy.value) return;
  busy.value = true;
  modalError.value = "";
  try {
    const { row, type } = current;
    if (type === "password")
      await managementPost("user/update-password", {
        ...versioned(row),
        password: password.value,
      });
    if (type === "roles")
      await managementPost("permission/assign-user-role", {
        userId: row.id,
        expectedVersion: row.authVersion,
        roleIds: selected.value,
      });
    if (type === "menus")
      await managementPost("permission/assign-role-menu", {
        roleId: row.id,
        expectedVersion: row.authVersion,
        menuIds: selected.value,
      });
    if (type === "scope")
      await managementPost("permission/assign-role-data-scope", {
        ...versioned(row),
        dataScope: dataScope.value,
        deptIds: dataScope.value === 2 ? selected.value : [],
      });
    grant.value = undefined;
    password.value = "";
    notice.value = "已保存";
    await load();
  } catch (e) {
    modalError.value = message(e);
    password.value = "";
    if (e instanceof ApiError && e.outcomeUnknown) {
      grant.value = undefined;
      notice.value = "结果待确认，请重新读取人员或角色状态后再操作。";
      await load();
    }
  } finally {
    busy.value = false;
  }
}
function checkRestore(row: ManagementRow) {
  modalError.value = "";
  confirmation.value = { row, action: "restore" };
}
onMounted(() => {
  try {
    if (kind.value === "packages") {
      const value = JSON.parse(sessionStorage.getItem(pendingKey()) || "null");
      if (value && typeof value.operationId === "string") pending.value = value;
    }
  } catch {
    notice.value = "未能读取待确认操作，请核对套餐变更记录。";
  }
  void load();
});
watch(menuRealm, () => {
  page.value = 1;
  void load();
});
watch(
  () => form.realm,
  async (realm, previous) => {
    if (
      kind.value !== "menus" ||
      !editor.value ||
      editRow.value ||
      realm === previous
    )
      return;
    form.parentId = 0;
    resourceOptions.value = [];
    try {
      const options = await managementGet<MenuOption[]>("menu/list", {
        realm: String(realm),
        deleted: false,
      });
      if (form.realm === realm && editor.value) resourceOptions.value = options;
    } catch (e) {
      if (form.realm === realm && editor.value) {
        modalError.value = message(e);
        resourceOptions.value = [];
      }
    }
  },
);
</script>

<template>
  <div class="management-page">
    <PageHeader :title="spec.title">
      <button
        v-if="allowed('infra:file:query')"
        class="button"
        :disabled="busy || loading || !!pending || editor"
        @click="fileAssetsOpen = true"
      >
        图片素材
      </button>
      <button
        v-if="can('create') && !deleted"
        class="button primary"
        :disabled="busy || loading || !!pending"
        @click="openEdit()"
      >
        {{ kind === "tenants" ? "开通租户" : "新增" }}
      </button>
    </PageHeader>
    <FileAssetsModal
      v-if="fileAssetsOpen && allowed('infra:file:query')"
      @close="fileAssetsOpen = false"
    />
    <nav class="management-tabs" aria-label="管理分类">
      <RouterLink
        v-for="item in links"
        :key="item"
        :to="`/${platform ? 'platform' : 'admin'}/${item}`"
        :aria-current="item === kind ? 'page' : undefined"
        >{{ managementSpec[item].title }}</RouterLink
      >
    </nav>
    <div v-if="pending" class="management-result" role="status">
      <strong>套餐变更结果待确认</strong
      ><span>操作编号：{{ pending.operationId }}</span>
      <button class="button" :disabled="busy" @click="recover()">
        查询结果
      </button>
      <button class="button" :disabled="busy" @click="recover(true)">
        使用原操作编号重新确认
      </button>
    </div>
    <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
    <div class="panel">
      <form
        class="filter-bar"
        @submit.prevent="
          page = 1;
          load();
        "
      >
        <label class="management-filter"
          >{{ kind === "staff" ? "登录账号" : "名称"
          }}<input
            v-model.trim="search"
            :placeholder="kind === 'staff' ? '搜索账号' : '搜索名称'"
            :maxlength="kind === 'staff' ? 30 : 128"
        /></label>
        <label v-if="kind === 'menus'" class="management-filter"
          >所属端<select v-model="menuRealm">
            <option value="merchant">商家资源</option>
            <option value="platform">平台资源</option>
          </select></label
        >
        <label class="management-filter"
          >记录<select v-model="deleted">
            <option :value="false">当前记录</option>
            <option :value="true">已删除</option>
          </select></label
        >
        <label v-if="paginated" class="management-filter"
          >状态<select v-model="statusFilter">
            <option value="">全部状态</option>
            <option value="0">启用</option>
            <option value="1">停用</option>
          </select></label
        >
        <button class="button" :disabled="loading || busy">查询</button>
      </form>
      <div v-if="error" class="management-alert" role="alert">
        {{ error }}<button class="text-link" @click="load()">重新读取</button>
      </div>
      <div v-if="loading" class="management-empty" role="status">正在读取…</div>
      <Empty
        v-else-if="!displayed.length && !error"
        :title="deleted ? '暂无已删除记录' : '暂无记录'"
      />
      <div v-else-if="displayed.length" class="management-table-scroll">
        <table class="management-table">
          <thead>
            <tr>
              <th>{{ kind === "staff" ? "员工" : "名称" }}</th>
              <th>标识 / 归属</th>
              <th>状态</th>
              <th>版本</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in displayed" :key="row.id">
              <td>
                <strong>{{ label(row) }}</strong
                ><small>#{{ row.id }}</small>
              </td>
              <td>
                {{
                  row.username ||
                  row.code ||
                  (row.realm === "merchant"
                    ? "商家"
                    : row.realm === "platform"
                      ? "平台"
                      : "") ||
                  row.parentId ||
                  "—"
                }}<small v-if="row.packageId"
                  >套餐 #{{ row.packageId }} · 账号额度
                  {{ row.accountCount }}</small
                ><small v-if="row.expireTime"
                  >到期 {{ String(row.expireTime).replace("T", " ") }}</small
                >
              </td>
              <td>
                <span
                  :class="[
                    'management-state',
                    row.deleted
                      ? 'deleted'
                      : row.status
                        ? 'disabled'
                        : 'enabled',
                  ]"
                  >{{
                    row.deleted ? "已删除" : row.status ? "停用" : "启用"
                  }}</span
                ><small v-if="kind === 'menus'">{{
                  row.visible ? "导航可见" : "导航隐藏"
                }}</small>
              </td>
              <td>{{ row.authVersion }}</td>
              <td>
                <div class="management-row-actions">
                  <template v-if="!row.deleted">
                    <button
                      v-if="
                        can('update') && !(kind === 'roles' && row.type === 1)
                      "
                      class="text-link"
                      :disabled="busy"
                      @click="openEdit(row)"
                    >
                      编辑
                    </button>
                    <button
                      v-if="
                        can('update') && !(kind === 'roles' && row.type === 1)
                      "
                      class="text-link"
                      :disabled="busy"
                      @click="
                        modalError = '';
                        confirmation = { row, action: 'update-status' };
                      "
                    >
                      {{ row.status ? "启用" : "停用" }}
                    </button>
                    <button
                      v-if="
                        kind === 'staff' &&
                        allowed('system:permission:assign-user-role') &&
                        true
                      "
                      class="text-link"
                      :disabled="busy"
                      @click="openGrant(row, 'roles')"
                    >
                      分配角色
                    </button>
                    <button
                      v-if="
                        kind === 'staff' &&
                        allowed('system:user:update-password')
                      "
                      class="text-link"
                      :disabled="busy"
                      @click="openGrant(row, 'password')"
                    >
                      重置密码
                    </button>
                    <button
                      v-if="
                        kind === 'roles' &&
                        row.type !== 1 &&
                        allowed('system:permission:assign-role-menu')
                      "
                      class="text-link"
                      :disabled="busy"
                      @click="openGrant(row, 'menus')"
                    >
                      资源授权
                    </button>
                    <button
                      v-if="
                        kind === 'roles' &&
                        row.type !== 1 &&
                        allowed('system:permission:assign-role-data-scope') &&
                        true
                      "
                      class="text-link"
                      :disabled="busy"
                      @click="openGrant(row, 'scope')"
                    >
                      数据范围
                    </button>
                    <button
                      v-if="
                        can('delete') && !(kind === 'roles' && row.type === 1)
                      "
                      class="text-link management-danger"
                      :disabled="busy"
                      @click="
                        modalError = '';
                        confirmation = { row, action: 'delete' };
                      "
                    >
                      删除
                    </button>
                  </template>
                  <button
                    v-else-if="can('restore')"
                    class="text-link"
                    :disabled="busy"
                    @click="checkRestore(row)"
                  >
                    恢复
                  </button>
                  <span v-if="kind === 'roles' && row.type === 1" class="muted"
                    >开通流程维护</span
                  >
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>共 {{ total }} 条</span>
        <div v-if="paginated" class="pagination-right">
          <button
            class="button"
            :disabled="page <= 1 || loading || busy"
            @click="
              page--;
              load();
            "
          >
            上一页</button
          ><span>第 {{ page }} 页</span
          ><button
            class="button"
            :disabled="page * 20 >= total || loading || busy"
            @click="
              page++;
              load();
            "
          >
            下一页
          </button>
        </div>
      </div>
    </div>
    <Modal
      v-if="editor"
      :title="`${editRow ? '编辑' : kind === 'tenants' ? '开通' : '新增'}${spec.title.replace('管理', '')}`"
      wide
      @close="closeEditor"
    >
      <form id="management-editor" @submit.prevent="save">
        <fieldset
          class="management-fieldset"
          :disabled="busy || uncertain || !!pending"
        >
          <div class="form-grid">
            <label
              v-for="field in fields"
              :key="field.key"
              :class="{ 'management-checkbox': field.type === 'checkbox' }"
              >{{ field.label }}
              <select
                v-if="field.type === 'select'"
                :value="form[field.key]"
                :required="field.required"
                :disabled="field.locked"
                @change="changeField(field, $event)"
              >
                <option
                  v-for="option in field.options"
                  :key="String(option.value)"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
              <textarea
                v-else-if="field.type === 'textarea'"
                :value="String(form[field.key] ?? '')"
                :maxlength="field.max"
                rows="3"
                @input="changeField(field, $event)"
              />
              <input
                v-else-if="field.type === 'checkbox'"
                type="checkbox"
                :checked="!!form[field.key]"
                @change="changeField(field, $event)"
              />
              <input
                v-else
                :type="field.type || 'text'"
                :step="field.type === 'datetime-local' ? 1 : undefined"
                :value="form[field.key]"
                :required="field.required"
                :readonly="field.locked"
                :maxlength="field.type !== 'number' ? field.max : undefined"
                :min="field.min"
                :max="field.type === 'number' ? field.max : undefined"
                :autocomplete="
                  field.type === 'password' ? 'new-password' : 'off'
                "
                @input="changeField(field, $event)"
              />
            </label>
          </div>
          <template v-if="kind === 'packages'">
            <h3 class="management-section-title">套餐资源</h3>
            <p class="muted">
              勾选资源会同时选中上级；取消上级会取消下级。新增资源必须处于启用状态。
            </p>
            <div class="management-checklist">
              <label
                v-for="option in orderedResourceOptions"
                :key="option.id"
                :style="{ paddingLeft: `${12 + depth(option) * 18}px` }"
                ><input
                  type="checkbox"
                  :checked="selected.includes(option.id)"
                  :disabled="
                    option.status === 1 && !selected.includes(option.id)
                  "
                  @change="checkOption(option.id, $event, true)"
                /><span
                  >{{ option.name
                  }}<small
                    >{{ option.permission
                    }}{{ option.status ? " · 已停用" : "" }}</small
                  ></span
                ></label
              >
            </div>
            <button
              v-if="editRow"
              class="button"
              type="button"
              @click="preview"
            >
              预览影响范围
            </button>
            <div v-if="impact" class="management-impact">
              <strong
                >影响 {{ impact.affectedTenants }} 个租户、{{
                  impact.changedRoles
                }}
                个角色</strong
              >
              <p>
                新增 {{ impact.addedBindings }} 项绑定，移除
                {{ impact.removedBindings }}
                项绑定。普通角色不会自动获得新增权限。
              </p>
              <ul>
                <li v-for="tenant in impact.tenants" :key="tenant.tenantId">
                  {{ tenant.code
                  }}{{ tenant.deleted ? "（已删除，可恢复）" : "" }}：{{
                    tenant.changedRoles
                  }}
                  个角色，新增 {{ tenant.addedBindings }} / 移除
                  {{ tenant.removedBindings }}
                </li>
              </ul>
            </div>
          </template>
        </fieldset>
        <p v-if="modalError" class="form-error" role="alert">
          {{ modalError }}
        </p>
        <p v-if="uncertain" class="management-alert" role="alert">
          处理结果待确认，本次编辑已停止提交。请关闭后核对最新列表{{
            pending ? "并查询原操作结果" : ""
          }}。
        </p>
      </form>
      <details v-if="changeHistory.length" class="management-history">
        <summary>最近套餐变更记录</summary>
        <ul>
          <li v-for="item in changeHistory" :key="item.operationId">
            {{ item.createTime.replace("T", " ") }} · 版本
            {{ item.fromVersion }} → {{ item.toVersion
            }}<small>{{ item.operationId }}</small>
          </li>
        </ul>
      </details>
      <template #footer
        ><button class="button" :disabled="busy" @click="closeEditor">
          关闭</button
        ><button
          class="button primary"
          form="management-editor"
          :disabled="
            busy ||
            uncertain ||
            !!pending ||
            (kind === 'packages' && !!editRow && !impact)
          "
        >
          {{ busy ? "保存中…" : "保存" }}
        </button></template
      >
    </Modal>
    <Modal
      v-if="confirmation"
      title="确认操作"
      @close="!busy && (confirmation = undefined)"
    >
      <p>
        确认{{
          confirmation.action === "delete"
            ? "删除"
            : confirmation.action === "restore"
              ? "恢复"
              : confirmation.row.status
                ? "启用"
                : "停用"
        }}「{{ label(confirmation.row) }}」？
      </p>
      <p v-if="confirmation.action === 'restore'" class="muted">
        恢复沿用原编号，默认停用；员工和角色的旧授权、旧会话不会自动恢复。
      </p>
      <p v-if="confirmation.action === 'delete'" class="muted">
        删除后保留原标识与历史记录；引用限制由保存时核对。
      </p>
      <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
      <template #footer
        ><button
          class="button"
          :disabled="busy"
          @click="confirmation = undefined"
        >
          取消</button
        ><button class="button primary" :disabled="busy" @click="perform">
          {{ busy ? "处理中…" : "确认" }}
        </button></template
      >
    </Modal>
    <Modal
      v-if="grant"
      :title="`${label(grant.row)} · ${grant.type === 'menus' ? '资源授权' : grant.type === 'roles' ? '分配角色' : grant.type === 'scope' ? '数据范围' : '重置密码'}`"
      @close="!busy && ((grant = undefined), (password = ''))"
    >
      <fieldset class="management-fieldset" :disabled="busy">
        <label v-if="grant.type === 'password'"
          >新密码<input
            v-model="password"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="100"
          /><small>至少8个字符；保存后原有登录凭据失效。</small></label
        >
        <template v-else-if="grant.type === 'scope'"
          ><label
            >可管理人员范围<select v-model="dataScope">
              <option :value="5">仅本人</option>
              <option :value="3">本部门</option>
              <option :value="4">本部门及下级</option>
              <option :value="2">指定部门</option>
              <option :value="1">本租户全部</option>
            </select></label
          >
          <div v-if="dataScope === 2" class="management-checklist">
            <label
              v-for="dept in departments.filter((d) => d.status === 0)"
              :key="dept.id"
              ><input
                type="checkbox"
                :checked="selected.includes(dept.id)"
                @change="checkOption(dept.id, $event)"
              />{{ label(dept) }}</label
            >
          </div></template
        >
        <div v-else-if="grant.type === 'roles'" class="management-checklist">
          <label v-for="role in roleOptions" :key="role.id"
            ><input
              type="checkbox"
              :checked="selected.includes(role.id)"
              :disabled="role.status === 1 && !selected.includes(role.id)"
              @change="checkOption(role.id, $event)"
            />{{ label(role) }}{{ role.status ? "（已停用）" : "" }}</label
          >
        </div>
        <div v-else class="management-checklist">
          <label
            v-for="option in orderedResourceOptions"
            :key="option.id"
            :style="{ paddingLeft: `${12 + depth(option) * 18}px` }"
            ><input
              type="checkbox"
              :checked="selected.includes(option.id)"
              :disabled="option.status === 1 && !selected.includes(option.id)"
              @change="checkOption(option.id, $event, true)"
            /><span
              >{{ option.name
              }}<small
                >{{ option.permission
                }}{{ option.status ? " · 已停用" : "" }}</small
              ></span
            ></label
          >
        </div>
      </fieldset>
      <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
      <template #footer
        ><button
          class="button"
          :disabled="busy"
          @click="
            grant = undefined;
            password = '';
          "
        >
          取消</button
        ><button class="button primary" :disabled="busy" @click="saveGrant">
          保存
        </button></template
      >
    </Modal>
  </div>
</template>
