package cn.qinghe.mall.module.trade.service.delivery.bo;
import lombok.Data;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.*;
import java.util.List;
/** 由可信只读事务读取的当前配置；不得由HTTP请求反序列化替代真实模板。 */
@Data
public class DeliveryTemplateSnapshotBO {
    private DeliveryExpressTemplateDO template;
    private List<DeliveryExpressTemplateChargeDO> charges;
    private List<DeliveryExpressTemplateFreeDO> frees;
}
