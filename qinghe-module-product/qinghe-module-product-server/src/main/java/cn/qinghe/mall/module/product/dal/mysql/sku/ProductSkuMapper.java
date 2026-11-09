package cn.qinghe.mall.module.product.dal.mysql.sku;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 取自yudao SKU按SPU查询形式；空集合不扩大查询，不提供任意绝对库存覆盖接口。 */
@Mapper
public interface ProductSkuMapper extends BaseMapperX<ProductSkuDO> {
    default List<ProductSkuDO> lockByIdsAndSpuId(Long spuId,Collection<Long> ids) {
        cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils.requireWriteTransaction();
        if (cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("库存写入不能忽略租户");
        }
        if (ids == null || ids.isEmpty()) { return Collections.emptyList(); }
        return selectList(new LambdaQueryWrapperX<ProductSkuDO>().eq(ProductSkuDO::getSpuId,spuId)
                .eq(ProductSkuDO::getTenantId,cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())
                .in(ProductSkuDO::getId,ids).orderByAsc(ProductSkuDO::getId).last("FOR UPDATE"));
    }

    default List<ProductSkuDO> lockBySpuId(Long spuId) {
        cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils.requireWriteTransaction();
        if (cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("SKU写入不能忽略租户");
        }
        return selectList(new LambdaQueryWrapperX<ProductSkuDO>().eq(ProductSkuDO::getSpuId,spuId)
                .eq(ProductSkuDO::getTenantId,cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())
                .orderByAsc(ProductSkuDO::getId).last("FOR UPDATE"));
    }

    @Select("SELECT COALESCE(MIN(price),0) AS price,COALESCE(MIN(market_price),0) AS market_price, "
            + "COALESCE(MIN(cost_price),0) AS cost_price,COALESCE(SUM(stock),0) AS stock,COUNT(*) AS enabled_count "
            + "FROM product_sku WHERE tenant_id=#{tenantId} AND spu_id=#{spuId} AND deleted=b'0' AND status=0")
    cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuSkuSummary summarize(@Param("tenantId") Long tenantId,@Param("spuId") Long spuId);

    @Update("UPDATE product_sku SET properties=CAST(#{properties} AS JSON),price=#{row.price},market_price=#{row.marketPrice}, "
            + "cost_price=#{row.costPrice},bar_code=#{row.barCode},pic_url=#{row.picUrl},weight=#{row.weight},volume=#{row.volume}, "
            + "status=#{row.status},auth_version=auth_version+1,updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND spu_id=#{spuId} AND id=#{id} AND deleted=b'0' AND auth_version=#{expectedVersion}")
    int updateMetadata(@Param("tenantId") Long tenantId,@Param("spuId") Long spuId,@Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,@Param("row") ProductSkuDO row,
            @Param("properties") String properties,@Param("actorId") String actorId);

    @Update("UPDATE product_sku SET status=1,auth_version=auth_version+1,updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND spu_id=#{spuId} AND id=#{id} AND status=0 AND deleted=b'0' AND auth_version=#{expectedVersion}")
    int retire(@Param("tenantId") Long tenantId,@Param("spuId") Long spuId,@Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,@Param("actorId") String actorId);

    default List<ProductSkuDO> selectListBySpuId(Long spuId) {
        if (spuId == null || spuId <= 0) { return Collections.emptyList(); }
        return selectList(new LambdaQueryWrapperX<ProductSkuDO>().eq(ProductSkuDO::getSpuId, spuId)
                .orderByAsc(ProductSkuDO::getId));
    }

    default List<ProductSkuDO> selectListBySpuId(Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) { return Collections.emptyList(); }
        return selectList(new LambdaQueryWrapperX<ProductSkuDO>().in(ProductSkuDO::getSpuId, spuIds));
    }
}
