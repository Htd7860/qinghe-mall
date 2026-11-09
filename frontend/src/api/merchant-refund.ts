import { ApiError } from "./http";
import {
  afterSaleIdentity,
  afterSaleRequest,
  readPendingDecision,
} from "./merchant-aftersale";
import {
  integer,
  record,
  uuid,
  stamp,
  refundProgress,
  type RefundProgress,
} from "./aftersale-contract";

// Selected upstream AfterSaleApi.refund duty; reuse the existing HTTP/Bearer boundary.
export type FinancialRefund = RefundProgress & {
  id: number;
  orderId: number;
  refundNo: string;
  requestId: string;
  preparedVersion: number;
  authVersion: number;
  updatedAt: number;
};
export type PrepareRefund = {
  id: number;
  expectedVersion: number;
  requestId: string;
  reason: string;
};
export type RefundAction = "execute" | "sync" | "retry";
export type RetryRefund = PrepareRefund & { refundNo: string };
export type RefundBudget = {
  id: number;
  refundNo: string;
  payVersion: number;
  submitCount: number;
  submissionLimit: number;
  status: string;
  retryAfter: number | null;
};
export type ContinuationReference = {
  id: number;
  refundNo: string;
  requestId: string;
  expectedVersion: number;
  expectedPayVersion: number;
};
export type ContinuationReceipt = {
  id: number;
  refundNo: string;
  requestId: string;
  recordedVersion: number;
  expectedPayVersion: number;
  authorizationRecorded: boolean;
  authorizedLimit: number | null;
};
export type PendingRefund =
  | (ContinuationReference & { action: "continue"; no: string })
  | {
      action: "prepare";
      id: number;
      no: string;
      requestId: string;
      expectedVersion: number;
    }
  | { action: "execute" | "sync"; id: number; no: string; refundNo: string }
  | {
      action: "retry";
      id: number;
      no: string;
      refundNo: string;
      requestId: string;
      expectedVersion: number;
    };
const refundNo = (x: unknown): x is string =>
  typeof x === "string" && /^RF[0-9a-f]{32}$/.test(x);

