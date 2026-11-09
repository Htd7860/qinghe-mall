import { assertSeckillProjection } from "../seckill";
import { computed, reactive, readonly, ref, watch } from "vue";
import type { createSeed } from "./seed";
import { assertReport, type OverviewReport } from "../api/report";
import { assertViewScope, permitsAction } from "../api/view-policy";
import { ApiError, request } from "../api/http";
import { context, credentials, demo, identities, prefix } from "../api/session";
import { safePaymentUrl, validIdentity } from "../api/policy";
import {
  allocateDiscount,
  refundable,
  timestamp,
  unitRefund,
  type Product,
  type AfterSale,
  type Address,
  type Order,
  type OrderItem,
} from "../domain";

const KEY = "qinghe-commerce-preview:v1";
type State = ReturnType<typeof createSeed>;
function withSkus(data: State): State {
  for (const p of data.products) {
    if (!p.skus) {
      const specs = p.colors.flatMap((c) => p.sizes.map((s) => `${c} / ${s}`));
      p.skus = specs.map((spec, i) => ({
        spec,
        price: p.price,
        stock:
          Math.floor(p.stock / specs.length) +
          (i < p.stock % specs.length ? 1 : 0),
      }));
    }
  }
  return data;
}
const stateKeys = [
  "products",
  "orders",
  "tenants",
  "cart",
  "roles",
  "afterSales",
  "ownedCoupons",
  "jobs",
  "shipping",
  "notifications",
  "packages",
  "members",
  "staff",
  "addresses",
  "audit",
  "campaigns",
  "inventory",
  "coupons",
  "seckillActivities",
  "seckillRequests",
] as const;
function emptyState(): State {
  return Object.assign(Object.fromEntries(stateKeys.map((k) => [k, []])), {
    settings: {},
  }) as unknown as State;
}
const mutableState = reactive<State>(emptyState());
// Production components receive a read-only server projection. Only hydration can replace it.
export const db = (demo ? mutableState : readonly(mutableState)) as State;
export const overviewReport = ref<OverviewReport | null>(null);
export const dashboardRange = ref(7);
export const serverClock = reactive({ serverNow: 0, sampledAt: 0 });
export function syncServerClock(now: number) {
  if (!Number.isFinite(now)) throw new Error("活动时间响应异常");
  serverClock.serverNow = now;
  serverClock.sampledAt = performance.now();
}
export function currentServerTime() {
  return demo
    ? Date.now()
    : serverClock.serverNow
      ? serverClock.serverNow + performance.now() - serverClock.sampledAt
      : NaN;
}
export const ui = reactive({
  tenant: "",
  role: "",
  memberId: 0,
  toast: "",
  toastType: "success",
  storageFailure: false,
  loading: false,
  error: "",
  busy: false,
  uncertain: "",
  revision: 0,
});
export async function initializeData() {
  if (import.meta.env.MODE !== "prototype") return;
  const { createSeed } = await import("./seed");
  let data = createSeed();
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || "null");
    if (
      saved &&
      stateKeys
        .filter((k) => !k.startsWith("seckill"))
        .every((k) => Array.isArray(saved[k])) &&
      saved.settings
    )
      data = {
        ...saved,
        seckillActivities: saved.seckillActivities || [],
        seckillRequests: saved.seckillRequests || [],
      };
  } catch {
    /* Use isolated demo fixtures only. */
  }
  data.cart = data.cart.filter((l) => Number.isSafeInteger(l.memberId));
  Object.assign(db, withSkus(data));
  const { initializeSeckillDemo } = await import("./seckill-demo");
  initializeSeckillDemo();
  watch(
    db,
    (value) => {
      try {
        localStorage.setItem(KEY, JSON.stringify(value));
      } catch {
        ui.storageFailure = true;
      }
    },
    { deep: true },
  );
}
export function clearData() {
  loadGeneration++;
  ui.loading = false;
  ui.uncertain = "";
  serverQuote.value = null;
  quoteGeneration++;
  overviewReport.value = null;
  if (!demo) Object.assign(mutableState, emptyState());
  ui.role = "";
  ui.memberId = 0;
  ui.error = "";
}
export function bindIdentity() {
  if (
    context.realm === "member" &&
    identities.member?.tenantCode !== context.tenant
  )
    identities.member = null;
  const identity = identities[context.realm];
  ui.tenant =
    context.realm === "member" ? context.tenant : identity?.tenantCode || "";
  ui.role = context.realm === "merchant" ? identity?.role || "" : "";
  ui.memberId = context.realm === "member" ? identity?.userId || 0 : 0;
}
let viewPath = "",
  viewQuery = "",
  loadGeneration = 0;
