package cn.qinghe.mall.module.promotion.service.coupon;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.api.coupon.dto.*;
import cn.qinghe.mall.module.promotion.core.coupon.CouponPriceCalculator;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.*;
import cn.qinghe.mall.module.promotion.dal.mysql.coupon.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原useCoupon/returnUsedCoupon状态更新职责，补B05/B06订单占用与原订单永久结果。
 * 创建/补偿/未付关闭及原全额退款退券由专用trade服务身份HTTP调用。调用者必须是已验证的trade订单流程，
 * user/order/商品金额来自权威准备记录；释放须确认无付款通道，退券须确认未付关闭或整单全额退款。
 * 不接受浏览器金额/订单状态/退券理由作为权威，不因付款截止时间推测已关闭，不在本地锁内RPC。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=10,rollbackFor=Exception.class)
public class CouponOrderService {
    private final CouponMapper coupons;
    private final CouponOrderMapper orders;

    @Transactional(propagation=Propagation.SUPPORTS,readOnly=true)
    public CouponQuoteDTO quote(Long trustedUserId,Long couponId,List<CouponOrderLineDTO> authoritativeLines,boolean normalOrder) {
        identity(trustedUserId,couponId);positive(couponId);CouponDO coupon=coupons.selectByIdAndUserId(couponId,trustedUserId);
        if(coupon==null)throw exception0(404,"优惠券不存在");
        validCoupon(coupon);return CouponPriceCalculator.calculate(snapshot(coupon),authoritativeLines,normalOrder,now());
    }

    public CouponOrderResultDTO reserve(Long trustedUserId,Long orderId,Long couponId,
            List<CouponOrderLineDTO> authoritativeLines,boolean normalOrder) {
        return reserveInternal(trustedUserId,orderId,couponId,authoritativeLines,normalOrder,null);
    }

    /** HTTP 服务入口必须带原报价；当前券版本或分摊变化时，在占用之前拒绝。 */
    public CouponOrderResultDTO reserve(Long trustedUserId,Long orderId,Long couponId,
            List<CouponOrderLineDTO> authoritativeLines,boolean normalOrder,CouponQuoteDTO expectedQuote) {
        if(expectedQuote==null)throw exception0(400,"缺少原订单优惠分摊");
        return reserveInternal(trustedUserId,orderId,couponId,authoritativeLines,normalOrder,expectedQuote);
    }

    private CouponOrderResultDTO reserveInternal(Long trustedUserId,Long orderId,Long couponId,
            List<CouponOrderLineDTO> authoritativeLines,boolean normalOrder,CouponQuoteDTO expectedQuote) {
        Long tenant=identity(trustedUserId,couponId);positive(couponId);positive(orderId);
        if(!normalOrder)throw exception0(400,"该订单类型不能使用优惠券");
        List<CouponOrderLineDTO> lines=CouponPriceCalculator.orderedLines(authoritativeLines);
        String digest=digest(tenant,trustedUserId,orderId,couponId,lines);
        openGate(gate(tenant,trustedUserId,orderId,couponId));
        CouponOrderDO previous=orders.find(tenant,orderId);
        if(previous!=null){same(previous,trustedUserId,couponId,digest);return expectedResult(previous,expectedQuote);}
        CouponDO coupon=lock(trustedUserId,couponId);
        CouponQuoteDTO quote=CouponPriceCalculator.calculate(snapshot(coupon),lines,true,now());
        if(expectedQuote!=null && !expectedQuote.equals(quote))throw exception0(409,"原订单优惠券版本或分摊已变化，请重新确认");
        LocalDateTime instant=now();
        changed(coupons.holdForOrder(tenant,trustedUserId,couponId,orderId,coupon.getAuthVersion(),instant));
        CouponOrderDO row=new CouponOrderDO();row.setTenantId(tenant);row.setUserId(trustedUserId);row.setOrderId(orderId);row.setCouponId(couponId);
        row.setRequestDigest(digest);row.setState(1);row.setQuoteJson(JsonUtils.toJsonString(quote));row.setCouponVersion(coupon.getAuthVersion());row.setAuthVersion(0L);
        // 同订单先锁gate再锁券；uk(tenant,order)继续约束一单一券。
        if(orders.insert(row)!=1 || row.getId()==null)throw new IllegalStateException("订单券绑定保存失败");
        audit(coupon,orderId,"HOLD",4,null,"订单准备占用");return result(row);
    }

