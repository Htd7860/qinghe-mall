package cn.qinghe.mall.module.system.controller.app.storefront.vo;
import lombok.Data;
/** 公开品牌入口投影，不含联系人、手机号、套餐资源或账号信息。目标定位不代表认证。 */
@Data
public class StorefrontRespVO {
    private Long tenantId;
    private String tenantCode;
    private String name;
    private Boolean canPurchase;
    private Long tenantVersion;
    private Long entryVersion;
    private Long bindingVersion;
    private Boolean localCodeLookup;
}
