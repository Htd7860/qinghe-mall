package cn.qinghe.mall.module.promotion.api.seckill.dto;
import lombok.Data;
/** 只读权威事件与受理快照，来源服务在同一RR读快照内核对；不表示允许盲目创建订单。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillDispatchSnapshotDTO {
    private SeckillEventDTO event;
    private SeckillAcceptedRequestDTO request;
}
