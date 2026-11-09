package cn.qinghe.mall.module.promotion.service.seckill.dto;
import lombok.Data;

/** 消费者响应白名单：10已持久受理/20已持久拒绝，单凭decision不表示已建单或可付款。 */
@Data
public class SeckillAdmissionAttemptDTO {
    private String requestNo;
    private String clientRequestId;
    private Integer decision;
    private Integer failureCode;
    private Boolean redisSynchronized;
    private Boolean quoteClosed;
    private SeckillRequestReceiptDTO request;
}