    private CouponOrderResultDTO expectedResult(CouponOrderDO row,CouponQuoteDTO expectedQuote) {
        CouponOrderResultDTO result=result(row);
        if(expectedQuote!=null && !expectedQuote.equals(result.getQuote()))throw exception0(409,"原订单优惠分摊不一致");
        return result;
    }

    /** 在最终创建订单前核销，仍重验结束不含；不等待支付成功后再检查券是否到期。 */
    public CouponOrderResultDTO use(Long trustedUserId,Long orderId,Long couponId) {
        Long tenant=identity(trustedUserId,couponId);positive(couponId);positive(orderId);
        openGate(gate(tenant,trustedUserId,orderId,couponId));
        CouponOrderDO row=required(tenant,trustedUserId,orderId,couponId);
        if(row.getState()==2)return result(row);
        if(row.getState()!=1)throw exception0(409,"原订单已释放或退券，不能再次核销");
        CouponDO coupon=lock(trustedUserId,couponId);
        changed(coupons.useForOrder(tenant,trustedUserId,couponId,orderId,coupon.getAuthVersion(),now()));
        transition(row,2,null);audit(coupon,orderId,"USE",2,null,"订单创建核销");return result(row);
    }

    /** 仅尚在准备/占用阶段；调用者须证明不会继续创建付款入口。 */
    public CouponOrderResultDTO releasePrepared(Long trustedUserId,Long orderId,Long couponId,String verifiedReason) {
        if(!Arrays.asList("CREATE_FAILED","UNPAID_CLOSED").contains(verifiedReason))throw exception0(400,"占用释放原因不正确");
        return restore(trustedUserId,orderId,couponId,1,3,verifiedReason,"RELEASE");
    }

    /** 部分退款不能调用；保留原绝对期限、原券身份和风控作废事实，不新发券或返还领取额度。 */
    public CouponOrderResultDTO returnUsed(Long trustedUserId,Long orderId,Long couponId,String verifiedReason) {
        if(!Arrays.asList("UNPAID_CLOSED","FULL_REFUND").contains(verifiedReason))throw exception0(400,"退券原因不正确");
        return restore(trustedUserId,orderId,couponId,2,4,verifiedReason,"RETURN");
    }

    /**
     * 仅创建阶段不可逆失败、尚无正式订单/付款入口时调用。先关原订单，再释放原占用或退回原核销。
     * gate与券变更在同一事务；取消先到也落关闭行，使随后到达的reserve/use永久失败。
     */
    public CouponOrderCloseResultDTO closeCreation(Long userId,Long orderId,Long couponId) {
        Long tenant=identity(userId,couponId);positive(orderId);positive(couponId);
        CouponOrderGateDO gate=gate(tenant,userId,orderId,couponId);
        closeGate(gate,"CREATE_FAILED");
        CouponOrderDO row=orders.lock(tenant,orderId);
        if(row!=null) {
            same(row,userId,couponId,null);
            if(row.getState()==1 || row.getState()==2) {
                restoreLocked(row,lock(userId,couponId),row.getState()==1?3:4,"CREATE_FAILED",row.getState()==1?"RELEASE":"RETURN");
            } else if(!Arrays.asList(3,4).contains(row.getState()) || !"CREATE_FAILED".equals(row.getReturnReason())) {
                throw exception0(409,"原订单已经按其他原因退券，不能作为创建失败补偿");
            }
        }
        CouponOrderCloseResultDTO result=new CouponOrderCloseResultDTO();
        result.setOrderId(orderId);result.setCouponId(couponId);result.setClosed(true);
        result.setState(row==null?null:row.getState());result.setReason("CREATE_FAILED");return result;
    }

