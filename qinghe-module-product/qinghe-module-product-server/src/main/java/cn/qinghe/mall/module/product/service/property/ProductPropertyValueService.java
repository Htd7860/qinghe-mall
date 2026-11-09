package cn.qinghe.mall.module.product.service.property;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.product.controller.admin.property.vo.value.ProductPropertyValuePageReqVO;
import cn.qinghe.mall.module.product.controller.admin.property.vo.value.ProductPropertyValueSaveReqVO;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyValueDO;

import java.util.Collection;
import java.util.List;
import javax.validation.Valid;

/**
 * 商品属性值 Service 接口
 *
 * @author LuoWenFeng
 */
public interface ProductPropertyValueService {

    /**
     * 创建属性值
     * 注意，如果已经存在该属性值，直接返回它的编号即可
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createPropertyValue(@Valid ProductPropertyValueSaveReqVO createReqVO);

    /**
     * 更新属性值
     *
     * @param updateReqVO 更新信息
     */
    void updatePropertyValue(@Valid ProductPropertyValueSaveReqVO updateReqVO);

    /**
     * 删除属性值
     *
     * @param id 编号
     */
    void deletePropertyValue(Long id, Long expectedVersion);

    /**
     * 获得属性值
     *
     * @param id 编号
     * @return 属性值
     */
    ProductPropertyValueDO getPropertyValue(Long id);

    /**
     * 根据属性项编号数组，获得属性值列表
     *
     * @param propertyIds 属性项目编号数组
     * @return 属性值列表
     */
    List<ProductPropertyValueDO> getPropertyValueListByPropertyId(Collection<Long> propertyIds);

    /** 按实际所选值编号批量读取，不扫描属性下全部候选值。 */
    List<ProductPropertyValueDO> getPropertyValueListByIds(Collection<Long> ids);

    /**
     * 根据属性项编号，活的属性值数量
     *
     * @param propertyId 属性项编号数
     * @return 属性值数量
     */
    Integer getPropertyValueCountByPropertyId(Long propertyId);

    /**
     * 获取属性值的分页
     *
     * @param pageReqVO 查询条件
     * @return 属性值的分页
     */
    PageResult<ProductPropertyValueDO> getPropertyValuePage(ProductPropertyValuePageReqVO pageReqVO);



}
