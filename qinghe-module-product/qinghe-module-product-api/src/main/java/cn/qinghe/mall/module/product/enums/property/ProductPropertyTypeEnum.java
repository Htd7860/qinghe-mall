package cn.qinghe.mall.module.product.enums.property;

/** 需求基线B04：销售规格参与SKU组合，展示属性不参与库存组合。 */
public enum ProductPropertyTypeEnum {
    SALES(1), DISPLAY(2);

    private final int value;
    ProductPropertyTypeEnum(int value) { this.value = value; }
    public int getValue() { return value; }
}
