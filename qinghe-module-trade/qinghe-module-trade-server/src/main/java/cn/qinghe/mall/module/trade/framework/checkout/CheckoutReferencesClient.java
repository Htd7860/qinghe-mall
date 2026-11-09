package cn.qinghe.mall.module.trade.framework.checkout;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.member.api.address.dto.MemberAddressRespDTO;
import cn.qinghe.mall.module.promotion.api.coupon.dto.CouponRespDTO;
import cn.qinghe.mall.module.promotion.core.coupon.CouponPriceCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原AddressApi/CouponApi的固定来源本人适配；不查询对方数据库、不转发外部userId。 */
@Service
public class CheckoutReferencesClient {
    private final CheckoutMemberApi members;private final CheckoutCouponApi coupons;private final TradeMemberAccess access;private final SecurityProperties security;
    private final Semaphore calls=new Semaphore(16);
    public CheckoutReferencesClient(CheckoutMemberApi members,CheckoutCouponApi coupons,TradeMemberAccess access,SecurityProperties security,
            @Value("${qinghe.trade.member-base-url:http://127.0.0.1:8084}") String memberOrigin,
            @Value("${qinghe.trade.promotion-base-url:http://127.0.0.1:8085}") String couponOrigin) {
        origin(memberOrigin);origin(couponOrigin);this.members=members;this.coupons=coupons;this.access=access;this.security=security;
    }
    public MemberAddressRespDTO address(Long id) {
        LoginUser user=access.actor();MemberAddressRespDTO row=call(()->members.address(bearer(),id));
        if(row==null || !Objects.equals(id,row.getId()) || !Objects.equals(user.getId(),row.getUserId()) || !Objects.equals(user.getTenantId(),row.getTenantId())
                || row.getAuthVersion()==null || row.getAuthVersion()<0 || row.getName()==null || row.getName().trim().isEmpty() || row.getName().length()>50
                || row.getMobile()==null || !row.getMobile().matches("1[3-9][0-9]{9}") || row.getDetailAddress()==null || row.getDetailAddress().trim().isEmpty() || row.getDetailAddress().length()>255
                || !AreaUtils.isChinaRegion(row.getAreaId()) || AreaUtils.getArea(row.getAreaId()).getType()<3 || !AreaUtils.getArea(row.getAreaId()).getChildren().isEmpty())throw exception0(503,"收货地址快照不完整");
        return row;
    }
    public CouponRespDTO coupon(Long id) {
        LoginUser user=access.actor();CouponRespDTO row=call(()->coupons.coupon(bearer(),id));
        if(row==null || !Objects.equals(id,row.getId()) || !Objects.equals(user.getId(),row.getUserId()) || !Objects.equals(user.getTenantId(),row.getTenantId()))throw exception0(503,"优惠券归属结果不完整");
        CouponPriceCalculator.validateSnapshot(row);return row;
    }
    /** 沿用固定promotion来源和原会员Bearer；响应不含地址、初始化token或可变池余额。 */
    public cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO seckill(Long activity,Integer generation,Long sku,Integer quantity) {
        if (activity==null || activity<1 || activity>9007199254740991L || generation==null || generation<1
                || sku==null || sku<1 || sku>9007199254740991L || quantity==null || quantity<1 || quantity>9999)
            throw exception0(400,"秒杀报价参数不正确");
        LoginUser user=access.actor();
        cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO row=call(()->coupons.seckill(bearer(),activity,generation,sku,quantity),true);
        if (row==null || !Objects.equals(user.getTenantId(),row.getTenantId()) || !Objects.equals(user.getId(),row.getMemberId())
                || !Objects.equals(activity,row.getActivityId()) || !Objects.equals(generation,row.getGeneration()) || !Objects.equals(sku,row.getSkuId())
                || row.getActivityVersion()==null || row.getActivityVersion()<0 || row.getActivityVersion()>9007199254740991L
                || !Long.valueOf(1).equals(row.getRedisEpoch()) || row.getSpuId()==null || row.getSpuId()<1 || row.getSpuId()>9007199254740991L
                || row.getSpuVersion()==null || row.getSpuVersion()<0 || row.getSpuVersion()>9007199254740991L
                || row.getSkuVersion()==null || row.getSkuVersion()<0 || row.getSkuVersion()>9007199254740991L
                || row.getPriceCent()==null || row.getPriceCent()<1 || row.getPriceCent()>2000000000
                || row.getSingleLimit()==null || row.getSingleLimit()<1 || row.getSingleLimit()>9999 || row.getMemberLimit()==null
                || row.getMemberLimit()<row.getSingleLimit() || quantity>row.getSingleLimit()
                || row.getStartTime()==null || row.getEndTime()==null || row.getServerTime()==null
                || row.getServerTime().isBefore(row.getStartTime()) || !row.getServerTime().isBefore(row.getEndTime()))
            throw exception0(503,"秒杀活动报价结果不完整");
        access.actor();return row;
    }
    private <T>T call(Supplier<CommonResult<T>> action) {
        return call(action,false);
    }
    private <T>T call(Supplier<CommonResult<T>> action,boolean seckill) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("结算引用RPC不能在trade事务内执行");
        if(!calls.tryAcquire())throw exception0(503,"结算资料服务繁忙");
        try {CommonResult<T> result=action.get();if(result==null || result.getCode()==null)throw exception0(503,"结算资料结果不完整");
            if (seckill && Arrays.asList(400,409).contains(result.getCode())) throw exception0(result.getCode(),"活动或购买数量已不可报价");
            if(Integer.valueOf(404).equals(result.getCode()))throw exception0(404,"所选地址或优惠券不存在");
            if(!result.isSuccess())throw exception0(503,"结算资料暂不可用");return result.getData();
        }finally{calls.release();}
    }
    private String bearer() {
        if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes))throw exception0(503,"结算缺少当前请求");
        String token=SecurityFrameworkUtils.obtainAuthorization(((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest(),security.getTokenHeader());
        if(token==null || token.length()>128)throw exception0(401,"请重新登录");return "Bearer "+token;
    }
    private void origin(String value) {
        URI uri=URI.create(value);
        if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && Arrays.asList("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost())))throw new IllegalArgumentException("结算资料来源必须是固定可信origin");
    }
}
