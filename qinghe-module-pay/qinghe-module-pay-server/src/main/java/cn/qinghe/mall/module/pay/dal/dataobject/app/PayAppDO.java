package cn.qinghe.mall.module.pay.dal.dataobject.app;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
/** 择取原PayAppDO字段；每租户独立支付应用/支付账，创建与查询固定SQL，不逻辑删除资金记录。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class PayAppDO {
    private Long id;
    private Long tenantId;
    private String appKey;
    private String name;
    private Integer status;
    private Long authVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
