package cn.qinghe.mall.module.promotion.controller.app.coupon.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
/** 择取原App模板/本人券的券面字段，不投影后台统计/用户/订单/业务键。 @author 芋道源码 */
@Data
public class AppCouponRulesRespVO {
    private Long id;
    private String name;
    private Integer usePrice;
    private Integer productScope;
    private List<Long> productScopeValues;
    private Integer discountType;
    private Integer discountPercent;
    private Integer discountPrice;
    private Integer discountLimitPrice;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="Asia/Shanghai") private LocalDateTime validStartTime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="Asia/Shanghai") private LocalDateTime validEndTime;
}
