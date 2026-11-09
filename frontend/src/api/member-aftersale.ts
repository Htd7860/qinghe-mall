import { ApiError, request, type PageResult } from "./http";
import { context, credentials, demo, identities } from "./session";
import { memberIdentity } from "./member-profile";
import {
  integer,
  record,
  text,
  uuid,
  states,
  summary,
  detail,
  log,
  type AfterSaleSummary,
  type AfterSaleDetail,
  type AfterSaleLog,
  type ReturnAddress,
  type Decision,
} from "./aftersale-contract";

export type ApplyCommand = {
  requestId: string;
  orderId: number;
  orderItemId: number;
  expectedVersion: number;
  count: number;
  way: number;
  applyReason: string;
  applyDescription: string;
};
export type MemberDecisionCommand = {
  id: number;
  expectedVersion: number;
  requestId: string;
  reason: string;
  qualityApproved: false;
  logisticsId?: number;
  logisticsNo?: string;
};
export type ApplyPending = {
  kind: "create";
  orderId: number;
  orderNo: string;
  orderItemId: number;
  requestId: string;
  expectedVersion: number;
  count: number;
  way: number;
};
export type DecisionPending = {
  kind: "delivery" | "cancel";
  id: number;
  no: string;
  requestId: string;
  expectedVersion: number;
  toStatus: number;
};
export type MemberAfterSalePending = ApplyPending | DecisionPending;
export type Carrier = { id: number; code: string; name: string };
export type ApplyResult = {
  id: number;
  no: string;
  status: number;
  count: number;
  goodsRefundPrice: number;
};
export { memberIdentity as afterSaleMemberIdentity };