export async function loadPage(path = viewPath, query = viewQuery) {
  viewPath = path;
  viewQuery = query;
  bindIdentity();
  serverQuote.value = null;
  quoteGeneration++;
  if (demo) return;
  const generation = ++loadGeneration;
  ui.loading = true;
  ui.error = "";
  Object.assign(mutableState, emptyState());
  try {
    const result = await request<{
      tenantCode: string;
      state: Partial<State>;
      report?: OverviewReport;
      serverTime?: number;
    }>(
      `${prefix()}/commerce/views/${encodeURIComponent(path)}${query}${["overview", "analytics"].includes(path) ? `&days=${dashboardRange.value}` : ""}`,
      credentials(),
    );
    if (generation !== loadGeneration) return;
    assertViewScope(result, context.realm, ui.tenant, ui.memberId);
    assertSeckillProjection(
      result.state.seckillActivities || [],
      result.state.seckillRequests || [],
      ui.tenant,
      ui.memberId,
      context.realm === "platform",
      context.realm === "member",
    );
    if (path.startsWith("seckill")) {
      if (!Number.isFinite(result.serverTime))
        throw new Error("活动时间响应异常");
      syncServerClock(result.serverTime!);
    }
    if (["overview", "analytics"].includes(path)) {
      assertReport(result.report);
      overviewReport.value = result.report;
    }
    const next = emptyState();
    for (const key of stateKeys) {
      if (result.state[key] !== undefined) {
        const rows = result.state[key];
        if (
          !Array.isArray(rows) ||
          rows.some(
            (r) =>
              !r ||
              typeof r !== "object" ||
              (context.realm !== "platform" &&
                "tenant" in r &&
                r.tenant !== ui.tenant),
          )
        )
          throw new Error("页面数据异常，请重试");
        (next[key] as unknown[]) = rows;
      }
    }
    const memberFields = [
      "orders",
      "addresses",
      "ownedCoupons",
      "notifications",
    ] as const;
    if (
      context.realm === "member" &&
      memberFields.some((k) =>
        next[k].some((r) => "memberId" in r && r.memberId !== ui.memberId),
      )
    )
      throw new Error("页面数据异常，请重试");
    next.settings = result.state.settings || {};
    if (
      context.realm !== "platform" &&
      Object.keys(next.settings).some((k) => k !== ui.tenant)
    )
      throw new Error("页面数据异常，请重试");
    Object.assign(mutableState, next);
  } catch (e) {
    if (generation !== loadGeneration) return;
    if (e instanceof ApiError && e.code === 401) {
      identities[context.realm] = null;
      bindIdentity();
      window.dispatchEvent(new Event("commerce:unauthorized"));
    }
    ui.error = e instanceof Error ? e.message : "加载失败，请重试";
  } finally {
    if (generation === loadGeneration) ui.loading = false;
  }
}
export async function command<T>(
  action: string,
  payload: unknown,
  preview: () => T,
): Promise<Awaited<T>> {
  if (ui.busy) throw new Error("上一项操作正在处理，请稍候");
  const identity = identities[context.realm];
  if (
    !validIdentity(
      identity,
      context.realm,
      context.realm === "platform" ? undefined : ui.tenant,
    )
  )
    throw new ApiError("请先登录", 401);
  if (!permitsAction(context.realm, action, identity.permissions))
    throw new Error("当前账号没有操作权限");
  if (demo) {
    ui.busy = true;
    try {
      return await preview();
    } finally {
      ui.busy = false;
    }
  }
  if (ui.uncertain) throw new Error("有一项操作结果待确认，请先查询结果");
  ui.busy = true;
  const key = crypto.randomUUID();
  const storageKey = pendingStorageKey(identity.userId);
  try {
    try {
      sessionStorage.setItem(storageKey, key);
    } catch {
      throw new Error("浏览器无法保存操作记录，请恢复存储后重试");
    }
    // No local transaction or fixture fallback may run on the API path.
    const result = await request<Awaited<T>>(
      `${prefix()}/commerce/commands/${action}`,
      { ...credentials(), idempotencyKey: key },
      { method: "POST", body: JSON.stringify(payload) },
    );
    sessionStorage.removeItem(storageKey);
    await loadPage();
    if (!ui.error) notify("操作已提交");
    if (ui.error) notify("操作已提交，页面更新失败，请刷新查看", true);
    return result;
  } catch (e) {
    if (e instanceof ApiError && e.outcomeUnknown) {
      ui.uncertain = key;
      // The recovery key was persisted before sending; reload cannot silently retry.
    }
    if (!(e instanceof ApiError && e.outcomeUnknown))
      sessionStorage.removeItem(storageKey);
    if (e instanceof ApiError && e.code === 401) {
      identities[context.realm] = null;
      clearData();
      window.dispatchEvent(new Event("commerce:unauthorized"));
    }
    throw e;
  } finally {
    ui.busy = false;
  }
}
export async function resolvePending() {
  if (!ui.uncertain || ui.busy) return;
  ui.busy = true;
  try {
    const result = await request<{
      status: "succeeded" | "failed" | "processing" | "unknown";
    }>(
      `${prefix()}/commerce/operations/${encodeURIComponent(ui.uncertain)}`,
      credentials(),
    );
    if (!["succeeded", "failed"].includes(result.status))
      throw new Error("处理结果尚未确认，请稍后再查");
    ui.uncertain = "";
    sessionStorage.removeItem(
      pendingStorageKey(identities[context.realm]!.userId),
    );
    await loadPage();
    notify(
      result.status === "succeeded"
        ? "操作已完成，请查看最新记录"
        : "操作未成功，可重新操作",
      result.status === "failed",
    );
  } finally {
    ui.busy = false;
  }
}
function pendingStorageKey(userId: number) {
  return `commerce:pending:${context.realm}:${ui.tenant}:${userId}`;
}
export function restorePending() {
  const identity = identities[context.realm];
  ui.uncertain = "";
  if (!identity || demo) return;
  try {
    ui.uncertain =
      sessionStorage.getItem(pendingStorageKey(identity.userId)) || "";
  } catch {
    ui.error = "浏览器无法保存操作记录，请恢复存储后重试";
  }
}
let toastTimer: ReturnType<typeof setTimeout>;
export function notify(message: string, error = false) {
  ui.toast = message;
  ui.toastType = error ? "error" : "success";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (ui.toast = ""), 3800);
}
export async function attempt<T>(fn: () => T): Promise<Awaited<T> | undefined> {
  try {
    return await fn();
  } catch (e) {
    notify(e instanceof Error ? e.message : "操作未完成，请重试", true);
  }
}
export const activeTenant = computed(() =>
  db.tenants.find((t) => t.code === ui.tenant)!,
);
export const brand = computed(
  () =>
    db.settings[ui.tenant] || {
      name: activeTenant.value?.name || "品牌商城",
      logo: "商城",
      tagline: "发现好物，享受日常。",
      phone: "",
      announcement: "欢迎来到我们的商城",
    },
);
export const tenantProducts = computed(() =>
  db.products.filter((p) => p.tenant === ui.tenant),
);
export const tenantOrders = computed(() =>
  db.orders.filter((o) => o.tenant === ui.tenant),
);
export const currentMember = computed(() =>
  db.members.find((m) => m.id === ui.memberId && m.tenant === ui.tenant),
);
export function can(permission: string) {
  const identity = identities.merchant;
  if (
    context.realm !== "merchant" ||
    !validIdentity(identity, "merchant", ui.tenant)
  )
    return false;
  if (!demo) return identity.permissions.includes(permission);
  return (
    db.roles.some(
      (r) =>
        r.tenant === ui.tenant &&
        r.name === identity.role &&
        r.status === "正常" &&
        r.permissions.includes(permission),
    ) &&
    db.staff.some(
      (s) =>
        s.id === identity.userId &&
        s.tenant === ui.tenant &&
        s.status === "正常",
    )
  );
}
export function requirePermission(permission: string) {
  if (!can(permission)) throw new Error("当前账号没有操作权限");
}
export function assertTenant(code: string) {
  if (code !== ui.tenant) throw new Error("无法访问此记录");
}
export function assertTrading() {
  if (
    !activeTenant.value ||
    activeTenant.value.status !== "正常" ||
    activeTenant.value.expire < new Date().toLocaleDateString("sv-SE")
  )
    throw new Error("店铺暂未营业，已有订单仍可查询");
}
export function log(action: string, subject: string, tenant = ui.tenant) {
  db.audit.unshift({
    id: Date.now(),
    tenant,
    action,
    subject,
    actor: ui.role,
    at: timestamp(),
  });
}
function previewSaveProduct(
  input: Omit<Product, "id" | "tenant" | "sales" | "updated">,
  id?: number,
) {
  requirePermission("product");
  assertTrading();
  if (
    !input.name.trim() ||
    !input.category ||
    !input.colors.length ||
    !input.sizes.length
  )
    throw new Error("请完整填写商品名称、分类和规格");
  if (!Number.isSafeInteger(input.price) || input.price <= 0)
    throw new Error("售价必须大于0且最多保留两位小数");
  if (!Number.isSafeInteger(input.stock) || input.stock < 0)
    throw new Error("库存必须是非负整数");
  if (
    input.skus?.some(
      (s) =>
        !Number.isSafeInteger(s.price) ||
        s.price <= 0 ||
        !Number.isSafeInteger(s.stock) ||
        s.stock < 0,
    )
  )
    throw new Error("请检查各规格的售价和库存");
  if (id) {
    const p = db.products.find((p) => p.id === id);
    if (!p) throw new Error("商品不存在");
    assertTenant(p.tenant);
    const oldSkus = p.skus || [];
    const skus = input.skus?.map((s) => ({
      ...s,
      stock: oldSkus.find((old) => old.spec === s.spec)?.stock || 0,
    }));
    if (
      oldSkus.some((s) => s.stock > 0 && !skus?.some((n) => n.spec === s.spec))
    )
      throw new Error("移除规格前请先处理该规格库存");
    Object.assign(p, {
      ...input,
      skus,
      stock: skus?.reduce((n, s) => n + s.stock, 0) || p.stock,
      price: skus?.length ? Math.min(...skus.map((s) => s.price)) : input.price,
      updated: timestamp(),
    });
  } else {
    const p: Product = {
      ...input,
      status: "已下架",
      id: Date.now(),
      tenant: ui.tenant,
      sales: 0,
      updated: timestamp(),
    };
    if (p.skus?.length) {
      p.price = Math.min(...p.skus.map((s) => s.price));
      p.stock = p.skus.reduce((n, s) => n + s.stock, 0);
    }
    db.products.unshift(p);
    withSkus(db);
  }
  log(id ? "更新商品" : "添加商品", input.name);
  notify("商品已保存");
}
function previewToggleProduct(p: Product) {
  requirePermission("product");
  assertTenant(p.tenant);
  assertTrading();
  p.status = p.status === "上架中" ? "已下架" : "上架中";
  p.updated = timestamp();
  log(p.status === "上架中" ? "商品上架" : "商品下架", p.name);
  notify(p.status === "上架中" ? "商品已上架" : "商品已下架");
}
function previewAdjustStock(
  p: Product,
  delta: number,
  reason: string,
  spec?: string,
) {
  requirePermission("product");
  assertTenant(p.tenant);
  const sku = p.skus?.find((s) => s.spec === (spec || p.skus?.[0]?.spec));
  if (
    !sku ||
    !Number.isSafeInteger(delta) ||
    !delta ||
    sku.stock + delta < 0 ||
    !reason.trim()
  )
    throw new Error("请填写有效调整数量和原因，调整后规格库存不能小于0");
  sku.stock += delta;
  p.stock += delta;
  db.inventory.unshift({
    id: Date.now(),
    tenant: ui.tenant,
    productId: p.id,
    product: `${p.name} · ${sku.spec}`,
    delta,
    reason,
    after: sku.stock,
    actor: ui.role,
    at: timestamp(),
  });
  log("调整库存", p.name);
  notify("库存已调整");
}
function previewAddToCart(p: Product, spec: string, quantity: number) {
  assertTrading();
  assertTenant(p.tenant);
  const sku = p.skus?.find((s) => s.spec === spec);
  if (
    p.status !== "上架中" ||
    !sku ||
    quantity < 1 ||
    !Number.isSafeInteger(quantity) ||
    quantity > sku.stock
  )
    throw new Error("该规格库存不足，请调整数量");
  if (!spec) throw new Error("请选择规格");
  const existing = db.cart.find(
    (l) =>
      l.tenant === ui.tenant &&
      l.memberId === ui.memberId &&
      l.productId === p.id &&
      l.spec === spec,
  );
  if (existing) {
    if (existing.quantity + quantity > sku.stock)
      throw new Error("购物袋数量已超过该规格库存");
    existing.quantity += quantity;
  } else
    db.cart.push({
      tenant: ui.tenant,
      memberId: ui.memberId,
      productId: p.id,
      spec,
      quantity,
      checked: true,
    });
  notify("已加入购物袋");
}
function previewClaimCoupon(
  id: number,
  memberId = ui.memberId,
  campaignId?: number,
) {
  assertTrading();
  const c = db.coupons.find((c) => c.id === id && c.tenant === ui.tenant),
    member = db.members.find(
      (m) => m.id === memberId && m.tenant === ui.tenant,
    );
  if (!member) throw new Error("请先登录");
  if (
    !c ||
    c.status !== "发放中" ||
    c.end < new Date().toLocaleDateString("sv-SE")
  )
    throw new Error("优惠券暂不可领取");
  if (c.issued >= c.total) throw new Error("优惠券已领完");
  if (
    db.ownedCoupons.filter(
      (o) =>
        o.tenant === ui.tenant && o.couponId === id && o.memberId === memberId,
    ).length >= c.limit
  )
    throw new Error("已达到领取上限");
  c.issued++;
  db.ownedCoupons.push({
    id: crypto.randomUUID(),
    tenant: ui.tenant,
    memberId,
    couponId: id,
    orderId: null,
    campaignId,
  });
  return true;
}
export function quote(couponId: number | null) {
  const lines = db.cart.filter(
    (c) => c.tenant === ui.tenant && c.memberId === ui.memberId && c.checked,
  );
  const items = lines.map((line) => {
    const p = db.products.find(
        (p) => p.id === line.productId && p.tenant === ui.tenant,
      ),
      sku = p?.skus?.find((s) => s.spec === line.spec);
    if (
      !p ||
      !sku ||
      p.status !== "上架中" ||
      line.quantity > sku.stock ||
      !Number.isInteger(line.quantity) ||
      line.quantity < 1
    )
      throw new Error("购物袋中有商品不可购买，请重新确认");
    return { ...line, product: p, sku };
  });
  const subtotal = items.reduce((s, l) => s + l.sku.price * l.quantity, 0);
  let discount = 0;
  if (couponId) {
    const owned = db.ownedCoupons.find(
        (o) =>
          o.tenant === ui.tenant &&
          o.memberId === ui.memberId &&
          o.couponId === couponId &&
          !o.orderId,
      ),
      c = db.coupons.find((c) => c.id === couponId && c.tenant === ui.tenant);
    if (
      !owned ||
      !c ||
      c.end < new Date().toLocaleDateString("sv-SE") ||
      c.threshold > subtotal
    )
      throw new Error("优惠券不满足使用条件");
    discount = Math.min(c.amount, subtotal);
  }
  const shippingTemplate = db.shipping.find(
    (s) => s.tenant === ui.tenant && s.enabled,
  );
  if (!shippingTemplate && items.length)
    throw new Error("店铺尚未设置配送方式");
  const quantity = items.reduce((n, l) => n + l.quantity, 0);
  const shipping =
    !items.length ||
    (shippingTemplate!.freeAbove > 0 &&
      subtotal - discount >= shippingTemplate!.freeAbove)
      ? 0
      : shippingTemplate!.first +
        Math.max(0, quantity - 1) * shippingTemplate!.additional;
  return {
    items,
    subtotal,
    discount,
    shipping,
    total: subtotal - discount + shipping,
  };
}
function previewCheckout(addressId: number, couponId: number | null): string {
  assertTrading();
  if (!currentMember.value) throw new Error("请先登录");
  const address = db.addresses.find(
    (a) =>
      a.id === addressId &&
      a.tenant === ui.tenant &&
      a.memberId === ui.memberId,
  );
  if (!address) throw new Error("请选择收货地址");
  const q = quote(couponId);
  if (!q.items.length) throw new Error("请先选择商品");
  const discounts = allocateDiscount(
    q.items.map((l) => ({ price: l.sku.price, quantity: l.quantity })),
    q.discount,
  );
  const id = `QH${Date.now()}`;
  const order: Order = {
    id,
    tenant: ui.tenant,
    memberId: ui.memberId,
    buyer: address.name,
    phone: address.phone,
    address: `${address.region} ${address.detail}`,
    items: q.items.map((l, i) => ({
      productId: l.product.id,
      name: l.product.name,
      image: l.product.image,
      spec: l.spec,
      price: l.sku.price,
      quantity: l.quantity,
      discount: discounts[i]!,
      refunded: 0,
      returnedQuantity: 0,
    })),
    total: q.total,
    shipping: q.shipping,
    coupon: couponId,
    status: "待付款",
    created: timestamp(),
    carrier: "",
    tracking: "",
    history: ["订单已创建"],
  };
  for (const line of q.items) {
    line.product.stock -= line.quantity;
    line.sku.stock -= line.quantity;
  }
  if (couponId)
    db.ownedCoupons.find(
      (c) =>
        c.tenant === ui.tenant &&
        c.memberId === ui.memberId &&
        c.couponId === couponId &&
        !c.orderId,
    )!.orderId = id;
  db.orders.unshift(order);
  db.cart = db.cart.filter(
    (l) => l.tenant !== ui.tenant || l.memberId !== ui.memberId || !l.checked,
  );
  log("创建订单", id);
  return id;
}
function ownOrder(id: string) {
  const o = db.orders.find(
    (o) => o.id === id && o.tenant === ui.tenant && o.memberId === ui.memberId,
  );
  if (!o) throw new Error("订单不存在");
  return o;
}
async function previewPayOrder(id: string) {
  const o = ownOrder(id);
  if (o.status !== "待付款") throw new Error("订单状态已变化，请刷新");
  if (import.meta.env.MODE === "prototype" && o.seckillRequestId) {
    const m = await import("./seckill-demo");
    m.demoSeckillPaid(o.seckillRequestId);
  }
  o.status = "待发货";
  o.history.push("付款成功");
  for (const item of o.items) {
    const p = db.products.find(
      (p) => p.id === item.productId && p.tenant === o.tenant,
    );
    if (p) p.sales += item.quantity;
  }
  const m = currentMember.value;
  if (m) {
    m.orders++;
    m.paid += o.total - o.shipping;
  }
  log("确认付款", id);
  notify("付款已确认");
}
async function previewCancelOrder(id: string) {
  const o = ownOrder(id);
  if (o.status !== "待付款") throw new Error("当前订单不能取消");
  if (import.meta.env.MODE === "prototype" && o.seckillRequestId) {
    const m = await import("./seckill-demo");
    m.demoSeckillCancelled(o.seckillRequestId);
  }
  o.status = "已取消";
  for (const item of o.seckillRequestId ? [] : o.items) {
    const p = db.products.find(
      (p) => p.id === item.productId && p.tenant === o.tenant,
    );
    if (p) {
      p.stock += item.quantity;
      const sku = p.skus?.find((s) => s.spec === item.spec);
      if (sku) sku.stock += item.quantity;
    }
  }
  const c = db.ownedCoupons.find(
    (c) => c.orderId === id && c.tenant === o.tenant,
  );
  if (c) c.orderId = null;
  o.history.push("订单已取消");
  log("取消订单", id);
  notify("订单已取消");
}
function previewShipOrder(id: string, carrier: string, tracking: string) {
  requirePermission("order");
  const o = db.orders.find((o) => o.id === id && o.tenant === ui.tenant);
  if (!o || o.status !== "待发货") throw new Error("当前订单不能发货");
  if (
    db.afterSales.some(
      (a) =>
        a.orderId === id &&
        !["退款成功", "已拒绝", "已撤销"].includes(a.status),
    )
  )
    throw new Error("订单有进行中的售后，请先处理");
  if (!carrier || !/^[a-zA-Z0-9-]{6,40}$/.test(tracking))
    throw new Error("请填写承运商和有效运单号");
  o.status = "已发货";
  o.carrier = carrier;
  o.tracking = tracking;
  o.history.push(`已发货 · ${carrier} ${tracking}`);
  log("确认发货", id);
  notify("发货成功");
}
function previewReceiveOrder(id: string) {
  const o = ownOrder(id);
  if (o.status !== "已发货") throw new Error("当前订单不能确认收货");
  if (
    db.afterSales.some(
      (a) =>
        a.orderId === id &&
        !["退款成功", "已拒绝", "已撤销"].includes(a.status),
    )
  )
    throw new Error("请先处理进行中的售后");
  o.status = "已完成";
  o.history.push("已确认收货");
  notify("已确认收货");
}
function previewApplyAfterSale(
  orderId: string,
  itemIndex: number,
  quantity: number,
  amount: number,
  type: AfterSale["type"],
  reason: string,
) {
  const o = ownOrder(orderId),
    item = o.items[itemIndex];
  if (!["待发货", "已发货", "已完成"].includes(o.status) || !item)
    throw new Error("当前订单不能申请售后");
  if (type === "退货退款" && o.status === "待发货")
    throw new Error("未发货商品请选择仅退款");
  if (
    db.afterSales.some(
      (a) =>
        a.orderId === o.id &&
        a.itemIndex === itemIndex &&
        !["退款成功", "已拒绝", "已撤销"].includes(a.status),
    )
  )
    throw new Error("该商品已有进行中的售后");
  if (
    !Number.isSafeInteger(quantity) ||
    quantity < 1 ||
    quantity > item.quantity - item.returnedQuantity ||
    !Number.isSafeInteger(amount) ||
    amount <= 0 ||
    amount > unitRefund(item, quantity)
  )
    throw new Error("申请数量或金额超出可退范围");
  if (!reason.trim()) throw new Error("请选择售后原因");
  db.afterSales.unshift({
    id: `AS${Date.now()}`,
    tenant: ui.tenant,
    orderId,
    itemIndex,
    quantity,
    amount,
    type,
    reason,
    status: "待审核",
    created: timestamp(),
    tracking: "",
    sellable: false,
  });
  log("申请售后", orderId);
  notify("售后申请已提交");
}
function previewReviewAfterSale(a: AfterSale, accept: boolean, reason = "") {
  requirePermission("order");
  assertTenant(a.tenant);
  if (a.status !== "待审核") throw new Error("申请状态已变化");
  if (!accept && !reason.trim()) throw new Error("请填写拒绝原因");
  a.status = accept
    ? a.type === "退货退款"
      ? "待买家退货"
      : "待退款"
    : "已拒绝";
  if (!accept) a.reason += `；审核意见：${reason}`;
  log(accept ? "通过售后审核" : "拒绝售后", a.id);
  notify("审核已保存");
}
function previewReturnShipment(a: AfterSale, tracking: string) {
  const o = ownOrder(a.orderId);
  if (a.status !== "待买家退货" || o.tenant !== a.tenant)
    throw new Error("当前状态不能填写物流");
  if (!/^[a-zA-Z0-9-]{6,40}$/.test(tracking))
    throw new Error("请填写有效退货运单号");
  a.tracking = tracking;
  a.status = "待商家收货";
  notify("退货物流已保存");
}
function previewInspectReturn(a: AfterSale, sellable: boolean) {
  requirePermission("order");
  assertTenant(a.tenant);
  if (a.status !== "待商家收货") throw new Error("当前状态不能验收");
  a.sellable = sellable;
  a.status = "待退款";
  log("退货验收", a.id);
  notify("验收结果已保存");
}
function previewExecuteRefund(a: AfterSale) {
  requirePermission("refund");
  assertTenant(a.tenant);
  if (a.status !== "待退款") throw new Error("当前申请不能执行退款");
  const o = db.orders.find(
      (o) => o.id === a.orderId && o.tenant === ui.tenant,
    )!,
    item = o.items[a.itemIndex]!;
  if (a.amount > refundable(item)) throw new Error("金额超出剩余可退范围");
  const fullUnits = a.amount === unitRefund(item, a.quantity);
  item.refunded += a.amount;
  a.status = "退款成功";
  if (fullUnits) item.returnedQuantity += a.quantity;
  if (
    (o.status === "待发货" && fullUnits) ||
    (a.type === "退货退款" && a.sellable)
  ) {
    const p = db.products.find(
      (p) => p.id === item.productId && p.tenant === o.tenant,
    );
    if (p) {
      p.stock += a.quantity;
      const sku = p.skus?.find((s) => s.spec === item.spec);
      if (sku) sku.stock += a.quantity;
    }
  }
  if (o.items.every((i) => refundable(i) === 0)) {
    if (o.status === "待发货") {
      o.history.push(`原运费已退 ¥${(o.shipping / 100).toFixed(2)}`);
      o.status = "已取消";
      const c = db.ownedCoupons.find(
        (c) => c.orderId === o.id && c.tenant === o.tenant,
      );
      if (c) c.orderId = null;
    } else o.history.push("商品款已退清，原运费保留");
  }
  o.history.push(`退款成功 · ¥${(a.amount / 100).toFixed(2)}`);
  log("执行退款", `${a.id} / ${a.amount}分`);
  notify("退款已完成");
}
function previewSaveAddress(
  a: Omit<Address, "id" | "tenant" | "memberId">,
  id?: number,
) {
  if (
    !a.name.trim() ||
    !/^1\d{10}$/.test(a.phone) ||
    !a.region.trim() ||
    !a.detail.trim()
  )
    throw new Error("请填写完整地址和有效手机号");
  if (a.isDefault)
    db.addresses
      .filter((x) => x.tenant === ui.tenant && x.memberId === ui.memberId)
      .forEach((x) => (x.isDefault = false));
  if (id) {
    const x = db.addresses.find(
      (x) =>
        x.id === id && x.tenant === ui.tenant && x.memberId === ui.memberId,
    );
    if (!x) throw new Error("地址不存在");
    Object.assign(x, a);
  } else
    db.addresses.push({
      ...a,
      id: Date.now(),
      tenant: ui.tenant,
      memberId: ui.memberId,
    });
  notify("地址已保存");
}
function previewExecuteCampaign(id: number) {
  requirePermission("marketing");
  assertTrading();
  const c = db.campaigns.find((c) => c.id === id && c.tenant === ui.tenant);
  if (!c || ["已中止", "已完成"].includes(c.status))
    throw new Error("活动当前不可执行");
  c.status = "执行中";
  for (const memberId of c.recipients) {
    if (c.sent.includes(memberId) || c.skipped.includes(memberId)) continue;
    const m = db.members.find(
      (m) => m.id === memberId && m.tenant === ui.tenant,
    );
    if (!m || m.optOut) {
      c.skipped.push(memberId);
      continue;
    }
    try {
      previewClaimCoupon(c.couponId, memberId, c.id);
      c.sent.push(memberId);
      db.notifications.push({
        id: Date.now() + memberId,
        tenant: ui.tenant,
        memberId,
        title: "专属优惠券已到账",
        content: c.name,
        read: false,
      });
    } catch {
      c.skipped.push(memberId);
    }
  }
  c.status = "已完成";
  log("执行发券活动", c.name);
  notify("发券已完成");
}
export const previewUnavailable = ref(false);