function financial(x: unknown): x is FinancialRefund {
  if (!record(x)) return false;
  const fields: Record<string, unknown> = x;
  return (
    refundProgress(x) &&
    integer(fields.id, 1) &&
    integer(fields.orderId, 1) &&
    refundNo(fields.refundNo) &&
    uuid(fields.requestId) &&
    integer(fields.preparedVersion, 1) &&
    integer(fields.authVersion, 1) &&
    fields.authVersion >= fields.preparedVersion &&
    integer(fields.updatedAt, 1) &&
    stamp(fields.updatedAt) &&
    (x.confirmedAt === null ||
      fields.updatedAt + 1000 >= x.confirmedAt) &&
    (x.status !== "SUCCESS" ||
      x.restoreCount !== 0 ||
      x.stockRecovered) &&
    (x.status !== "SUCCESS" ||
      x.couponReturnRequired ||
      x.couponRecovered)
  );
}
function budget(x: unknown): x is RefundBudget {
  return (
    record(x) &&
    integer(x.id, 1) &&
    refundNo(x.refundNo) &&
    integer(x.payVersion) &&
    integer(x.submitCount) &&
    integer(x.submissionLimit, 3) &&
    x.submissionLimit <= 2147483647 &&
    x.submitCount <= x.submissionLimit &&
    typeof x.status === "string" &&
    ["PREPARED", "PROCESSING", "UNKNOWN", "FAILED", "SUCCESS"].includes(
      x.status,
    ) &&
    (x.retryAfter === null || stamp(x.retryAfter))
  );
}
export async function financialRefundBudget(id: number, originalNo: string) {
  if (!integer(id, 1) || !refundNo(originalNo))
    throw new ApiError("原退款编号不正确", 400);
  const value = await afterSaleRequest(
    `refund-budget?${new URLSearchParams({ id: String(id), refundNo: originalNo })}`,
  );
  if (!budget(value) || value.id !== id || value.refundNo !== originalNo)
    throw new ApiError("原退款预算不完整，请重新读取", -2);
  return value;
}
export async function continueFinancialRefund(
  body: ContinuationReference & { reason: string },
) {
  if (
    !integer(body.id, 1) ||
    !refundNo(body.refundNo) ||
    !uuid(body.requestId) ||
    !integer(body.expectedVersion) ||
    body.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !integer(body.expectedPayVersion) ||
    body.expectedPayVersion >= Number.MAX_SAFE_INTEGER ||
    !body.reason.trim() ||
    body.reason.length > 500 ||
    /[\u0000-\u001f\u007f-\u009f]/.test(body.reason)
  )
    throw new ApiError("请核对原人工授权依据", 400);
  const value = await afterSaleRequest("refund-continue", {
    method: "POST",
    body: JSON.stringify({
      id: body.id,
      refundNo: body.refundNo,
      requestId: body.requestId,
      expectedVersion: body.expectedVersion,
      expectedPayVersion: body.expectedPayVersion,
      reason: body.reason,
    }),
  });
  if (
    !budget(value) ||
    value.id !== body.id ||
    value.refundNo !== body.refundNo
  )
    throw new ApiError("原人工授权结果待确认，请查询原请求", -2, true);
  // Budget growth alone is not evidence that this UUID was granted. Read its receipt separately.
  return value;
}
export async function financialContinuationResult(
  reference: ContinuationReference,
) {
  if (
    !integer(reference.id, 1) ||
    !refundNo(reference.refundNo) ||
    !uuid(reference.requestId) ||
    !integer(reference.expectedVersion) ||
    reference.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !integer(reference.expectedPayVersion) ||
    reference.expectedPayVersion >= Number.MAX_SAFE_INTEGER
  )
    throw new ApiError("原人工授权引用不正确", 400);
  const value = await afterSaleRequest(
    `refund-continue-result?requestId=${encodeURIComponent(reference.requestId)}`,
  );
  if (value === null) return null;
  if (
    !record(value) ||
    value.id !== reference.id ||
    value.refundNo !== reference.refundNo ||
    value.requestId !== reference.requestId ||
    value.recordedVersion !== reference.expectedVersion + 1 ||
    value.expectedPayVersion !== reference.expectedPayVersion ||
    typeof value.authorizationRecorded !== "boolean" ||
    (value.authorizationRecorded
      ? !integer(value.authorizedLimit, 4) || value.authorizedLimit > 2147483647
      : value.authorizedLimit !== null)
  )
    throw new ApiError("原人工授权回执不完整，请保留原请求", -2);
  return value as ContinuationReceipt;
}
export async function financialRefundResult(id: number, originalNo?: string) {
  if (!integer(id, 1) || (originalNo !== undefined && !refundNo(originalNo)))
    throw new ApiError("原售后号或退款号不正确", 400);
  const query = new URLSearchParams({ id: String(id) });
  if (originalNo !== undefined) query.set("refundNo", originalNo);
  const value = await afterSaleRequest(`refund-result?${query}`);
  if (
    !financial(value) ||
    value.id !== id ||
    (originalNo !== undefined && value.refundNo !== originalNo)
  )
    throw new ApiError("原退款记录不完整，请重新查询", -2);
  return value;
}
export async function refundPreparationResult(
  marker: Extract<PendingRefund, { action: "prepare" }>,
) {
  const value = await afterSaleRequest(
    `refund-prepare-result?requestId=${encodeURIComponent(marker.requestId)}`,
  );
  if (value === null) return null;
  if (
    !financial(value) ||
    value.id !== marker.id ||
    value.requestId !== marker.requestId ||
    value.preparedVersion !== marker.expectedVersion + 1
  )
    throw new ApiError("原准备回执不完整，请保留待确认记录", -2);
  return value;
}
export async function prepareFinancialRefund(body: PrepareRefund) {
  if (
    !integer(body.id, 1) ||
    !integer(body.expectedVersion) ||
    body.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !uuid(body.requestId) ||
    !body.reason.trim() ||
    body.reason.length > 500 ||
    /[\u0000-\u001f\u007f-\u009f]/.test(body.reason)
  )
    throw new ApiError("请核对原申请及退款原因", 400);
  const value = await afterSaleRequest("refund-prepare", {
    method: "POST",
    body: JSON.stringify({
      id: body.id,
      expectedVersion: body.expectedVersion,
      requestId: body.requestId,
      reason: body.reason,
    }),
  });
  if (
    !record(value) ||
    value.id !== body.id ||
    value.authVersion !== body.expectedVersion + 1 ||
    !refundNo(value.refundNo) ||
    !integer(value.goodsPrice) ||
    !integer(value.deliveryPrice) ||
    !integer(value.totalPrice) ||
    !Number.isSafeInteger(value.goodsPrice + value.deliveryPrice) ||
    value.totalPrice !== value.goodsPrice + value.deliveryPrice ||
    !integer(value.restoreCount) ||
    value.restoreCount > 9999 ||
    typeof value.couponReturnRequired !== "boolean"
  )
    throw new ApiError("退款准备结果待确认，请查询原请求", -2, true);
  // The preparation receipt is not a paid result. Read the authorized local projection.
  let progress: FinancialRefund;
  try {
    progress = await financialRefundResult(body.id, value.refundNo);
  } catch (e) {
    // The POST already returned a receipt; a later GET failure cannot prove rollback.
    throw new ApiError(
      e instanceof Error ? e.message : "原准备结果待确认",
      e instanceof ApiError ? e.code : -2,
      true,
    );
  }
  if (
    progress.requestId !== body.requestId ||
    progress.preparedVersion !== body.expectedVersion + 1 ||
    progress.goodsPrice !== value.goodsPrice ||
    progress.deliveryPrice !== value.deliveryPrice ||
    progress.totalPrice !== value.totalPrice ||
    progress.restoreCount !== value.restoreCount ||
    progress.couponReturnRequired !== value.couponReturnRequired
  )
    throw new ApiError("原退款计划待核对，请保留原准备请求", -2, true);
  return progress;
}
export async function commandFinancialRefund(
  action: "execute" | "sync",
  id: number,
  originalNo: string,
) {
  if (
    !integer(id, 1) ||
    !refundNo(originalNo) ||
    !["execute", "sync"].includes(action)
  )
    throw new ApiError("原退款操作不正确", 400);
  const value = await afterSaleRequest(`refund-${action}`, {
    method: "POST",
    body: JSON.stringify({ id, refundNo: originalNo }),
  });
  if (!financial(value) || value.id !== id || value.refundNo !== originalNo)
    throw new ApiError("原退款结果待确认，请先查询进度", -2, true);
  return value;
}

