package cn.qinghe.mall.framework.common.biz.system.permission;

import cn.qinghe.mall.framework.common.pojo.CommonResult;

/** 权限检查内部契约；由已认证调用方传入身份，不作为公开HTTP接口。 */
public interface PermissionCommonApi {

    /** 当前可信租户内，命中任一精确权限码即可；空集合拒绝。 */
    CommonResult<Boolean> hasAnyPermissions(Long userId, Long tenantId, String... permissions);
}
