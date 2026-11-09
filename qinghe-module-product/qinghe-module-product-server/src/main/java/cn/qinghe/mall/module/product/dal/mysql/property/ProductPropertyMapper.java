package cn.qinghe.mall.module.product.dal.mysql.property;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.product.controller.admin.property.vo.property.ProductPropertyPageReqVO;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductPropertyMapper extends BaseMapperX<ProductPropertyDO> {

    default PageResult<ProductPropertyDO> selectPage(ProductPropertyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductPropertyDO>()
                .likeIfPresent(ProductPropertyDO::getName, reqVO.getName())
                .eqIfPresent(ProductPropertyDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProductPropertyDO::getType, reqVO.getType())
                .betweenIfPresent(ProductPropertyDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ProductPropertyDO::getId));
    }

    default ProductPropertyDO selectByName(String name, Integer type) {
        return selectOne(new LambdaQueryWrapperX<ProductPropertyDO>()
                .eq(ProductPropertyDO::getName, name)
                .eq(ProductPropertyDO::getType, type));
    }

}