export async function retryFinancialRefund(body: RetryRefund) {
  if (
    !integer(body.id, 1) ||
    !refundNo(body.refundNo) ||
    !uuid(body.requestId) ||
    !integer(body.expectedVersion) ||
    body.expectedVersion >= Number.MAX_SAFE_INTEGER ||
    !body.reason.trim() ||
    body.reason.length > 500 ||
    /[\u0000-\u001f\u007f-\u009f]/.test(body.reason)
  )
    throw new ApiError("请填写原退款重试依据", 400);
  const value = await afterSaleRequest("refund-retry", {
    method: "POST",
    body: JSON.stringify({
      id: body.id,
      refundNo: body.refundNo,
      expectedVersion: body.expectedVersion,
      requestId: body.requestId,
      reason: body.reason,
    }),
  });
  if (
    !financial(value) ||
    value.id !== body.id ||
    value.refundNo !== body.refundNo ||
    value.authVersion < body.expectedVersion + 1
  )
    throw new ApiError("原重试结果待确认，请查询原请求", -2, true);
  return value;
}
export async function refundRetryResult(
  marker: Extract<PendingRefund, { action: "retry" }>,
) {
  const value = await afterSaleRequest(
    `refund-retry-result?requestId=${encodeURIComponent(marker.requestId)}`,
  );
  if (value === null) return null;
  if (
    !record(value) ||
    value.id !== marker.id ||
    value.refundNo !== marker.refundNo ||
    value.requestId !== marker.requestId ||
    value.recordedVersion !== marker.expectedVersion + 1
  )
    throw new ApiError("原重试回执不完整，请保留待确认记录", -2);
  return {
    id: marker.id,
    refundNo: marker.refundNo,
    requestId: marker.requestId,
    recordedVersion: marker.expectedVersion + 1,
  };
}

