package cn.qinghe.mall.module.trade.dal.mysql.delivery;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate.DeliveryExpressTemplatePageReqVO;
/** 择取yudao同名Mapper的模板内查询，真实租户过滤由既有插件处理。 */
@Mapper
public interface DeliveryExpressTemplateMapper extends BaseMapperX<DeliveryExpressTemplateDO> {
    default DeliveryExpressTemplateDO lockById(Long id) {
        cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils.requireWriteTransaction();
        return selectOne(new LambdaQueryWrapperX<DeliveryExpressTemplateDO>().eq(DeliveryExpressTemplateDO::getId,id)
                .eq(DeliveryExpressTemplateDO::getTenantId,cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())
                .last("FOR UPDATE"));
    }
    default PageResult<DeliveryExpressTemplateDO> selectPage(DeliveryExpressTemplatePageReqVO body) {
        return selectPage(body,new LambdaQueryWrapperX<DeliveryExpressTemplateDO>().likeIfPresent(DeliveryExpressTemplateDO::getName,body.getName())
                .eqIfPresent(DeliveryExpressTemplateDO::getStatus,body.getStatus()).eqIfPresent(DeliveryExpressTemplateDO::getChargeMode,body.getChargeMode())
                .betweenIfPresent(DeliveryExpressTemplateDO::getCreateTime,body.getCreateTime())
                .orderByAsc(DeliveryExpressTemplateDO::getSort).orderByDesc(DeliveryExpressTemplateDO::getId));
    }
    @Update("UPDATE trade_delivery_express_template SET name=#{row.name},charge_mode=#{row.chargeMode},sort=#{row.sort},status=#{row.status}, "
            + "auth_version=auth_version+1,updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND auth_version=#{expected}")
    int updateConfiguration(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expected") Long expected,
            @Param("row") DeliveryExpressTemplateDO row,@Param("actorId") String actorId);
    @Update("UPDATE trade_delivery_express_template SET deleted=b'1',status=1,auth_version=auth_version+1,updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND auth_version=#{expected}")
    int retire(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expected") Long expected,@Param("actorId") String actorId);
    @Insert("INSERT INTO trade_delivery_template_audit(tenant_id,actor_user_id,action,target_id,from_version,to_version,details) "
            + "VALUES(#{tenantId},#{actorId},#{action},#{targetId},#{from},#{to},#{details})")
    int audit(@Param("tenantId") Long tenantId,@Param("actorId") Long actorId,@Param("action") String action,
            @Param("targetId") Long targetId,@Param("from") Long from,@Param("to") Long to,@Param("details") String details);

    default DeliveryExpressTemplateDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapperX<DeliveryExpressTemplateDO>().eq(DeliveryExpressTemplateDO::getName,name));
    }
}