export const saveProduct = (...args: Parameters<typeof previewSaveProduct>) =>
  command("product.save", { input: args[0], id: args[1] }, () =>
    previewSaveProduct(...args),
  );
export const toggleProduct = (p: Product) =>
  command(
    "product.status",
    { id: p.id, status: p.status === "上架中" ? "已下架" : "上架中" },
    () => previewToggleProduct(p),
  );
export const adjustStock = (...args: Parameters<typeof previewAdjustStock>) =>
  command(
    "inventory.adjust",
    { productId: args[0].id, delta: args[1], reason: args[2], spec: args[3] },
    () => previewAdjustStock(...args),
  );
export const addToCart = (p: Product, spec: string, quantity: number) =>
  command("cart.add", { productId: p.id, spec, quantity }, () =>
    previewAddToCart(p, spec, quantity),
  );
export const claimCoupon = (id: number) =>
  command("coupon.claim", { id }, () => previewClaimCoupon(id));
export const checkout = (addressId: number, couponId: number | null) => {
  if (!demo && !serverQuote.value) throw new Error("请先确认订单金额");
  return command(
    "order.create",
    { addressId, couponId, quoteToken: serverQuote.value?.token },
    () => previewCheckout(addressId, couponId),
  );
};
export async function payOrder(id: string) {
  if (demo) return command("payment.start", { id }, () => previewPayOrder(id));
  const result = await command<{ redirectUrl: string }>(
    "payment.start",
    { id },
    () => {
      throw new Error("支付服务不可用");
    },
  );
  window.location.assign(
    safePaymentUrl(
      result.redirectUrl,
      window.location.origin,
      String(import.meta.env.VITE_PAYMENT_ORIGINS || "")
        .split(",")
        .filter(Boolean),
    ),
  );
}
export const cancelOrder = (id: string) =>
  command("order.cancel", { id }, () => previewCancelOrder(id));
