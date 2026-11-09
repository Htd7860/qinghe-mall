package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.security.core.filter.TradeCommandAuthenticationFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;
import javax.servlet.http.HttpServletRequest;

/** 当前退款命令的调用边界：可信后台，或精确内部POST经现有服务Token过滤器认证。 */
public final class SeckillRefundCommandContext {
    public static final String RECOVER="/internal-api/promotion/seckill-refund/recover";
    public static final String RESULT="/internal-api/promotion/seckill-refund/result";
    private SeckillRefundCommandContext() {}
    public static void requireCaller() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("退款资源RPC/缓存必须在本地事务外");
        RequestAttributes attributes=RequestContextHolder.getRequestAttributes();
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(attributes==null && auth==null)return;
        if(!(attributes instanceof ServletRequestAttributes) || auth==null || !auth.isAuthenticated()
                || !"trade-order-service".equals(auth.getPrincipal()) || auth.getAuthorities().size()!=1
                || auth.getAuthorities().stream().noneMatch(a->TradeCommandAuthenticationFilter.AUTHORITY.equals(a.getAuthority())))
            throw new IllegalStateException("退款资源只能由可信后台或原trade服务推进");
        HttpServletRequest request=((ServletRequestAttributes)attributes).getRequest();
        if(!"POST".equals(request.getMethod()) || !RECOVER.equals(request.getServletPath()))
            throw new IllegalStateException("只有精确原退款恢复命令可以推进资源");
    }
}
