package cn.qinghe.mall.module.product.api.stock;

/** 可信 trade 调用方须先证明创建失败，或支付入口已关闭且确定未支付。 */
public enum ProductStockReleaseReason {
    CREATE_FAILED,
    UNPAID_CLOSED
}
