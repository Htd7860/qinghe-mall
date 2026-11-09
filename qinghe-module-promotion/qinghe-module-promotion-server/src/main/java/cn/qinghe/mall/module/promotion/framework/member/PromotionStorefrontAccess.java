package cn.qinghe.mall.module.promotion.framework.member;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取既有MemberStorefrontClient.resolveRequest，复用system入口规则；匿名定位不构造登录身份。 @author 芋道源码 */
@Service
public class PromotionStorefrontAccess {
    private final CouponStorefrontApi api;
    private final CouponMemberAccess members;
    private final String token;
    private final Semaphore calls=new Semaphore(16);
    public PromotionStorefrontAccess(CouponStorefrontApi api,CouponMemberAccess members,
            @Value("${qinghe.promotion.storefront.service-token:}") String token) {
        this.api=api;this.members=members;
        if(token==null || !token.isEmpty() && !token.matches("[A-Za-z0-9_-]{43,128}"))
            throw new IllegalArgumentException("商城只读入口凭据格式不正确");
        this.token=token;
    }
    public MemberStorefrontRespDTO resolve(HttpServletRequest request,String store) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("店铺解析必须在浏览事务外");
        if(request==null || TenantContextHolder.isIgnore())throw exception0(403,"商城入口上下文不正确");
        // 携带真实身份时继续原本店会员检查，不能借匿名读取切换已认证租户。
        if(SecurityFrameworkUtils.getLoginUser()!=null)return members.check(request,store);
        List<String> hosts=Collections.list(request.getHeaders("Host"));
        if(hosts.size()!=1 || hosts.get(0)==null || hosts.get(0).isEmpty() || hosts.get(0).length()>300
                || store!=null && !store.matches("[a-z0-9-]{3,32}"))throw exception0(400,"请提供唯一有效的商城入口");
        if(token.isEmpty())throw exception0(503,"商城只读入口凭据未配置");
        if(!calls.tryAcquire())throw exception0(503,"商城入口解析繁忙");
        try {
            MemberStorefrontReqDTO body=new MemberStorefrontReqDTO();body.setRequestHost(hosts.get(0));body.setStore(store);
            CommonResult<MemberStorefrontRespDTO> result;
            try {result=api.resolve("Bearer "+token,body);}catch(RuntimeException ex){throw exception0(503,"商城入口服务暂不可用");}
            if(result==null || result.getCode()==null)throw exception0(503,"商城入口结果不完整");
            if(Arrays.asList(400,403,404).contains(result.getCode()))throw exception0(result.getCode(),"商城入口不存在、已停用或与店铺不匹配");
            if(!result.isSuccess())throw exception0(503,"商城入口服务暂不可用");
            MemberStorefrontRespDTO entry=result.getData();
            if(entry==null || !id(entry.getTenantId()) || entry.getTenantCode()==null || !entry.getTenantCode().matches("[a-z0-9-]{3,32}")
                    || store!=null && !store.equals(entry.getTenantCode()) || entry.getCanPurchase()==null
                    || !version(entry.getTenantVersion()) || entry.getLocalCodeLookup()==null)throw incomplete();
            if(Boolean.TRUE.equals(entry.getLocalCodeLookup())) {
                if(entry.getEntryVersion()!=null || entry.getBindingVersion()!=null)throw incomplete();
            } else if(!version(entry.getEntryVersion()) || !version(entry.getBindingVersion()))throw incomplete();
            Long claimed=TenantContextHolder.getTenantId();
            if(claimed!=null && !claimed.equals(entry.getTenantId()))throw exception0(403,"商城入口与请求租户不匹配");
            return entry;
        } finally {calls.release();}
    }
    private boolean id(Long n){return n!=null && n>0 && n<=9007199254740991L;}
    private boolean version(Long n){return n!=null && n>=0 && n<=9007199254740991L;}
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete(){return exception0(503,"商城入口归属信息不完整");}
}
