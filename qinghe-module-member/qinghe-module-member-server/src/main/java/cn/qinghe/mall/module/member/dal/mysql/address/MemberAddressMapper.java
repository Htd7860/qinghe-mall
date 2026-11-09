package cn.qinghe.mall.module.member.dal.mysql.address;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.member.dal.dataobject.address.MemberAddressDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
/** 原版地址ID+本人查询/默认筛选直接保留，补可信tenant和显式更新白名单。
 * @author 芋道源码
 */
@Mapper
public interface MemberAddressMapper extends BaseMapperX<MemberAddressDO> {
    default LambdaQueryWrapperX<MemberAddressDO> owned(Long userId) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        if (tenant<=0 || TenantContextHolder.isIgnore() || userId==null || userId<=0) { throw new IllegalStateException("地址查询缺少可信会员身份"); }
        return new LambdaQueryWrapperX<MemberAddressDO>().eq(MemberAddressDO::getTenantId,tenant).eq(MemberAddressDO::getUserId,userId);
    }
    default MemberAddressDO selectByIdAndUserId(Long id,Long userId) {
        return selectOne(owned(userId).eq(MemberAddressDO::getId,id));
    }
    default List<MemberAddressDO> selectListByUserIdAndDefaulted(Long userId,Boolean defaulted) {
        return selectList(owned(userId).eqIfPresent(MemberAddressDO::getDefaultStatus,defaulted)
                .orderByDesc(MemberAddressDO::getDefaultStatus).orderByDesc(MemberAddressDO::getId));
    }
    default Long countByUserId(Long userId) { return selectCount(owned(userId)); }
    @Update("UPDATE member_address SET name=#{row.name},mobile=#{row.mobile},area_id=#{row.areaId},detail_address=#{row.detailAddress}, "
            + "default_status=#{row.defaultStatus},auth_version=auth_version+1,updater=#{userId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND id=#{id} AND deleted=b'0' AND auth_version=#{version}")
    int updateOwned(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("id") Long id,
            @Param("version") Long version,@Param("row") MemberAddressDO row);
    @Update("UPDATE member_address SET default_status=b'0',auth_version=auth_version+1,updater=#{userId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND id=#{id} AND deleted=b'0' AND default_status=b'1' AND auth_version=#{version}")
    int clearDefault(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("id") Long id,@Param("version") Long version);
    @Update("UPDATE member_address SET deleted=b'1',auth_version=auth_version+1,updater=#{userId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND id=#{id} AND deleted=b'0' AND auth_version=#{version}")
    int deleteOwned(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("id") Long id,@Param("version") Long version);
    @Insert("INSERT INTO member_address_audit(tenant_id,user_id,address_id,action,from_version,to_version) VALUES(#{tenantId},#{userId},#{id},#{action},#{from},#{to})")
    int audit(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("id") Long id,
            @Param("action") String action,@Param("from") Long from,@Param("to") Long to);
}