async function call(path: string, init: RequestInit = {}) {
  const member = identities.member;
  if (
    demo ||
    context.realm !== "member" ||
    !member ||
    member.tenantCode !== context.tenant
  )
    throw new ApiError("请从当前商城登录", 401);
  const actor = memberIdentity(),
    bearer = credentials(),
    store = member.tenantCode;
  if (!bearer.accessToken) throw new ApiError("请重新登录", 401);
  const value = await request<unknown>(
    `/app-api/trade/after-sale/${path}${path.includes("?") ? "&" : "?"}store=${encodeURIComponent(store)}`,
    { ...bearer, timeoutMs: 15000 },
    init,
  );
  if (
    actor !== memberIdentity() ||
    bearer.accessToken !== credentials().accessToken
  )
    throw new ApiError("登录信息已变化，请查询原售后", 409, !!init.method);
  return value;
}
export async function readMemberAfterSales(pageNo: number, status: string) {
  if (!integer(pageNo, 1) || (status && !states.includes(Number(status))))
    throw new ApiError("售后分页条件不正确", 400);
  const query = new URLSearchParams({ pageNo: String(pageNo), pageSize: "20" });
  if (status) query.set("status", status);
  const value = await call(`page?${query}`);
  if (
    !record(value) ||
    !integer(value.total) ||
    !Array.isArray(value.list) ||
    value.list.length > 20 ||
    !value.list.every(summary) ||
    new Set(value.list.map((x) => x.id)).size !== value.list.length
  )
    throw new ApiError("本人售后列表不完整", -2);
  return value as PageResult<AfterSaleSummary>;
}
export async function readMemberAfterSale(
  id: number,
): Promise<AfterSaleDetail> {
  if (!integer(id, 1)) throw new ApiError("售后编号不正确", 400);
  const value = await call(`get?id=${id}`);
  if (!detail(value) || value.id !== id)
    throw new ApiError("本人售后详情不完整", -2);
  return value;
}
export async function readMemberAfterSaleLogs(id: number, pageNo: number) {
  if (!integer(id, 1) || !integer(pageNo, 1))
    throw new ApiError("记录分页不正确", 400);
  const value = await call(
    `log-page?${new URLSearchParams({ id: String(id), pageNo: String(pageNo), pageSize: "20" })}`,
  );
  if (
    !record(value) ||
    !integer(value.total) ||
    !Array.isArray(value.list) ||
    value.list.length > 20 ||
    !value.list.every(log) ||
    new Set(value.list.map((x) => x.id)).size !== value.list.length
  )
    throw new ApiError("本人售后记录不完整", -2);
  return value as PageResult<AfterSaleLog>;
}
export async function memberAfterSaleApplicationResult(
  marker: ApplyPending,
): Promise<AfterSaleSummary | null> {
  const value = await call(
    `application-result?requestId=${encodeURIComponent(marker.requestId)}`,
  );
  if (value === null) return null;
  if (
    !summary(value) ||
    value.orderId !== marker.orderId ||
    value.orderItemId !== marker.orderItemId ||
    value.count !== marker.count ||
    value.way !== marker.way
  )
    throw new ApiError("原申请结果不完整，请保留待确认记录", -2);
  return value;
}
export async function memberAfterSaleDecisionResult(
  marker: DecisionPending,
): Promise<Decision | null> {
  const value = await call(
    `decision-result?requestId=${encodeURIComponent(marker.requestId)}`,
  );
  if (value === null) return null;
  if (
    !record(value) ||
    value.id !== marker.id ||
    value.status !== marker.toStatus ||
    value.authVersion !== marker.expectedVersion + 1
  )
    throw new ApiError("原操作结果不完整，请保留待确认记录", -2);
  return value as Decision;
}
export async function createMemberAfterSale(body: ApplyCommand) {
  if (
    !uuid(body.requestId) ||
    !integer(body.orderId, 1) ||
    !integer(body.orderItemId, 1) ||
    !integer(body.expectedVersion) ||
    body.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !integer(body.count, 1) ||
    body.count > 9999 ||
    ![10, 20].includes(body.way) ||
    !body.applyReason.trim() ||
    body.applyReason.length > 200 ||
    /[\x00-\x1f\x7f]/.test(body.applyReason) ||
    body.applyDescription.length > 500 ||
    /[\x00-\x09\x0b\x0c\x0e-\x1f\x7f]/.test(body.applyDescription)
  )
    throw new ApiError("请检查申请商品、数量和原因", 400);
  const value = await call("create", {
    method: "POST",
    body: JSON.stringify(body),
  });
  if (
    !record(value) ||
    !integer(value.id, 1) ||
    typeof value.no !== "string" ||
    !/^AS[0-9a-f]{32}$/.test(value.no) ||
    typeof value.status !== "number" ||
    !states.includes(value.status) ||
    value.count !== body.count ||
    !integer(value.goodsRefundPrice)
  )
    throw new ApiError("申请结果待确认，请查询原申请", -2, true);
  return value as ApplyResult;
}
export async function changeMemberAfterSale(
  action: "delivery" | "cancel",
  body: MemberDecisionCommand,
) {
  if (
    !integer(body.id, 1) ||
    !integer(body.expectedVersion) ||
    body.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !uuid(body.requestId) ||
    !body.reason.trim() ||
    body.reason.length > 500 ||
    /[\x00-\x1f\x7f]/.test(body.reason) ||
    body.qualityApproved !== false
  )
    throw new ApiError("请检查售后操作及原因", 400);
  if (
    action === "delivery" &&
    (body.reason !== "买家提交退货物流" ||
      !integer(body.logisticsId, 1) ||
      typeof body.logisticsNo !== "string" ||
      !/^[A-Za-z0-9][A-Za-z0-9-]{3,63}$/.test(body.logisticsNo))
  )
    throw new ApiError("请选择承运商并填写有效物流单号", 400);
  if (!["delivery", "cancel"].includes(action))
    throw new ApiError("售后操作不正确", 400);
  const value = await call(action, {
    method: "POST",
    body: JSON.stringify(body),
  });
  if (
    !record(value) ||
    value.id !== body.id ||
    value.status !== (action === "delivery" ? 30 : 61) ||
    value.authVersion !== body.expectedVersion + 1
  )
    throw new ApiError("处理结果待确认，请查询原操作", -2, true);
  return value as Decision;
}
export async function memberReturnAddress(id: number): Promise<ReturnAddress> {
  if (!integer(id, 1)) throw new ApiError("售后编号不正确", 400);
  const value = await call(`return-address?id=${id}`);
  if (
    !record(value) ||
    !text(value.name, 50) ||
    !value.name.trim() ||
    typeof value.mobile !== "string" ||
    !/^1[3-9][0-9]{9}$/.test(value.mobile) ||
    !integer(value.areaId, 1) ||
    !text(value.detail, 255) ||
    !value.detail.trim()
  )
    throw new ApiError("原寄回地址不完整", -2);
  return value as ReturnAddress;
}
export async function memberAfterSaleCarriers(): Promise<Carrier[]> {
  const value = await call("express-options");
  if (
    !Array.isArray(value) ||
    value.length > 200 ||
    !value.every(
      (x) =>
        record(x) &&
        integer(x.id, 1) &&
        typeof x.code === "string" &&
        /^[A-Z0-9_-]{2,32}$/.test(x.code) &&
        text(x.name, 64) &&
        !!x.name.trim(),
    ) ||
    new Set(value.map((x) => x.id)).size !== value.length
  )
    throw new ApiError("承运商选项不完整", -2);
  return value as Carrier[];
}

