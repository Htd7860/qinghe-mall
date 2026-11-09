package cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import lombok.Data;
import java.util.*;
import java.time.LocalDateTime;
@Data
public class PackageRespVO {
    private Long id;private String name;private Integer status;private String remark;
    private Set<Long> menuIds;private Long authVersion;private Boolean deleted;private LocalDateTime createTime;
    public static PackageRespVO from(TenantPackageDO row) {
        PackageRespVO out=new PackageRespVO();out.setId(row.getId());out.setName(row.getName());
        out.setStatus(row.getStatus());out.setRemark(row.getRemark());out.setMenuIds(new TreeSet<>(row.getMenuIds()));
        out.setAuthVersion(row.getAuthVersion());out.setDeleted(row.getDeleted());out.setCreateTime(row.getCreateTime());return out;
    }
}
