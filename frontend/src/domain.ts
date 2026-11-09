export type ProductSku = { spec: string; price: number; stock: number };
export type Product = {
  id: number;
  tenant: string;
  name: string;
  subtitle: string;
  category: string;
  image: string;
  price: number;
  stock: number;
  sales: number;
  status: "上架中" | "已下架";
  colors: string[];
  sizes: string[];
  updated: string;
  skus?: ProductSku[];
};
export type OrderStatus = "待付款" | "待发货" | "已发货" | "已完成" | "已取消";
export type OrderItem = {
  productId: number;
  name: string;
  image: string;
  spec: string;
  price: number;
  quantity: number;
  discount: number;
  refunded: number;
  returnedQuantity: number;
};
export type Order = {
  seckillRequestId?: string;
  id: string;
  tenant: string;
  memberId: number;
  buyer: string;
  phone: string;
  address: string;
  items: OrderItem[];
  total: number;
  shipping: number;
  coupon: number | null;
  status: OrderStatus;
  created: string;
  carrier: string;
  tracking: string;
  history: string[];
};
export type AfterSale = {
  id: string;
  tenant: string;
  orderId: string;
  itemIndex: number;
  quantity: number;
  amount: number;
  type: "仅退款" | "退货退款";
  reason: string;
  status:
    | "待审核"
    | "待买家退货"
    | "待商家收货"
    | "待退款"
    | "退款处理中"
    | "退款成功"
    | "已拒绝"
    | "已撤销";
  created: string;
  tracking: string;
  sellable: boolean;
};
export type Coupon = {
  id: number;
  tenant: string;
  name: string;
  amount: number;
  threshold: number;
  total: number;
  issued: number;
  status: "发放中" | "已暂停";
  end: string;
  limit: number;
};
export type Member = {
  id: number;
  tenant: string;
  name: string;
  phone: string;
  orders: number;
  paid: number;
  tags: string[];
  optOut: boolean;
  registered: string;
};
export type Staff = {
  id: number;
  name: string;
  username: string;
  tenant: string;
  role: string;
  department: string;
  status: "正常" | "已停用";
};
export type Tenant = {
  code: string;
  name: string;
  owner: string;
  domain: string;
  package: string;
  expire: string;
  status: "正常" | "已停用";
  quota: number;
};
export type Campaign = {
  id: number;
  tenant: string;
  name: string;
  couponId: number;
  tag: string;
  minimumOrders: number;
  recipients: number[];
  sent: number[];
  skipped: number[];
  status: "待执行" | "执行中" | "已完成" | "已中止";
  scheduled: string;
};
export type Address = {
  id: number;
  tenant: string;
  memberId: number;
  name: string;
  phone: string;
  region: string;
  detail: string;
  isDefault: boolean;
};
export type CartLine = {
  memberId: number;
  tenant: string;
  productId: number;
  spec: string;
  quantity: number;
  checked: boolean;
};
export type Role = {
  id: number;
  tenant: string;
  name: string;
  code: string;
  permissions: string[];
  status: "正常" | "已停用";
};
export type Audit = {
  id: number;
  tenant: string;
  action: string;
  subject: string;
  actor: string;
  at: string;
};
export type OwnedCoupon = {
  id: string;
  tenant: string;
  memberId: number;
  couponId: number;
  orderId: string | null;
  campaignId?: number;
};
export const money = (cents: number) =>
  (cents / 100).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
export const timestamp = () =>
  new Date().toLocaleString("sv-SE", { timeZone: "Asia/Shanghai" });
export const canReturnCoupon = (paid: number, refunded: number) =>
  refunded >= paid;
export function allocateDiscount(
  lines: { price: number; quantity: number }[],
  discount: number,
): number[] {
  if (
    lines.some(
      (l) =>
        !Number.isSafeInteger(l.price) ||
        l.price < 0 ||
        !Number.isSafeInteger(l.quantity) ||
        l.quantity < 1,
    )
  )
    throw new Error("商品金额或数量无效");
  const weights = lines.map((l) => l.price * l.quantity);
  const sum = weights.reduce((a, b) => a + b, 0);
  if (
    !Number.isSafeInteger(sum) ||
    !Number.isSafeInteger(discount) ||
    discount < 0 ||
    discount > sum
  )
    throw new Error("优惠金额无效");
  if (!sum) return weights.map(() => 0);
  const allocations = weights.map((w) =>
    Number((BigInt(w) * BigInt(discount)) / BigInt(sum)),
  );
  const remainders = weights
    .map((w, i) => ({ i, r: (BigInt(w) * BigInt(discount)) % BigInt(sum) }))
    .sort((a, b) => (a.r === b.r ? a.i - b.i : a.r > b.r ? -1 : 1));
  let rest = discount - allocations.reduce((a, b) => a + b, 0);
  for (const row of remainders) {
    if (!rest--) break;
    allocations[row.i]!++;
  }
  return allocations;
}
export function refundable(item: OrderItem) {
  return item.price * item.quantity - item.discount - item.refunded;
}
export function unitRefund(item: OrderItem, count: number) {
  const base = Math.floor(item.discount / item.quantity),
    remainder = item.discount % item.quantity;
  let amount = 0;
  for (let i = item.returnedQuantity; i < item.returnedQuantity + count; i++)
    amount += item.price - base - (i < remainder ? 1 : 0);
  return Math.min(amount, refundable(item));
}
