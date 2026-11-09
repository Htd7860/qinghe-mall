package cn.qinghe.mall.framework.common.biz.system.permission.dto;
import lombok.Data;
import java.util.Set;

/** 当前Bearer员工的实时范围；不得用客户端提交的同名字段替代。 */
@Data
public class UserDataScopeRespDTO {
    private Long tenantId;
    private Long userId;
    private Long currentDeptId;
    private Boolean all;
    private Boolean self;
    private Set<Long> deptIds;
    public boolean includes(Long ownerUserId, Long ownerDeptId) {
        return ownerUserId != null && ownerUserId > 0 && (Boolean.TRUE.equals(all)
                || (Boolean.TRUE.equals(self) && ownerUserId.equals(userId))
                || (ownerDeptId != null && deptIds != null && deptIds.contains(ownerDeptId)));
    }
}