    /** 后续风控/后台受保护调用者须先验证真实员工及作废权限，本领域方法不提供公网入口。 */
    public void invalidate(Long trustedUserId,Long couponId,Long trustedActorId,String reason) {
        identity(trustedUserId,couponId);positive(couponId);positive(trustedActorId);
        if(reason==null || reason.trim().isEmpty() || reason.length()>200 || reason.chars().anyMatch(Character::isISOControl))throw exception0(400,"作废原因不正确");
        CouponDO coupon=lock(trustedUserId,couponId);
        if(coupon.getStatus()==5)return;
        changed(coupons.invalidate(coupon.getTenantId(),trustedUserId,couponId,coupon.getAuthVersion(),trustedActorId.toString(),now()));
        // 保留原订单指针供后续履约/退回收敛；退回更新也必须保持作废，不复活。
        audit(coupon,null,"INVALIDATE",5,trustedActorId,reason.trim());
    }

    @Transactional(propagation=Propagation.SUPPORTS,readOnly=true)
    public CouponOrderResultDTO find(Long trustedUserId,Long orderId) {
        Long tenant=identity(trustedUserId,null);positive(orderId);
        CouponOrderDO row=orders.find(tenant,orderId);
        if(row==null || !Objects.equals(trustedUserId,row.getUserId()))return null;
        return result(row);
    }

    private CouponOrderResultDTO restore(Long userId,Long orderId,Long couponId,int expected,int terminal,String reason,String action) {
        Long tenant=identity(userId,couponId);positive(couponId);positive(orderId);
        CouponOrderGateDO gate=gate(tenant,userId,orderId,couponId);
        CouponOrderDO row=required(tenant,userId,orderId,couponId);
        closeGate(gate,reason);
        if(row.getState()==terminal) {
            if(!Objects.equals(reason,row.getReturnReason()))throw exception0(409,"原订单退券原因与请求不一致");
            return result(row);
        }
        if(row.getState()!=expected)throw exception0(409,"原订单券阶段不支持此操作");
        return restoreLocked(row,lock(userId,couponId),terminal,reason,action);
    }

    private CouponOrderResultDTO restoreLocked(CouponOrderDO row,CouponDO coupon,int terminal,String reason,String action) {
        LocalDateTime instant=now();
        changed(coupons.restoreForOrder(row.getTenantId(),row.getUserId(),row.getCouponId(),row.getOrderId(),row.getState(),coupon.getAuthVersion(),instant));
        int restored=coupon.getInvalidatedTime()!=null || coupon.getStatus()==5?5:!instant.isBefore(coupon.getValidEndTime())?3:1;
        transition(row,terminal,reason);audit(coupon,row.getOrderId(),action,restored,null,reason);return result(row);
    }