// One actor-scoped marker. No reason, money, contact/address, raw response or credential.
const pendingKey = () => `qinghe:refund-pending:v1:${afterSaleIdentity()}`;
function validMarker(x: unknown): x is PendingRefund {
  if (
    !record(x) ||
    !integer(x.id, 1) ||
    typeof x.no !== "string" ||
    !/^AS[0-9a-f]{32}$/.test(x.no)
  )
    return false;
  const keys = Object.keys(x).sort().join(",");
  if (x.action === "continue")
    return (
      keys ===
        "action,expectedPayVersion,expectedVersion,id,no,refundNo,requestId" &&
      refundNo(x.refundNo) &&
      uuid(x.requestId) &&
      integer(x.expectedVersion) &&
      x.expectedVersion < Number.MAX_SAFE_INTEGER &&
      integer(x.expectedPayVersion) &&
      x.expectedPayVersion < Number.MAX_SAFE_INTEGER
    );
  if (x.action === "retry")
    return (
      keys === "action,expectedVersion,id,no,refundNo,requestId" &&
      refundNo(x.refundNo) &&
      uuid(x.requestId) &&
      integer(x.expectedVersion) &&
      x.expectedVersion < Number.MAX_SAFE_INTEGER
    );
  return x.action === "prepare"
    ? keys === "action,expectedVersion,id,no,requestId" &&
        uuid(x.requestId) &&
        integer(x.expectedVersion) &&
        x.expectedVersion < Number.MAX_SAFE_INTEGER
    : keys === "action,id,no,refundNo" &&
        ["execute", "sync"].includes(String(x.action)) &&
        refundNo(x.refundNo);
}
export function readPendingRefund(): PendingRefund | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(pendingKey());
  } catch {
    throw new ApiError("无法读取退款待确认记录，请检查浏览器隐私设置", -2);
  }
  if (raw === null) return null;
  let value: unknown;
  try {
    value = raw.length <= 512 ? JSON.parse(raw) : null;
  } catch {
    value = null;
  }
  if (!validMarker(value))
    throw new ApiError("退款待确认记录无法读取，请先核对原售后", -2);
  return value;
}
export function savePendingRefund(value: PendingRefund) {
  if (!validMarker(value)) throw new ApiError("退款恢复记录不正确", 400);
  if (readPendingRefund() || readPendingDecision())
    throw new ApiError("请先查询上一笔待确认操作", 409);
  const safe =
    value.action === "prepare"
      ? {
          action: value.action,
          id: value.id,
          no: value.no,
          requestId: value.requestId,
          expectedVersion: value.expectedVersion,
        }
      : value.action === "continue"
        ? {
            action: value.action,
            id: value.id,
            no: value.no,
            refundNo: value.refundNo,
            requestId: value.requestId,
            expectedVersion: value.expectedVersion,
            expectedPayVersion: value.expectedPayVersion,
          }
        : value.action === "retry"
          ? {
              action: value.action,
              id: value.id,
              no: value.no,
              refundNo: value.refundNo,
              requestId: value.requestId,
              expectedVersion: value.expectedVersion,
            }
          : {
              action: value.action,
              id: value.id,
              no: value.no,
              refundNo: value.refundNo,
            };
  try {
    sessionStorage.setItem(pendingKey(), JSON.stringify(safe));
  } catch {
    throw new ApiError("无法保存退款待确认记录，请检查浏览器设置后重试", -2);
  }
}
export function removePendingRefund(value: PendingRefund) {
  const old = readPendingRefund();
  if (
    old &&
    (old.id !== value.id ||
      old.no !== value.no ||
      old.action !== value.action ||
      (old.action === "prepare" &&
        (value.action !== "prepare" ||
          old.requestId !== value.requestId ||
          old.expectedVersion !== value.expectedVersion)) ||
      (old.action !== "prepare" &&
        (value.action === "prepare" || old.refundNo !== value.refundNo)) ||
      (old.action === "retry" &&
        (value.action !== "retry" ||
          old.requestId !== value.requestId ||
          old.expectedVersion !== value.expectedVersion)) ||
      (old.action === "continue" &&
        (value.action !== "continue" ||
          old.requestId !== value.requestId ||
          old.expectedVersion !== value.expectedVersion ||
          old.expectedPayVersion !== value.expectedPayVersion)))
  )
    throw new ApiError("退款待确认记录已变化，请重新查询", 409);
  try {
    sessionStorage.removeItem(pendingKey());
  } catch {
    throw new ApiError("暂时无法清除退款待确认记录，请再查询一次", -2);
  }
}
