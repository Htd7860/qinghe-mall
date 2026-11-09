package cn.qinghe.mall.module.system.dal.dataobject.tenant;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.Map;
import java.time.LocalDateTime;
@Data
@TenantIgnore
@TableName(value="system_tenant_package_change",autoResultMap=true)
public class TenantPackageChangeDO {
    @TableId(type=IdType.AUTO) private Long id;
    private String operationId;private Long packageId;private Long actorUserId;private String requestHash;
    private Long fromVersion;private Long toVersion;private Boolean changed;
    @TableField(typeHandler=JacksonTypeHandler.class) private Map<String,Object> impact;
    private LocalDateTime createTime;
}
