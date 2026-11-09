package cn.qinghe.mall.module.pay.dal.dataobject.order;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
/** 择取原PayOrderDO字段；每租户独立支付应用/支付账，创建与查询固定SQL，不逻辑删除资金记录。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class PayOrderDO {
    private Long id;
    private Long tenantId;
    private Long appId;
    private Long userId;
    private Integer userType;
    private String merchantOrderId;
    private String subject;
    private Long price;
    private Integer status;
    private Boolean closing;
    private LocalDateTime expireTime;
    private LocalDateTime successTime;
    private Long refundPrice;
    /** 未确认退款占用；超时/失败保留，只能沿原退款号核查。 */
    private Long refundReserved;
    private Long authVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
