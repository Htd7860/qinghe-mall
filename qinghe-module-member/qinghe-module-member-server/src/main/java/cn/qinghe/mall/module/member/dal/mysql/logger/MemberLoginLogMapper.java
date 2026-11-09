package cn.qinghe.mall.module.member.dal.mysql.logger;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.member.dal.dataobject.logger.MemberLoginLogDO;
import org.apache.ibatis.annotations.Mapper;

/** 原LoginLogMapper的插入职责，当前没有开放查询/导出或变更历史接口。
 * @author 芋道源码
 */
@Mapper
public interface MemberLoginLogMapper extends BaseMapperX<MemberLoginLogDO> { }
