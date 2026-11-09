package cn.qinghe.mall.module.promotion.service.coupon;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.promotion.controller.app.coupon.vo.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.*;
import cn.qinghe.mall.module.promotion.dal.mysql.coupon.*;
import cn.qinghe.mall.module.promotion.framework.member.CouponMemberAccess;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原App分页/本人券/领取委托；只增加实际入口准入与稳定键恢复，不在本地锁内RPC。
 * @author 芋道源码
 */
@Service
public class AppCouponService {
    private final CouponMemberAccess access;
    private final CouponTemplateMapper templates;
    private final CouponMapper coupons;
    private final CouponService grants;
    private final CouponRules rules;
    private final TransactionTemplate write;
    public AppCouponService(CouponMemberAccess access,CouponTemplateMapper templates,CouponMapper coupons,
            CouponService grants,CouponRules rules,PlatformTransactionManager manager) {
        this.access=access;this.templates=templates;this.coupons=coupons;this.grants=grants;this.rules=rules;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public PageResult<AppCouponTemplateRespVO> templatePage(HttpServletRequest request,String store,AppCouponPageReqVO page) {
        MemberStorefrontRespDTO entry=access.check(request,store);LoginUser actor=access.actor();
        PageResult<CouponTemplateDO> rows=templates.selectAppPage(page,now());
        Map<Long,Long> counts=new HashMap<>();
        if(!rows.getList().isEmpty())for(CouponMapper.TakeCount row:coupons.selectTakeCounts(actor.getTenantId(),actor.getId(),
                rows.getList().stream().map(CouponTemplateDO::getId).collect(Collectors.toList())))counts.put(row.getTemplateId(),row.getTakenCount());
        List<AppCouponTemplateRespVO> result=new ArrayList<>();
        for(CouponTemplateDO row:rows.getList()) {
            rules.validateStored(row);browserId(row.getId());
            AppCouponTemplateRespVO vo=BeanUtils.toBean(row,AppCouponTemplateRespVO.class);
            long taken=counts.getOrDefault(row.getId(),0L);
            vo.setTakenCount(taken);vo.setRemainingCount(row.getTotalCount()==-1?-1:Math.max(0,row.getTotalCount()-row.getTakeCount()));
            String reason=!Boolean.TRUE.equals(entry.getCanPurchase())?"店铺暂不支持新领取":
                    row.getTotalCount()!=-1 && row.getTakeCount()>=row.getTotalCount()?"已领完":
                    row.getTakeLimitCount()!=-1 && taken>=row.getTakeLimitCount()?"已达个人领取上限":"";
            vo.setCanTake(reason.isEmpty());vo.setUnavailableReason(reason);result.add(vo);
        }
        access.actor();return new PageResult<>(rows.getTotal(),result);
    }
    public PageResult<AppCouponRespVO> ownedPage(HttpServletRequest request,String store,AppCouponPageReqVO page) {
        access.check(request,store);LoginUser actor=access.actor();LocalDateTime instant=now();
        PageResult<CouponDO> rows=coupons.selectOwnedPage(actor.getId(),page.getStatus(),page,instant);
        List<AppCouponRespVO> result=rows.getList().stream().map(row -> view(row,instant)).collect(Collectors.toList());
        access.actor();return new PageResult<>(rows.getTotal(),result);
    }
    public AppCouponRespVO get(HttpServletRequest request,String store,Long id) {
        access.check(request,store);browserId(id);
        CouponDO row=coupons.selectByIdAndUserId(id,access.actor().getId());
        if(row==null)throw exception0(404,"优惠券不存在");
        return view(row,now());
    }
    /** 内部结算只读：原Bearer独立认证，当前MEMBER本人券，不接受目标user/tenant。 */
    public cn.qinghe.mall.module.promotion.api.coupon.dto.CouponRespDTO getForCheckout(Long id) {
        LoginUser actor=access.actor();browserId(id);
        CouponDO row=coupons.selectByIdAndUserId(id,actor.getId());
        return row==null?null:BeanUtils.toBean(row,cn.qinghe.mall.module.promotion.api.coupon.dto.CouponRespDTO.class);
    }
    public AppCouponGrantRespVO result(HttpServletRequest request,String store,AppCouponTakeReqVO body) {
        access.check(request,store);LoginUser actor=access.actor();
        return receipt(actor,body);
    }
    public AppCouponGrantRespVO take(HttpServletRequest request,String store,AppCouponTakeReqVO body) {
        MemberStorefrontRespDTO entry=access.check(request,store);LoginUser actor=access.actor();
        // 成功回执先于当前经营/模板资格，暂停后仍能恢复原领取结果。
        AppCouponGrantRespVO previous=receipt(actor,body);if(previous.getFound())return previous;
        if(!Boolean.TRUE.equals(entry.getCanPurchase()))throw exception0(403,"店铺暂不支持新领取");
        final Long id;
        try {
            id=write.execute(status -> {
                LoginUser current=access.actor();
                if(!Objects.equals(actor.getId(),current.getId()) || !Objects.equals(actor.getTenantId(),current.getTenantId()))throw exception0(403,"领取身份已变化");
                Long couponId=grants.takeCoupon(body.getTemplateId(),current.getId(),1,key(body));
                browserId(couponId);return couponId;
            });
        } catch(DuplicateKeyException conflict) {
            // 唯一键竞争必须等本次事务回滚后再读原成功回执。
            AppCouponGrantRespVO original=receipt(actor,body);if(original.getFound())return original;
            throw exception0(409,"领取请求冲突，请使用原请求号查询结果");
        }
        if(id==null)throw new IllegalStateException("领取事务未返回结果");
        AppCouponGrantRespVO result=new AppCouponGrantRespVO();result.setFound(true);result.setCouponId(id);result.setTemplateId(body.getTemplateId());return result;
    }
    private AppCouponGrantRespVO receipt(LoginUser actor,AppCouponTakeReqVO body) {
        CouponDO row=coupons.selectGrantReceipt(actor.getTenantId(),actor.getId(),key(body));
        AppCouponGrantRespVO vo=new AppCouponGrantRespVO();vo.setFound(row!=null);vo.setTemplateId(body.getTemplateId());
        if(row!=null) {
            if(!Objects.equals(row.getTemplateId(),body.getTemplateId()) || !Integer.valueOf(1).equals(row.getTakeType()))throw exception0(409,"原请求号不能用于另一项领券操作");
            browserId(row.getId());vo.setCouponId(row.getId());
        }
        return vo;
    }
    private String key(AppCouponTakeReqVO body){return "claim:"+body.getRequestId();}
    private AppCouponRespVO view(CouponDO row,LocalDateTime instant) {
        browserId(row.getId());browserId(row.getTemplateId());
        AppCouponRespVO vo=BeanUtils.toBean(row,AppCouponRespVO.class);
        if(Integer.valueOf(1).equals(row.getStatus()) && !row.getValidEndTime().isAfter(instant))vo.setStatus(3);
        return vo;
    }
    private void browserId(Long id) {if(id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(503,"优惠券编号无法投影");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
}