export const shipOrder = (id: string, carrier: string, tracking: string) =>
  command("order.ship", { id, carrier, tracking }, () =>
    previewShipOrder(id, carrier, tracking),
  );
export const receiveOrder = (id: string) =>
  command("order.receive", { id }, () => previewReceiveOrder(id));
export const applyAfterSale = (
  ...args: Parameters<typeof previewApplyAfterSale>
) =>
  command(
    "aftersale.apply",
    {
      orderId: args[0],
      itemIndex: args[1],
      quantity: args[2],
      amount: args[3],
      type: args[4],
      reason: args[5],
    },
    () => previewApplyAfterSale(...args),
  );
export const reviewAfterSale = (a: AfterSale, accept: boolean, reason = "") =>
  command("aftersale.review", { id: a.id, accept, reason }, () =>
    previewReviewAfterSale(a, accept, reason),
  );
export const returnShipment = (a: AfterSale, tracking: string) =>
  command("aftersale.returnShipment", { id: a.id, tracking }, () =>
    previewReturnShipment(a, tracking),
  );
export const inspectReturn = (a: AfterSale, sellable: boolean) =>
  command("aftersale.inspect", { id: a.id, sellable }, () =>
    previewInspectReturn(a, sellable),
  );
export const executeRefund = (a: AfterSale) =>
  command("aftersale.refund", { id: a.id }, () => previewExecuteRefund(a));
