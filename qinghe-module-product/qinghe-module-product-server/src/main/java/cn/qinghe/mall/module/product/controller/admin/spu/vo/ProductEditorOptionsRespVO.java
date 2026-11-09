package cn.qinghe.mall.module.product.controller.admin.spu.vo;
import lombok.Data;
/** 与服务端实际SKU上限一致的表单保护参数；不可用它替代服务端校验。 */
@Data
public class ProductEditorOptionsRespVO {
    private Integer maxSkus;
    private Integer maxDimensions;
}
