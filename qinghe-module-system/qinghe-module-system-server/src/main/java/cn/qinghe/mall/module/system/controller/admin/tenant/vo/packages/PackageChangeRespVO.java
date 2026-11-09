package cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageChangeDO;
import lombok.Data;
import java.util.Map;
import java.time.LocalDateTime;
@Data
public class PackageChangeRespVO {
    private String operationId;private Long packageId;private Long actorUserId;
    private Long fromVersion;private Long toVersion;private Boolean changed;
    private Map<String,Object> impact;private LocalDateTime createTime;
    public static PackageChangeRespVO from(TenantPackageChangeDO row) {
        PackageChangeRespVO out=new PackageChangeRespVO();out.setOperationId(row.getOperationId());out.setPackageId(row.getPackageId());
        out.setActorUserId(row.getActorUserId());out.setFromVersion(row.getFromVersion());out.setToVersion(row.getToVersion());
        out.setChanged(row.getChanged());out.setImpact(row.getImpact());out.setCreateTime(row.getCreateTime());return out;
    }
}
