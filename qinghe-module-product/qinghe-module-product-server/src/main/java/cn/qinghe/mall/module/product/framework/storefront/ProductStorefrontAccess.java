package cn.qinghe.mall.module.product.framework.storefront;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 直接择取已有Member/PromotionStorefrontAccess；不从前端tenant/转发头定位，不建立匿名登录身份。 */
@Service
public class ProductStorefrontAccess {
    private final ProductStorefrontApi api;
    private final SecurityProperties security;
    private final String token;
    private final Semaphore calls=new Semaphore(16);
    public ProductStorefrontAccess(ProductStorefrontApi api,SecurityProperties security,
            @Value("${qinghe.product.storefront.service-token:}") String token) {
        this.api=api;this.security=security;
        if(token==null || !token.isEmpty() && !token.matches("[A-Za-z0-9_-]{43,128}"))throw new IllegalArgumentException("商品目录只读凭据格式不正确");
        this.token=token;
    }
    public MemberStorefrontRespDTO resolve(HttpServletRequest request,String store) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("店铺解析不能置于商品读事务内");
        if(request==null || TenantContextHolder.isIgnore())throw exception0(403,"商城入口上下文不正确");
        List<String> hosts=Collections.list(request.getHeaders("Host"));
        if(hosts.size()!=1 || hosts.get(0)==null || hosts.get(0).isEmpty() || hosts.get(0).length()>300
                || store!=null && !store.matches("[a-z0-9-]{3,32}"))throw exception0(400,"请提供唯一有效的商城入口");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();String bearer;
        if(actor!=null) {
            if(actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
            if(!UserTypeEnum.MEMBER.getValue().equals(actor.getUserType()) || !id(actor.getTenantId())
                    || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"当前身份不属于此商城");
            bearer=SecurityFrameworkUtils.obtainAuthorization(request,security.getTokenHeader());
            if(bearer==null || bearer.length()>128)throw exception0(401,"请重新登录");
        } else {bearer=token;if(bearer.isEmpty())throw exception0(503,"商品目录只读入口尚未配置");}
        if(!calls.tryAcquire())throw exception0(503,"商城入口解析繁忙");
        try {
            MemberStorefrontReqDTO body=new MemberStorefrontReqDTO();body.setRequestHost(hosts.get(0));body.setStore(store);
            CommonResult<MemberStorefrontRespDTO> result;
            try {result=actor==null?api.resolve("Bearer "+bearer,body):api.current("Bearer "+bearer,body);}
            catch(RuntimeException unavailable){throw exception0(503,"商城入口服务暂不可用");}
            if(result==null || result.getCode()==null)throw incomplete();
            if(Arrays.asList(400,401,403,404).contains(result.getCode()))throw exception0(result.getCode(),"商城入口或当前身份已变化，请重新打开店铺");
            if(!result.isSuccess())throw exception0(503,"商城入口服务暂不可用");
            MemberStorefrontRespDTO entry=result.getData();
            if(entry==null || !id(entry.getTenantId()) || entry.getTenantCode()==null || !entry.getTenantCode().matches("[a-z0-9-]{3,32}")
                    || store!=null && !store.equals(entry.getTenantCode()) || entry.getCanPurchase()==null
                    || !version(entry.getTenantVersion()) || entry.getLocalCodeLookup()==null)throw incomplete();
            if(Boolean.TRUE.equals(entry.getLocalCodeLookup())) {
                if(entry.getEntryVersion()!=null || entry.getBindingVersion()!=null)throw incomplete();
            } else if(!version(entry.getEntryVersion()) || !version(entry.getBindingVersion()))throw incomplete();
            Long claimed=TenantContextHolder.getTenantId();
            if(claimed!=null && !claimed.equals(entry.getTenantId()) || actor!=null && !actor.getTenantId().equals(entry.getTenantId()))throw exception0(403,"商城入口与请求身份不匹配");
            return entry;
        } finally {calls.release();}
    }
    private boolean id(Long n){return n!=null && n>0 && n<=9007199254740991L;}
    private boolean version(Long n){return n!=null && n>=0 && n<=9007199254740991L;}
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete(){return exception0(503,"商城入口归属信息不完整");}
}
