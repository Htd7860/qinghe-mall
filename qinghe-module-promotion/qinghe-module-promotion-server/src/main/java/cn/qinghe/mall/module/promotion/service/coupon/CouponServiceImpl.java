package cn.qinghe.mall.module.promotion.service.coupon;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.convert.coupon.CouponConvert;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.*;
import cn.qinghe.mall.module.promotion.dal.mysql.coupon.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原removeTakeLimitUser→validateCanTake→快照insert→updateTakeCount单事务流程。
 * 模板父锁串行化同模板发放/编辑，数据库条件额度与成功键唯一约束兜底。
 * @author 芋道源码
 */
@Service @RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {
    private final CouponTemplateMapper templates;
    private final CouponMapper coupons;
    private final CouponRules rules;
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public Long takeCoupon(Long templateId,Long trustedUserId,Integer takeType,String key) {
        MybatisUtils.requireWriteTransaction();Long tenant=target(trustedUserId,key);
        if(!Integer.valueOf(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED)
                .equals(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalStateException("发放内核要求调用者提供READ_COMMITTED本地写事务");
        }
        if(templateId==null || templateId<=0 || !(Integer.valueOf(1).equals(takeType) || Integer.valueOf(2).equals(takeType)))throw exception0(400,"发放目标不正确");
        CouponDO previous=coupons.selectGrantReceipt(tenant,trustedUserId,key);
        if(previous!=null)return replay(previous,templateId,takeType);
        CouponTemplateDO template=templates.selectForUpdate(templateId);
        if(template==null)throw exception0(404,"优惠券模板不存在");
        previous=coupons.selectGrantReceipt(tenant,trustedUserId,key);
        if(previous!=null)return replay(previous,templateId,takeType);
        rules.validateStored(template);
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
        if(template.getStatus()!=0 || takeType==1 && template.getTakeType()!=1
                || template.getValidityType()==1 && !template.getValidEndTime().isAfter(now))throw exception0(409,"该优惠券已暂停、到期或不支持直接领取");
        Long taken=coupons.countSuccessfulGrants(tenant,templateId,trustedUserId);
        if(template.getTakeLimitCount()!=-1 && taken>=template.getTakeLimitCount())throw exception0(409,"已达到该优惠券的领取上限");
        if(templates.incrementTakeCount(tenant,templateId,1)!=1)throw exception0(409,"该优惠券已领完");
        CouponDO coupon=CouponConvert.convert(template,trustedUserId,takeType,key,now);
        if(coupons.insert(coupon)!=1 || coupon.getId()==null || coupon.getId()<=0)throw new IllegalStateException("会员券保存失败");
        if(!coupon.getValidEndTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))throw exception0(409,"领取处理期间优惠券已到期");
        return coupon.getId();
    }
    @Override
    public Long findGrantResult(Long userId,String key) {
        CouponDO receipt=coupons.selectGrantReceipt(target(userId,key),userId,key);return receipt==null?null:receipt.getId();
    }
    private Long target(Long userId,String key) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        if(tenant<=0 || TenantContextHolder.isIgnore() || userId==null || userId<=0 || key==null || !key.matches("[A-Za-z0-9:_-]{8,128}"))throw exception0(400,"可信发放身份或业务键不正确");
        return tenant;
    }
    private Long replay(CouponDO row,Long templateId,Integer type) {
        if(!Objects.equals(row.getTemplateId(),templateId) || !Objects.equals(row.getTakeType(),type))throw exception0(409,"相同发放键不能用于另一项领券操作");
        return row.getId();
    }
}
