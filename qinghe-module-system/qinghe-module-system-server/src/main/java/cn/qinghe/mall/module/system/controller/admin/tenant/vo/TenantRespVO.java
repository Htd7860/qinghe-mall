package cn.qinghe.mall.module.system.controller.admin.tenant.vo;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class TenantRespVO {
    private Long id; private String code; private String name;
    private Long contactUserId; private Long adminRoleId;
    private String contactName; private String contactMobile;
    private Integer status; private Long packageId; private LocalDateTime expireTime;
    private Integer accountCount; private Long authVersion; private Boolean deleted;
    private LocalDateTime createTime;
    public static TenantRespVO from(TenantDO row) {
        TenantRespVO out=new TenantRespVO();out.setId(row.getId());out.setCode(row.getCode());out.setName(row.getName());
        out.setContactUserId(row.getContactUserId());out.setAdminRoleId(row.getAdminRoleId());
        out.setContactName(row.getContactName());out.setContactMobile(row.getContactMobile());
        out.setStatus(row.getStatus());out.setPackageId(row.getPackageId());out.setExpireTime(row.getExpireTime());
        out.setAccountCount(row.getAccountCount());out.setAuthVersion(row.getAuthVersion());out.setDeleted(row.getDeleted());
        out.setCreateTime(row.getCreateTime());return out;
    }
}
