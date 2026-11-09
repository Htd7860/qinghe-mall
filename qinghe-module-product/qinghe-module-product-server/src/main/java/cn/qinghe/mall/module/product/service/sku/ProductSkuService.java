package cn.qinghe.mall.module.product.service.sku;

import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import java.util.List;

/** yudao SKU校验、查询和差集写入职责；写入须在SPU保存事务内，库存走独立流水。
 * @author 芋道源码
 */
public interface ProductSkuService {
    /** 真实查属性/值并规范化名称；调用方保存时仍须持有商品配置锁。 */
    void validateSkuList(List<ProductSkuSaveReqVO> skus, Boolean specType);
    /** 展示属性校验与命名快照，永远不进入SKU组合。 */
    List<ProductSkuDO.Property> validateDisplayProperties(List<ProductSkuSaveReqVO.Property> properties);
    /** 必须由SPU保存事务调用；新SPU已插入且版本为0，不能独立创建缺失SPU。 */
    void createSkuList(Long spuId,List<ProductSkuSaveReqVO> skus);
    /** 同一SPU事务内更新资料，推进父版本一次；外层负责SPU统一审计，不重复推进版本；其他资料须在同事务协调。 */
    void updateSkuList(Long spuId,Long expectedSpuVersion,List<ProductSkuSaveReqVO> skus);
    ProductSkuDO getSku(Long id);
    List<ProductSkuDO> getSkuListBySpuId(Long spuId);
}
