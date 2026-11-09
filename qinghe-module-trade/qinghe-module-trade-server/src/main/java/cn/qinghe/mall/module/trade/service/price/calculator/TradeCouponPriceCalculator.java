package cn.qinghe.mall.module.trade.service.price.calculator;
import cn.qinghe.mall.module.promotion.api.coupon.dto.*;
import cn.qinghe.mall.module.promotion.core.coupon.CouponPriceCalculator;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.List;
import java.util.Objects;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原TradeCouponPriceCalculator职责；选中行必须来自trade权威商品读取，非浏览器金额。
 * 本批计算内核复用，购物车/结算HTTP和promotion受保护RPC仍待后续接入。
 * @author 芋道源码
 */
@Component
@lombok.RequiredArgsConstructor
public class TradeCouponPriceCalculator {
    private final cn.qinghe.mall.module.trade.framework.cart.CartProperties limits;
    public CouponQuoteDTO calculate(Long trustedUserId,CouponRespDTO trustedCoupon,List<CouponOrderLineDTO> authoritativeLines,boolean normalOrder) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        if(tenant<=0 || TenantContextHolder.isIgnore() || trustedUserId==null || trustedUserId<=0 || trustedCoupon==null
                || !Objects.equals(tenant,trustedCoupon.getTenantId()) || !Objects.equals(trustedUserId,trustedCoupon.getUserId()))throw exception0(403,"优惠券不属于当前结算会员");
        if(authoritativeLines==null || authoritativeLines.size()>limits.getMaxOrderLines() || authoritativeLines.stream().anyMatch(x->x==null || x.getQuantity()==null || x.getQuantity()>limits.getMaxItemQuantity()))throw exception0(400,"结算商品超出当前资源限制");
        return CouponPriceCalculator.calculate(trustedCoupon,authoritativeLines,normalOrder,LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }
}