function key() {
  return `qinghe:member-aftersale-pending:v1:${memberIdentity()}`;
}
export function readMemberAfterSalePending(): MemberAfterSalePending | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(key());
  } catch {
    throw new ApiError("无法读取待确认记录，请检查浏览器隐私设置", -2);
  }
  if (raw === null) return null;
  let x: unknown;
  try {
    x = raw.length <= 600 ? JSON.parse(raw) : null;
  } catch {
    x = null;
  }
  if (
    !record(x) ||
    !uuid(x.requestId) ||
    !integer(x.expectedVersion) ||
    x.expectedVersion >= Number.MAX_SAFE_INTEGER
  )
    throw new ApiError("待确认售后记录无法读取，请先核对原订单和售后历史", -2);
  if (x.kind === "create") {
    if (
      Object.keys(x).sort().join(",") !==
        "count,expectedVersion,kind,orderId,orderItemId,orderNo,requestId,way" ||
      !integer(x.orderId, 1) ||
      !integer(x.orderItemId, 1) ||
      typeof x.orderNo !== "string" ||
      !/^QH[0-9a-f]{32}$/.test(x.orderNo) ||
      !integer(x.count, 1) ||
      x.count > 9999 ||
      typeof x.way !== "number" ||
      ![10, 20].includes(x.way)
    )
      throw new ApiError("原申请记录不完整，请先核对本人订单", -2);
  } else if (
    !["delivery", "cancel"].includes(String(x.kind)) ||
    Object.keys(x).sort().join(",") !==
      "expectedVersion,id,kind,no,requestId,toStatus" ||
    !integer(x.id, 1) ||
    typeof x.no !== "string" ||
    !/^AS[0-9a-f]{32}$/.test(x.no) ||
    x.toStatus !== (x.kind === "delivery" ? 30 : 61)
  )
    throw new ApiError("原售后操作记录不完整，请先核对处理进度", -2);
  return x as MemberAfterSalePending;
}
export function saveMemberAfterSalePending(marker: MemberAfterSalePending) {
  if (readMemberAfterSalePending() !== null)
    throw new ApiError("请先查询上一笔待确认售后", 409);
  const value =
    marker.kind === "create"
      ? {
          kind: marker.kind,
          orderId: marker.orderId,
          orderNo: marker.orderNo,
          orderItemId: marker.orderItemId,
          requestId: marker.requestId,
          expectedVersion: marker.expectedVersion,
          count: marker.count,
          way: marker.way,
        }
      : {
          kind: marker.kind,
          id: marker.id,
          no: marker.no,
          requestId: marker.requestId,
          expectedVersion: marker.expectedVersion,
          toStatus: marker.toStatus,
        };
  try {
    sessionStorage.setItem(key(), JSON.stringify(value));
  } catch {
    throw new ApiError("无法保存处理记录，请检查浏览器隐私设置后重试", -2);
  }
}
export function clearMemberAfterSalePending(marker: MemberAfterSalePending) {
  const previous = readMemberAfterSalePending();
  if (previous && previous.requestId !== marker.requestId)
    throw new ApiError("待确认记录已变化，请重新查询", 409);
  try {
    sessionStorage.removeItem(key());
  } catch {
    throw new ApiError("无法清除待确认记录，请再查询一次", -2);
  }
}
