package cn.qinghe.mall.module.product.dal.mysql.spu;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.UserDataScopeRespDTO;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;

/** 取自yudao商品查询职责；当前提供真实分类/品牌引用检查，随后接SPU业务查询。 */
@Mapper
public interface ProductSpuMapper extends BaseMapperX<ProductSpuDO> {
    /** 仅库存原结果恢复读取归属，不开放普通商品含删除详情；不读取或返回商品敏感正文。 */
    @Select("SELECT id,tenant_id,owner_user_id,owner_dept_id FROM product_spu WHERE tenant_id=#{tenantId} AND id=#{id}")
    ProductSpuDO selectStockOwnerIncludingDeleted(@Param("tenantId") Long tenantId,@Param("id") Long id);

    default ProductSpuDO selectByIdForUpdate(Long id) {
        cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils.requireWriteTransaction();
        if (cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("商品写入不能忽略租户");
        }
        return selectOne(new LambdaQueryWrapperX<ProductSpuDO>().eq(ProductSpuDO::getId,id)
                .eq(ProductSpuDO::getTenantId,cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())
                .last("FOR UPDATE"));
    }

    @Update("UPDATE product_spu SET price=#{summary.price},market_price=#{summary.marketPrice},cost_price=#{summary.costPrice}, "
            + "stock=#{summary.stock},auth_version=auth_version+#{versionStep},updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND auth_version=#{expectedVersion}")
    int updateSkuSummary(@Param("tenantId") Long tenantId,@Param("id") Long id,
            @Param("summary") cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuSkuSummary summary,
            @Param("expectedVersion") Long expectedVersion,@Param("versionStep") Integer versionStep,@Param("actorId") String actorId);

    /** 资料白名单不含状态/归属/统计/库存/汇总价格；版本由同事务SKU保存统一推进。 */
    @Update("UPDATE product_spu SET name=#{row.name},keyword=#{row.keyword},introduction=#{row.introduction},description=#{row.description}, "
            + "category_id=#{row.categoryId},brand_id=#{row.brandId},pic_url=#{row.picUrl},slider_pic_urls=CAST(#{sliders} AS JSON), "
            + "sort=#{row.sort},spec_type=#{row.specType},delivery_template_id=#{row.deliveryTemplateId},display_properties=CAST(#{display} AS JSON), "
            + "updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND auth_version=#{expectedVersion}")
    int updateSaveFields(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expectedVersion") Long expectedVersion,
            @Param("row") ProductSpuDO row,@Param("sliders") String sliders,@Param("display") String display,@Param("actorId") String actorId);

    @Update("UPDATE product_spu SET status=#{status},auth_version=auth_version+1,updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND status=#{previous} AND auth_version=#{expected}")
    int updateStatus(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expected") Long expected,
            @Param("previous") Integer previous,@Param("status") Integer status,@Param("actorId") String actorId);

