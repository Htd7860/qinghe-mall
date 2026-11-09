package cn.qinghe.mall.framework.common.biz.system.permission.dto;
import lombok.Data;
/** 商品归属转移的最小真实人员投影，不包含账号凭据/联系方式/角色。 */
@Data
public class ProductOwnerRespDTO {
    private Long tenantId;
    private Long ownerUserId;
    private String ownerName;
    private Long ownerDeptId;
    private String ownerDeptName;
    private Long userVersion;
    private Long deptVersion;
}
