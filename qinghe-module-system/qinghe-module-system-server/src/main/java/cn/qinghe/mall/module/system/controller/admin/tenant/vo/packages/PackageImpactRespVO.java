package cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages;
import lombok.Data;
import java.util.*;
@Data
public class PackageImpactRespVO {
    private Long packageId;private Long expectedVersion;private String impactToken;
    private Integer affectedTenants;private Integer changedRoles;private Integer addedBindings;private Integer removedBindings;
    private List<TenantImpact> tenants=new ArrayList<>();
    @Data
    public static class TenantImpact {
        private Long tenantId;private String code;private Long expectedVersion;private Boolean deleted;
        private Integer changedRoles;private Integer addedBindings;private Integer removedBindings;
    }
}
