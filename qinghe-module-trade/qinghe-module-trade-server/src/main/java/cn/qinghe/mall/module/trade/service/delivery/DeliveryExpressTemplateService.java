package cn.qinghe.mall.module.trade.service.delivery;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO;
import cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate.*;
import cn.qinghe.mall.module.trade.service.delivery.bo.DeliveryTemplateSnapshotBO;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import java.util.Collection;
import java.util.List;
/** 择取yudao模板校验职责，管理/核价接口按后续片段继续接入。
 * @author jason
 */
public interface DeliveryExpressTemplateService {
    DeliveryExpressTemplateDO validateDeliveryExpressTemplate(Long templateId);
    DeliveryTemplateSnapshotBO getSnapshot(Long templateId);
    java.util.Map<Long,DeliveryTemplateSnapshotBO> getCheckoutSnapshots(Collection<Long> ids);
    Long createDeliveryExpressTemplate(DeliveryExpressTemplateCreateReqVO body);
    void updateDeliveryExpressTemplate(DeliveryExpressTemplateUpdateReqVO body);
    void deleteDeliveryExpressTemplate(Long id,Long expectedVersion);
    DeliveryExpressTemplateDetailRespVO getDeliveryExpressTemplate(Long id);
    PageResult<DeliveryExpressTemplateDO> getDeliveryExpressTemplatePage(DeliveryExpressTemplatePageReqVO body);
    List<DeliveryExpressTemplateDO> getDeliveryExpressTemplateList(Collection<Long> ids);
    List<DeliveryExpressTemplateDO> getDeliveryExpressTemplateList();
}
