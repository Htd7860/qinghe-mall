package cn.qinghe.mall.module.system.dal.mysql.permission;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.system.dal.dataobject.permission.ManagementAuditDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ManagementAuditMapper extends BaseMapperX<ManagementAuditDO> {
}
