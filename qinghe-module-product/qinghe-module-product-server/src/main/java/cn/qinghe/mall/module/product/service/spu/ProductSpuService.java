package cn.qinghe.mall.module.product.service.spu;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.*;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import java.util.Map;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
/** 择取yudao商品保存/读取契约；HTTP须白名单投影，不直接把DO暴露给消费者。
 * @author 芋道源码
 */
public interface ProductSpuService {
    cn.qinghe.mall.framework.common.biz.system.permission.dto.ProductOwnerRespDTO getOwnerOption(Long userId);
    void transferOwner(ProductSpuTransferReqVO body);
    Long createSpu(ProductSpuSaveReqVO body);
    void updateSpu(ProductSpuSaveReqVO body);
    ProductSpuDO getSpu(Long id);
    ProductSpuRespVO getSpuDetail(Long id);
    PageResult<ProductSpuDO> getSpuPage(ProductSpuPageReqVO body);
    Map<Integer,Long> getTabsCount(ProductSpuPageReqVO body);
    void updateSpuStatus(ProductSpuUpdateStatusReqVO body);
    void deleteSpu(Long id,Long expectedVersion);
}
