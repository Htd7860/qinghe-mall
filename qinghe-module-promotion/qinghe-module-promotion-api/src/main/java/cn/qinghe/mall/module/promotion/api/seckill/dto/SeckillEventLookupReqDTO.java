package cn.qinghe.mall.module.promotion.api.seckill.dto;
import lombok.Data;
/** 事件号仅作定位；租户由专用服务认证头确定，其他内容由来源库读取。 */
@Data
public class SeckillEventLookupReqDTO { private String eventId; }
