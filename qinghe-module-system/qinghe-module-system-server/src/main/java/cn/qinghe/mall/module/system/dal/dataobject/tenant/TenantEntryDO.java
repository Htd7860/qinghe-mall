package cn.qinghe.mall.module.system.dal.dataobject.tenant;
import cn.qinghe.mall.framework.mybatis.core.dataobject.BaseDo;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;
/** B02入口唯一映射；替代yudao websites集合。停用/逻辑删除均保留host占用。 */
@Data
@EqualsAndHashCode(callSuper=true)
@ToString(onlyExplicitlyIncluded=true)
@TableName("system_tenant_entry")
@TenantIgnore(enable="true")
public class TenantEntryDO extends BaseDo {
    @TableId(type=IdType.AUTO) private Long id;
    private Long tenantId;
    private String host;
    private String purpose;
    private String kind;
    private Integer status;
    private Long bindingVersion;
    private Long verifiedTenantId;
    private Long verifiedBindingVersion;
    private LocalDateTime domainVerifiedAt;
    private LocalDateTime httpsVerifiedAt;
    private Long authVersion;
}
