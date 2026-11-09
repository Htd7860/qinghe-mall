package cn.qinghe.mall.module.promotion.framework.product;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.product.api.coupon.dto.CouponProductScopeReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
@Service
public class CouponProductScopeClient {
    private final CouponProductScopeApi api;
    private final SecurityProperties security;
    private final Semaphore calls=new Semaphore(16);
    public CouponProductScopeClient(CouponProductScopeApi api,SecurityProperties security,
            @Value("${qinghe.promotion.product-base-url:http://127.0.0.1:8082}") String source) {
        URI uri=URI.create(source);
        if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && Arrays.asList("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost())))throw new IllegalArgumentException("商品校验来源必须为固定可信origin");
        this.api=api;this.security=security;
    }
    public void validate(Integer scope,List<Long> ids) {
        if(scope==1)return;
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("商品范围RPC不能在券库写事务内执行");
        RequestAttributes attrs=RequestContextHolder.getRequestAttributes();
        if(!(attrs instanceof ServletRequestAttributes))throw exception0(503,"商品范围校验缺少当前请求");
        String token=SecurityFrameworkUtils.obtainAuthorization(((ServletRequestAttributes)attrs).getRequest(),security.getTokenHeader());
        if(token==null || token.length()>128)throw exception0(401,"缺少当前访问凭据");
        if(!calls.tryAcquire())throw exception0(503,"商品范围校验繁忙");
        try {
            CouponProductScopeReqDTO body=new CouponProductScopeReqDTO();body.setProductScope(scope);body.setIds(new ArrayList<>(ids));
            CommonResult<Boolean> result;
            try {result=api.validate("Bearer "+token,body);}catch(RuntimeException unavailable){throw exception0(503,"商品范围校验暂不可用");}
            if(result!=null && (Integer.valueOf(400).equals(result.getCode()) || Integer.valueOf(404).equals(result.getCode())))throw exception0(400,"商品范围不存在、已停用或不在当前数据范围内");
            if(result==null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData()))throw exception0(503,"商品范围校验未确认");
        } finally {calls.release();}
    }
}
