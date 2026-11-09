import type { ProductSku, SkuProperty } from "./product";
/** Extracted from yudao-ui-admin-vue3 SkuList.vue build(). MIT (c) 2021-present Archer.
 * The recursive combination algorithm is retained; preflight bounds avoid allocating an enormous Cartesian product.
 */
export function buildSkuCombinations(
  propertyValuesList: SkuProperty[][],
  maxSkus: number,
): SkuProperty[][] {
  if (
    !Number.isSafeInteger(maxSkus) ||
    maxSkus < 1 ||
    maxSkus > 2000 ||
    propertyValuesList.length > 20
  )
    throw new Error("规格组合数量配置不正确");
  let count = 1;
  for (const values of propertyValuesList) {
    if (!values.length) throw new Error("每个销售规格至少选择一个值");
    if (values.length > Math.floor(maxSkus / count))
      throw new Error(`规格组合超过${maxSkus}条，请减少所选规格值`);
    count *= values.length;
  }
  return build(propertyValuesList);
}
function build(propertyValuesList: SkuProperty[][]): SkuProperty[][] {
  if (propertyValuesList.length === 0) {
    return [];
  } else if (propertyValuesList.length === 1) {
    return propertyValuesList[0].map((item) => [item]);
  } else {
    const result: SkuProperty[][] = [];
    const rest = build(propertyValuesList.slice(1));
    for (let i = 0; i < propertyValuesList[0].length; i++) {
      for (let j = 0; j < rest.length; j++) {
        result.push([propertyValuesList[0][i], ...rest[j]]);
      }
    }
    return result;
  }
}
/** Same stable identity semantics as backend; names and selection order do not identify a SKU. */
export function skuCombinationKey(properties: SkuProperty[]) {
  return [...properties]
    .sort((a, b) => a.propertyId - b.propertyId)
    .map((value) => `${value.propertyId}:${value.valueId}`)
    .join(";");
}
export type EditorSku = {
  id?: number;
  expectedVersion?: number;
  properties: SkuProperty[];
  price: string;
  marketPrice: string;
  costPrice: string;
  barCode: string;
  picUrl: string;
  stock: string;
  currentStock?: number;
  weight: string;
  volume: string;
  status: number;
};
export function skuFromSaved(sku: ProductSku): EditorSku {
  return {
    id: sku.id,
    expectedVersion: sku.authVersion,
    properties: sku.properties.map((value) => ({ ...value })),
    price: (sku.price / 100).toFixed(2),
    marketPrice: (sku.marketPrice / 100).toFixed(2),
    costPrice: (sku.costPrice / 100).toFixed(2),
    barCode: sku.barCode ?? "",
    picUrl: sku.picUrl,
    stock: "",
    currentStock: sku.stock,
    weight: sku.weight ?? "",
    volume: sku.volume ?? "",
    status: sku.status,
  };
}
export function amountInCents(value: string, positive = false) {
  const text = value.trim();
  if (!/^\d{1,8}(?:\.\d{1,2})?$/.test(text))
    throw new Error("价格须为非负金额，最多两位小数");
  const [whole, fraction = ""] = text.split(".");
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (
    !Number.isSafeInteger(cents) ||
    cents > 2147483647 ||
    (positive && cents === 0)
  )
    throw new Error("售价须大于零，金额不能超过支持上限");
  return cents;
}
export function positiveMeasure(value: string) {
  const text = value.trim();
  if (!text) return null;
  if (!/^\d{1,12}(?:\.\d{1,6})?$/.test(text) || /^0+(?:\.0+)?$/.test(text))
    throw new Error("重量或体积须为正数，最多12位整数和6位小数");
  return text;
}
export function skuSaveFields(sku: EditorSku) {
  const row: Record<string, unknown> = {
    price: amountInCents(sku.price, true),
    marketPrice: amountInCents(sku.marketPrice || "0"),
    costPrice: amountInCents(sku.costPrice || "0"),
    barCode: sku.barCode.trim(),
    picUrl: sku.picUrl.trim(),
    weight: positiveMeasure(sku.weight),
    volume: positiveMeasure(sku.volume),
    status: sku.status,
    properties: sku.properties.map((value) => ({
      propertyId: value.propertyId,
      valueId: value.valueId,
    })),
  };
  if (
    !row.picUrl ||
    String(row.picUrl).length > 1024 ||
    sku.barCode.trim().length > 100 ||
    ![0, 1].includes(sku.status)
  )
    throw new Error("请检查SKU图片、条码和启停状态");
  if (sku.id !== undefined) {
    if (
      !Number.isSafeInteger(sku.expectedVersion) ||
      Number(sku.expectedVersion) < 0
    )
      throw new Error("已有SKU版本不完整，请重新读取");
    row.id = sku.id;
    row.expectedVersion = sku.expectedVersion;
  } else {
    if (!/^\d{1,10}$/.test(sku.stock.trim()) || Number(sku.stock) > 2147483647)
      throw new Error("新SKU须填写有效的非负初始库存");
    row.stock = Number(sku.stock);
  }
  return row;
}
