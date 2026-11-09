package cn.qinghe.mall.module.system.api.tenant.dto;

import lombok.Data;

/** 认证所需入口投影；不含联系人、套餐资源、账号或服务密钥。 */
@Data
public class MemberStorefrontRespDTO {
    private Long tenantId;
    private String tenantCode;
    private Boolean canPurchase;
    private Long tenantVersion;
    private Long entryVersion;
    private Long bindingVersion;
    private Boolean localCodeLookup;
}
