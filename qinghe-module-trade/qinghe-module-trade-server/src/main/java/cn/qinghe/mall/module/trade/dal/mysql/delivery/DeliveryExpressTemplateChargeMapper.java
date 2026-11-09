package cn.qinghe.mall.module.trade.dal.mysql.delivery;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateChargeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
/** 择取yudao同名Mapper的模板内查询，真实租户过滤由既有插件处理。 */
@Mapper
public interface DeliveryExpressTemplateChargeMapper extends BaseMapperX<DeliveryExpressTemplateChargeDO> {
    @Update("UPDATE trade_delivery_express_template_charge SET deleted=b'1',updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND template_id=#{templateId} AND id=#{id} AND deleted=b'0'")
    int retire(@Param("tenantId") Long tenantId,@Param("templateId") Long templateId,@Param("id") Long id,@Param("actorId") String actorId);

    @Update("UPDATE trade_delivery_express_template_charge SET area_ids=CAST(#{areas} AS JSON),charge_mode=#{row.chargeMode},start_count=#{row.startCount},start_price=#{row.startPrice},extra_count=#{row.extraCount},extra_price=#{row.extraPrice}, "
            + "default_rule=#{row.defaultRule},deleted=b'0',updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND template_id=#{templateId} AND id=#{id}")
    int updateRetained(@Param("tenantId") Long tenantId,@Param("templateId") Long templateId,@Param("id") Long id,
            @Param("row") DeliveryExpressTemplateChargeDO row,@Param("areas") String areas,@Param("actorId") String actorId);

    default java.util.List<DeliveryExpressTemplateChargeDO> selectListByTemplateId(Long templateId) {
        if (templateId == null || templateId <= 0) { return java.util.Collections.emptyList(); }
        return selectList(new LambdaQueryWrapperX<DeliveryExpressTemplateChargeDO>().eq(DeliveryExpressTemplateChargeDO::getTemplateId,templateId).orderByAsc(DeliveryExpressTemplateChargeDO::getId));
    }
}
