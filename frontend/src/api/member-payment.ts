import { ApiError } from "./http";
import { cartIdentity } from "./member-cart";
import {
  memberOrderRequest,
  pendingOrderCancellation,
  validMemberOrder,
  type MemberOrder,
} from "./member-order";

export type MemberPayment = {
  order: MemberOrder;
  paymentState:
    | "NOT_SUBMITTED"
    | "SUBMITTING"
    | "WAITING"
    | "SUCCESS"
    | "CLOSED"
    | "CLOSING"
    | "REVIEW";
  canSubmit: boolean;
  qrCode: string | null;
  canRecoverQr: boolean;
  qrRecoveryCount: number;
  qrRecoveryAfter: number | null;
};
function validId(id: number) {
  if (!Number.isSafeInteger(id) || id < 1)
    throw new ApiError("订单编号不正确", 400);
}
function key(id: number) {
  return `qinghe:payment-submit:${cartIdentity()}:${id}`;
}
export function pendingMemberPayment(id: number) {
  validId(id);
  try {
    const raw = sessionStorage.getItem(key(id));
    if (raw === null) return false;
    if (raw === String(id)) return true;
  } catch {
    throw new ApiError(
      "无法读取上次付款记录，请恢复浏览器存储后查询原订单",
      -2,
    );
  }
  throw new ApiError("上次付款记录无法识别，请先查询原订单", -2);
}
function qrUrl(value: string) {
  try {
    const url = new URL(value);
    return (
      value.length <= 2048 &&
      url.protocol === "https:" &&
      !url.username &&
      !url.password &&
      !url.hash &&
      (!url.port || url.port === "443") &&
      ["qr.alipay.com", "qr.alipaydev.com"].includes(url.hostname)
    );
  } catch {
    return false;
  }
}
function accept(value: unknown, id: number): MemberPayment {
  const result = value as MemberPayment;
  if (
    !result ||
    !validMemberOrder(result.order) ||
    result.order.id !== id ||
    ![
      "NOT_SUBMITTED",
      "SUBMITTING",
      "WAITING",
      "SUCCESS",
      "CLOSED",
      "CLOSING",
      "REVIEW",
    ].includes(result.paymentState) ||
    typeof result.canSubmit !== "boolean" ||
    typeof result.canRecoverQr !== "boolean" ||
    !Number.isSafeInteger(result.qrRecoveryCount) ||
    result.qrRecoveryCount < 0 ||
    result.qrRecoveryCount > 3 ||
    !(
      result.qrRecoveryAfter === null ||
      (Number.isSafeInteger(result.qrRecoveryAfter) &&
        result.qrRecoveryAfter > 0 &&
        result.qrRecoveryAfter <= 8_640_000_000_000_000)
    ) ||
    (result.canRecoverQr &&
      (!result.canSubmit ||
        result.qrCode !== null ||
        result.order.payPrice <= 0 ||
        result.order.payOrderId === null ||
        !["SUBMITTING", "WAITING"].includes(result.paymentState) ||
        result.qrRecoveryCount >= 3 ||
        result.qrRecoveryAfter === null)) ||
    (result.canSubmit &&
      (result.order.status !== 0 ||
        result.order.payStatus ||
        result.order.payPrice < 0 ||
        result.order.serverNow >= result.order.payDeadline ||
        ["SUCCESS", "CLOSED", "CLOSING", "REVIEW"].includes(
          result.paymentState,
        ))) ||
    !(
      result.qrCode === null ||
      (typeof result.qrCode === "string" &&
        qrUrl(result.qrCode) &&
        result.canSubmit &&
        result.paymentState === "WAITING" &&
        result.order.payOrderId !== null)
    )
  )
    throw new ApiError("原付款响应不完整，请查询同一订单", 503, true);
  // A missing attempt is not proof that a timed-out submission never reached pay.
  if (
    ["SUCCESS", "CLOSED", "REVIEW"].includes(result.paymentState) ||
    result.qrCode !== null
  ) {
    try {
      sessionStorage.removeItem(key(id));
    } catch {
      throw new ApiError(
        "付款结果已返回，但无法更新本地记录，请恢复存储后查询",
        -2,
      );
    }
  }
  return result;
}
export async function readMemberPayment(id: number) {
  validId(id);
  return accept(await memberOrderRequest(`payment-result?id=${id}`), id);
}
export async function queryMemberPayment(id: number) {
  validId(id);
  return accept(
    await memberOrderRequest("query-payment", {
      method: "POST",
      body: JSON.stringify({ id }),
    }),
    id,
  );
}
function preservePaymentIntent(id: number) {
  validId(id);
  if (pendingOrderCancellation() === id)
    throw new ApiError("请先确认原订单的取消结果", 409);
  pendingMemberPayment(id);
  try {
    sessionStorage.setItem(key(id), String(id));
  } catch {
    throw new ApiError("无法保存付款记录，本次未发送，请恢复浏览器存储", -2);
  }
}
export async function submitMemberPayment(id: number) {
  preservePaymentIntent(id);
  return accept(
    await memberOrderRequest("submit-payment", {
      method: "POST",
      body: JSON.stringify({ id }),
    }),
    id,
  );
}
export async function recoverMemberPaymentQr(id: number) {
  preservePaymentIntent(id);
  return accept(
    await memberOrderRequest("recover-payment-qr", {
      method: "POST",
      body: JSON.stringify({ id }),
    }),
    id,
  );
}
