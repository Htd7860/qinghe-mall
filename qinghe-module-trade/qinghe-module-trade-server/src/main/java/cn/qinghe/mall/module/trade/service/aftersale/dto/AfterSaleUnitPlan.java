package cn.qinghe.mall.module.trade.service.aftersale.dto;

import lombok.Value;
import java.util.List;

/** 仅内部不可变计算结果；持久化调用方须在原订单父锁内读取占用并保存。
 * 原运费分摊是成交快照，绝非当前可退运费或授权打款结果。 */
@Value
public class AfterSaleUnitPlan {
    Long orderId;
    Long orderItemId;
    Long expectedOrderVersion;
    Integer way;
    Integer count;
    Long goodsRefundPrice;
    Long originalDeliveryShare;
    List<Unit> units;

    @Value
    public static class Unit {
        /** 原订单项内从0开始的固定购买单位序号，不接受前端自由指定。 */
        Integer unitNo;
        Long goodsPrice;
        Long originalDeliveryShare;
    }
}