    /** 所有订单写入统一先锁gate，再锁订单绑定/券；作废仅锁券，不反向获取gate。 */
    private CouponOrderGateDO gate(Long tenant,Long user,Long order,Long coupon) {
        int inserted=orders.ensureGate(tenant,order,user,coupon);
        if(inserted!=0 && inserted!=1)throw new IllegalStateException("订单券关闭记录登记异常");
        CouponOrderGateDO row=orders.lockGate(tenant,order);
        if(row==null || row.getClosed()==null)throw exception0(503,"订单券关闭记录不完整");
        if(!Objects.equals(user,row.getUserId()) || !Objects.equals(coupon,row.getCouponId()))throw exception0(409,"原订单不能更换会员或优惠券");
        return row;
    }
    private void openGate(CouponOrderGateDO row) {
        if(Boolean.TRUE.equals(row.getClosed()))throw exception0(409,"原订单已关闭，不能再次占券或核销");
    }
    private void closeGate(CouponOrderGateDO row,String reason) {
        if(Boolean.TRUE.equals(row.getClosed())) {
            if(!Objects.equals(reason,row.getReason()))throw exception0(409,"原订单关闭原因不一致");
            return;
        }
        changed(orders.closeGate(row.getTenantId(),row.getOrderId(),reason));
        row.setClosed(true);row.setReason(reason);
    }
    private CouponDO lock(Long userId,Long couponId) {
        CouponDO row=coupons.selectOwnedForUpdate(couponId,userId);
        if(row==null)throw exception0(404,"优惠券不存在");validCoupon(row);return row;
    }
    private CouponOrderDO required(Long tenant,Long user,Long order,Long coupon) {
        CouponOrderDO row=orders.lock(tenant,order);
        if(row==null)throw exception0(404,"原订单券绑定不存在");same(row,user,coupon,null);return row;
    }
    private void same(CouponOrderDO row,Long user,Long coupon,String digest) {
        if(!Objects.equals(user,row.getUserId()) || !Objects.equals(coupon,row.getCouponId())
                || digest!=null && !digest.equals(row.getRequestDigest()))throw exception0(409,"原订单不能更换会员、券或核价内容");
    }
    private void transition(CouponOrderDO row,int to,String reason) {
        changed(orders.transition(row.getTenantId(),row.getOrderId(),row.getUserId(),row.getCouponId(),row.getState(),to,reason,row.getAuthVersion()));
        row.setState(to);row.setReturnReason(reason);row.setAuthVersion(Math.addExact(row.getAuthVersion(),1L));
    }
    private void audit(CouponDO coupon,Long orderId,String action,int to,Long actor,String reason) {
        CouponAuditDO row=new CouponAuditDO();row.setTenantId(coupon.getTenantId());row.setCouponId(coupon.getId());row.setUserId(coupon.getUserId());
        row.setOrderId(orderId);row.setAction(action);row.setFromStatus(coupon.getStatus());row.setToStatus(to);row.setFromVersion(coupon.getAuthVersion());
        row.setToVersion(Math.addExact(coupon.getAuthVersion(),1L));row.setActorId(actor);row.setReason(reason);
        if(orders.audit(row)!=1)throw new IllegalStateException("优惠券变更审计保存失败");
    }
    private CouponOrderResultDTO result(CouponOrderDO row) {
        CouponQuoteDTO quote=JsonUtils.parseObject(row.getQuoteJson(),CouponQuoteDTO.class);
        if(quote==null || !Objects.equals(row.getCouponId(),quote.getCouponId()) || !Objects.equals(row.getCouponVersion(),quote.getCouponVersion())
                || quote.getDiscountPrice()==null || quote.getDiscountPrice()<=0 || quote.getLines()==null || quote.getLines().isEmpty()
                || row.getState()==null || !Arrays.asList(1,2,3,4).contains(row.getState()))throw exception0(503,"订单券历史结果不完整");
        CouponOrderResultDTO result=new CouponOrderResultDTO();result.setOrderId(row.getOrderId());result.setCouponId(row.getCouponId());
        result.setState(row.getState());result.setReturnReason(row.getReturnReason());result.setQuote(quote);return result;
    }
    private void validCoupon(CouponDO coupon) {
        CouponPriceCalculator.validateSnapshot(snapshot(coupon));
        boolean held=coupon.getLockedOrderId()!=null,used=coupon.getUseOrderId()!=null;
        int status=coupon.getStatus();
        if(held!=(coupon.getLockedTime()!=null) || used!=(coupon.getUseTime()!=null) || held && used
                || (status==1 || status==3) && (held || used) || status==4 && !held || status==2 && !used
                || (coupon.getInvalidatedTime()!=null)!=(status==5))throw exception0(503,"优惠券归属状态异常");
    }
    private CouponRespDTO snapshot(CouponDO row){return BeanUtils.toBean(row,CouponRespDTO.class);}
    private Long identity(Long user,Long coupon) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        if(tenant<=0 || TenantContextHolder.isIgnore())throw exception0(403,"订单券需要可信租户上下文");
        positive(user);if(coupon!=null)positive(coupon);return tenant;
    }
    private void positive(Long x){if(x==null || x<=0)throw exception0(400,"订单券身份不正确");}
    private void changed(int count){if(count!=1)throw exception0(409,"优惠券状态、期限或版本已变化，请查询原订单结果");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
    private String digest(Long tenant,Long user,Long order,Long coupon,List<CouponOrderLineDTO> lines) {
        StringBuilder key=new StringBuilder("coupon-order-v1:").append(tenant).append(':').append(user).append(':').append(order).append(':').append(coupon);
        for(CouponOrderLineDTO line:lines)key.append('|').append(line.getLineId()).append(':').append(line.getSpuId()).append(':').append(line.getSkuId())
                .append(':').append(line.getCategoryId()).append(':').append(line.getQuantity()).append(':').append(line.getUnitPrice());
        return DigestUtil.sha256Hex(key.toString());
    }
}
