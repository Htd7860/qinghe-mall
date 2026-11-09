package cn.qinghe.mall.module.system.service.permission;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.role.RolePageReqVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;

/** 内部角色管理读取；入口调用方负责认证及资源权限检查。 */
public interface RoleService {

    RoleDO getRole(Long id);

    PageResult<RoleDO> getRolePage(RolePageReqVO reqVO);
}
