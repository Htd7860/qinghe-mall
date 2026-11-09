package cn.qinghe.mall.module.trade.api.seckill.dto;
import lombok.Data;
/** 只接受来源原事件定位，不能从调用方指定订单或结果。 */
@Data
public class SeckillOrderResultLookupDTO {
    private String eventId;
}
