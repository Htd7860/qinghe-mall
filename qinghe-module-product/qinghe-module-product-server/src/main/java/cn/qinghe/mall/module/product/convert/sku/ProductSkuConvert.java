package cn.qinghe.mall.module.product.convert.sku;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 择取yudao ProductSkuConvert.buildPropertyKey：复制、排序、生成组合身份。
 * 原版仅拼接valueId；本项目明确property:value分隔并SHA256，避免[1,234]/[12,34]歧义。
 * 组合摘要只含稳定编号，不含会改名的显示文本；它不是加密或权限凭据。
 * @author 芋道源码
 */
public final class ProductSkuConvert {
    private ProductSkuConvert() { }

    public static String buildPropertyKey(ProductSkuDO bean) {
        Objects.requireNonNull(bean, "SKU不能为空");
        List<ProductSkuDO.Property> properties = bean.getProperties();
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("必须先规范化SKU属性");
        }
        List<ProductSkuDO.Property> sorted = new ArrayList<>(properties);
        Set<Long> dimensions = new HashSet<>();
        for (ProductSkuDO.Property property : sorted) {
            if (property == null || property.getPropertyId() == null || property.getValueId() == null
                    || property.getPropertyId() < 0 || property.getValueId() < 0
                    || !dimensions.add(property.getPropertyId())
                    || ((property.getPropertyId() == 0 || property.getValueId() == 0)
                        && !(sorted.size() == 1 && property.getPropertyId() == 0 && property.getValueId() == 0))) {
                throw new IllegalArgumentException("SKU属性未正确规范化");
            }
        }
        sorted.sort(Comparator.comparing(ProductSkuDO.Property::getPropertyId));
        String canonical = sorted.stream().map(property -> property.getPropertyId() + ":" + property.getValueId())
                .collect(Collectors.joining(";"));
        return DigestUtil.sha256Hex(canonical);
    }
}
