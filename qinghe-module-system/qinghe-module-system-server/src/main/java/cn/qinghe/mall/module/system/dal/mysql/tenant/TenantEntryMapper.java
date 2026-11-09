package cn.qinghe.mall.module.system.dal.mysql.tenant;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantEntryDO;
import org.apache.ibatis.annotations.*;
/** 全局入口定位包含停用/已删占位，不因过滤后查不到而回退别的租户。 */
@Mapper
public interface TenantEntryMapper extends BaseMapperX<TenantEntryDO> {
    @Select("SELECT * FROM system_tenant_entry WHERE host=#{host}")
    TenantEntryDO selectReservedByHost(@Param("host") String host);
}
