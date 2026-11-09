package cn.qinghe.mall.module.product.dal.mysql.property;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.product.controller.admin.property.vo.value.ProductPropertyValuePageReqVO;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyValueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductPropertyValueMapper extends BaseMapperX<ProductPropertyValueDO> {

    default List<ProductPropertyValueDO> selectListByPropertyId(Collection<Long> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<ProductPropertyValueDO>()
                .inIfPresent(ProductPropertyValueDO::getPropertyId, propertyIds));
    }

    default ProductPropertyValueDO selectByName(Long propertyId, String name) {
        return selectOne(new LambdaQueryWrapperX<ProductPropertyValueDO>()
                .eq(ProductPropertyValueDO::getPropertyId, propertyId)
                .eq(ProductPropertyValueDO::getName, name));
    }

    default void deleteByPropertyId(Long propertyId) {
        delete(new LambdaQueryWrapperX<ProductPropertyValueDO>()
                .eq(ProductPropertyValueDO::getPropertyId, propertyId));
    }

    default PageResult<ProductPropertyValueDO> selectPage(ProductPropertyValuePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductPropertyValueDO>()
                .eqIfPresent(ProductPropertyValueDO::getPropertyId, reqVO.getPropertyId())
                .eqIfPresent(ProductPropertyValueDO::getStatus, reqVO.getStatus())
                .likeIfPresent(ProductPropertyValueDO::getName, reqVO.getName())
                .orderByDesc(ProductPropertyValueDO::getId));
    }

    default Integer selectCountByPropertyId(Long propertyId) {
        return selectCount(ProductPropertyValueDO::getPropertyId, propertyId).intValue();
    }

}
