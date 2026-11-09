import { db, ui, command, currentServerTime } from "./store";
import { demo, context, credentials, prefix } from "../api/session";
import { request } from "../api/http";
import {
  assertSeckillProjection,
  type ActivityInput,
  type SeckillQuote,
  type SeckillRequest,
} from "../seckill";
export async function saveActivity(input: ActivityInput, id?: string) {
  return command<Promise<string>>("seckill.save", { input, id }, async () => {
    if (import.meta.env.MODE !== "prototype")
      throw new Error("演示适配器不可用");
    const m = await import("./seckill-demo");
    return m.demoSaveActivity(input, id);
  });
}
export async function transitionActivity(
  id: string,
  version: number,
  operation: "publish" | "withdraw" | "stop" | "reconcile",
  reason = "",
) {
  if (context.realm === "platform" && operation !== "stop")
    throw new Error("平台仅可停止异常活动");
  return command<Promise<string>>(
    context.realm === "platform"
      ? "seckill.platformStop"
      : `seckill.${operation}`,
    { id, version, operation, reason },
    async () => {
      if (import.meta.env.MODE !== "prototype")
        throw new Error("演示适配器不可用");
      const m = await import("./seckill-demo");
      return m.demoTransition(id, operation, reason);
    },
  );
}
export async function getSeckillQuote(
  input: {
    activityId: string;
    version: number;
    spec: string;
    quantity: number;
    addressId: number;
  },
  signal?: AbortSignal,
) {
  let value: SeckillQuote;
  if (import.meta.env.MODE === "prototype") {
    const a = db.seckillActivities.find(
        (a) => a.id === input.activityId && a.tenant === ui.tenant,
      ),
      sku = a?.skus.find((s) => s.spec === input.spec),
      t = db.shipping.find((s) => s.tenant === ui.tenant && s.enabled);
    if (!sku || !t) throw new Error("请选择规格及可用配送地址");
    const subtotal = sku.price * input.quantity,
      shipping =
        t.freeAbove > 0 && subtotal >= t.freeAbove
          ? 0
          : t.first + Math.max(0, input.quantity - 1) * t.additional;
    value = {
      token: "prototype-quote",
      subtotal,
      shipping,
      total: subtotal + shipping,
      expiresAt: Date.now() + 60000,
    };
  } else
    value = await request<SeckillQuote>(
      `${prefix("member")}/seckill/quote`,
      { ...credentials(), signal },
      { method: "POST", body: JSON.stringify(input) },
    );
  if (
    !value ||
    typeof value.token !== "string" ||
    !value.token ||
    ![value.subtotal, value.shipping, value.total].every(
      (n) => Number.isSafeInteger(n) && n >= 0,
    ) ||
    value.total !== value.subtotal + value.shipping ||
    !Number.isFinite(value.expiresAt) ||
    !Number.isFinite(currentServerTime()) ||
    value.expiresAt <= currentServerTime()
  )
    throw new Error("报价已失效，请重新确认");
  return value;
}
export async function joinSeckill(input: {
  activityId: string;
  version: number;
  spec: string;
  quantity: number;
  addressId: number;
  requestKey: string;
  quoteToken: string;
}) {
  return command<Promise<string>>("seckill.join", input, async () => {
    if (import.meta.env.MODE !== "prototype")
      throw new Error("演示适配器不可用");
    const m = await import("./seckill-demo");
    return m.demoJoin(input);
  });
}
export async function fetchSeckillRequest(
  id: string,
  signal?: AbortSignal,
): Promise<SeckillRequest> {
  let value: SeckillRequest;
  if (import.meta.env.MODE === "prototype") {
    const m = await import("./seckill-demo");
    value = m.demoRequest(id, context.realm === "platform");
  } else
    value = await request<SeckillRequest>(
      `${prefix()}/seckill/requests/${encodeURIComponent(id)}`,
      { ...credentials(), signal },
    );
  assertSeckillProjection(
    [],
    [value],
    ui.tenant,
    ui.memberId,
    context.realm === "platform",
    context.realm === "member",
  );
  if (value.id !== id) throw new Error("抢购记录响应异常");
  return value;
}