    @Update("UPDATE product_spu SET deleted=b'1',auth_version=auth_version+1,updater=#{actorId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND status=-1 AND auth_version=#{expected}")
    int retire(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expected") Long expected,@Param("actorId") String actorId);

    /** 归属专用白名单，不改变creator/库存/销量/状态；父版本与同事务审计一并推进。 */
    @Update("UPDATE product_spu SET owner_user_id=#{ownerId},owner_dept_id=#{deptId},auth_version=auth_version+1, "
            + "updater=#{actor},update_time=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{tenantId} AND id=#{id} "
            + "AND deleted=b'0' AND auth_version=#{expected}")
    int transferOwner(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("expected") Long expected,
            @Param("ownerId") Long ownerId,@Param("deptId") Long deptId,@Param("actor") String actor);

    default PageResult<ProductSpuDO> selectPage(ProductSpuPageReqVO body,UserDataScopeRespDTO scope,int alertStock) {
        return selectPage(body,managementQuery(body,scope,body.getTabType(),alertStock)
                .orderByDesc(ProductSpuDO::getSort).orderByDesc(ProductSpuDO::getId));
    }
    default Long selectCountByTab(ProductSpuPageReqVO body,UserDataScopeRespDTO scope,Integer tab,int alertStock) {
        return selectCount(managementQuery(body,scope,tab,alertStock));
    }
    /** 同一业务筛选/owner范围；固定200条游标分批，不读取详情或图片等大字段。 */
    default java.util.List<ProductSpuDO> selectExportBatch(ProductSpuPageReqVO body,UserDataScopeRespDTO scope,int alertStock,Long beforeId) {
        LambdaQueryWrapperX<ProductSpuDO> query=managementQuery(body,scope,body.getTabType(),alertStock);
        query.ltIfPresent(ProductSpuDO::getId,beforeId);
        return selectList(query.select(ProductSpuDO::getId,ProductSpuDO::getName,ProductSpuDO::getCategoryId,ProductSpuDO::getBrandId,
                        ProductSpuDO::getStatus,ProductSpuDO::getPrice,ProductSpuDO::getStock,ProductSpuDO::getSalesCount,
                        ProductSpuDO::getOwnerUserId,ProductSpuDO::getOwnerDeptId,ProductSpuDO::getCreateTime)
                .orderByDesc(ProductSpuDO::getId).last("LIMIT 200"));
    }

    default LambdaQueryWrapperX<ProductSpuDO> managementQuery(ProductSpuPageReqVO body,UserDataScopeRespDTO scope,Integer tab,int alertStock) {
        if (scope==null || cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.isIgnore()
                || !java.util.Objects.equals(scope.getTenantId(),cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalStateException("商品查询数据范围身份异常");
        }
        LambdaQueryWrapperX<ProductSpuDO> query=new LambdaQueryWrapperX<ProductSpuDO>()
                .eq(ProductSpuDO::getTenantId,scope.getTenantId()).likeIfPresent(ProductSpuDO::getName,body.getName())
                .eqIfPresent(ProductSpuDO::getCategoryId,body.getCategoryId()).eqIfPresent(ProductSpuDO::getBrandId,body.getBrandId())
                .betweenIfPresent(ProductSpuDO::getCreateTime,body.getCreateTime());
        if (!Boolean.TRUE.equals(scope.getAll())) {
            boolean self=Boolean.TRUE.equals(scope.getSelf());
            boolean depts=scope.getDeptIds()!=null && !scope.getDeptIds().isEmpty();
            if (!self && !depts) { query.apply("1=0"); }
            else { query.and(part -> {
                if (self) { part.eq(ProductSpuDO::getOwnerUserId,scope.getUserId()); }
                if (depts) { if (self) { part.or(); } part.in(ProductSpuDO::getOwnerDeptId,scope.getDeptIds()); }
            }); }
        }
        if (java.util.Objects.equals(tab,ProductSpuPageReqVO.FOR_SALE)) { query.eq(ProductSpuDO::getStatus,1); }
        else if (java.util.Objects.equals(tab,ProductSpuPageReqVO.IN_WAREHOUSE)) { query.eq(ProductSpuDO::getStatus,0); }
        else if (java.util.Objects.equals(tab,ProductSpuPageReqVO.SOLD_OUT)) { query.ne(ProductSpuDO::getStatus,-1).eq(ProductSpuDO::getStock,0); }
        else if (java.util.Objects.equals(tab,ProductSpuPageReqVO.ALERT_STOCK)) { query.ne(ProductSpuDO::getStatus,-1).le(ProductSpuDO::getStock,alertStock); }
        else if (java.util.Objects.equals(tab,ProductSpuPageReqVO.RECYCLE_BIN)) { query.eq(ProductSpuDO::getStatus,-1); }
        else { query.ne(ProductSpuDO::getStatus,-1); }
        return query;
    }

    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(new LambdaQueryWrapperX<ProductSpuDO>().eq(ProductSpuDO::getCategoryId, categoryId));
    }

    default Long selectCountByBrandId(Long brandId) {
        return selectCount(new LambdaQueryWrapperX<ProductSpuDO>().eq(ProductSpuDO::getBrandId, brandId));
    }
}