export const saveAddress = (...args: Parameters<typeof previewSaveAddress>) =>
  command("address.save", { input: args[0], id: args[1] }, () =>
    previewSaveAddress(...args),
  );
export const executeCampaign = (id: number) =>
  command("campaign.execute", { id }, () => previewExecuteCampaign(id));
type ServerQuote = {
  token: string;
  subtotal: number;
  discount: number;
  shipping: number;
  total: number;
};
export const serverQuote = ref<ServerQuote | null>(null);
let quoteGeneration = 0;
export async function refreshQuote(
  addressId: number | undefined,
  couponId: number | null,
) {
  const generation = ++quoteGeneration;
  serverQuote.value = null;
  if (demo || !addressId || !identities.member) return;
  const result = await request<ServerQuote>(
    `${prefix("member")}/commerce/quote`,
    credentials(),
    { method: "POST", body: JSON.stringify({ addressId, couponId }) },
  );
  if (generation === quoteGeneration) {
    if (
      !result ||
      typeof result.token !== "string" ||
      !result.token ||
      [result.subtotal, result.discount, result.shipping, result.total].some(
        (n) => !Number.isSafeInteger(n) || n < 0,
      )
    )
      throw new Error("结算金额异常，请重新确认");
    serverQuote.value = result;
  }
}

export function takeDownProducts(ids: number[]) {
  return command("product.takeDown", { ids }, () => {
    for (const p of db.products.filter(
      (p) =>
        p.tenant === ui.tenant && ids.includes(p.id) && p.status === "上架中",
    ))
      previewToggleProduct(p);
  });
}
