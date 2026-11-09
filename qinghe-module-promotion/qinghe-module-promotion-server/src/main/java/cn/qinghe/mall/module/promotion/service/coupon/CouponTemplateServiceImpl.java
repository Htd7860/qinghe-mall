package cn.qinghe.mall.module.promotion.service.coupon;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.qinghe.mall.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.qinghe.mall.module.promotion.framework.product.CouponProductScopeClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原create/update/status/delete/read及validateProductScope流程；外部校验在本地事务前完成。
 * 与领取共用模板父锁，CAS配置/审计同事务；不重写用户现有框架。
 * @author 芋道源码
 */
@Service
public class CouponTemplateServiceImpl implements CouponTemplateService {
    private final CouponTemplateMapper mapper;
    private final CouponRules rules;
    private final CouponProductScopeClient products;
    private final SecurityFrameworkService security;
    private final RemoteSystemSecurityClient remote;
    private final TransactionTemplate transaction;
    public CouponTemplateServiceImpl(CouponTemplateMapper mapper,CouponRules rules,CouponProductScopeClient products,
            SecurityFrameworkService security,RemoteSystemSecurityClient remote,PlatformTransactionManager manager) {
        this.mapper=mapper;this.rules=rules;this.products=products;this.security=security;this.remote=remote;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);transaction.setTimeout(10);
    }
    @Override
    public Long createCouponTemplate(CouponTemplateBaseVO body) {
        LoginUser actor=writeActor("create");CouponTemplateDO row=rules.normalize(body);validateNew(row);products.validate(row.getProductScope(),row.getProductScopeValues());
        return write(() -> {
            fresh(actor);validateNew(row);row.setTenantId(actor.getTenantId());row.setStatus(0);row.setTakeCount(0);row.setUseCount(0);row.setAuthVersion(0L);
            row.setCreator(actor.getId().toString());row.setUpdater(row.getCreator());row.setDeleted(false);
            row.setCreateTime(now());row.setUpdateTime(row.getCreateTime());
            if(mapper.insert(row)!=1 || row.getId()==null)throw new IllegalStateException("优惠券模板创建失败");
            audit(actor,row.getId(),"CREATE",-1L,0L,row);return row.getId();
        });
    }
    @Override
    public void updateCouponTemplate(Long id,Long expected,CouponTemplateBaseVO body) {
        LoginUser actor=writeActor("update");CouponTemplateDO next=rules.normalize(body);validateNew(next);products.validate(next.getProductScope(),next.getProductScopeValues());
        write(() -> {
            CouponTemplateDO current=locked(id,expected);fresh(actor);validateNew(next);
            if(next.getTotalCount()!=-1 && next.getTotalCount()<current.getTakeCount())throw exception0(400,"发行总量不能小于已成功发放数量");
            if(mapper.updateRules(actor.getTenantId(),id,expected,next,JsonUtils.toJsonString(next.getProductScopeValues()),actor.getId().toString())!=1)throw changed();
            audit(actor,id,"UPDATE",expected,expected+1,next);return null;
        });
    }
    @Override
    public void updateCouponTemplateStatus(Long id,Long expected,Integer status) {
        LoginUser actor=writeActor("update");if(status==null || status<0 || status>1)throw exception0(400,"优惠券状态不正确");
        write(() -> {
            CouponTemplateDO current=locked(id,expected);fresh(actor);
            if(Objects.equals(current.getStatus(),status))return null;
            if(mapper.updateStatus(actor.getTenantId(),id,expected,status,actor.getId().toString())!=1)throw changed();
            audit(actor,id,"STATUS",expected,expected+1,Collections.singletonMap("status",status));return null;
        });
    }
    @Override
    public void deleteCouponTemplate(Long id,Long expected) {
        LoginUser actor=writeActor("delete");write(() -> {
            CouponTemplateDO current=locked(id,expected);fresh(actor);
            if(current.getStatus()!=1 || current.getTakeCount()!=0)throw exception0(409,"只能删除已暂停且未发放的模板");
            if(mapper.retire(actor.getTenantId(),id,expected,actor.getId().toString())!=1)throw changed();
            audit(actor,id,"DELETE",expected,expected+1,Collections.emptyMap());return null;
        });
    }
    @Override
    public CouponTemplateDO getCouponTemplate(Long id) {
        actor("query");if(id==null || id<=0)throw exception0(400,"模板编号不正确");
        CouponTemplateDO row=mapper.selectById(id);if(row==null)throw exception0(404,"优惠券模板不存在");return row;
    }
    @Override
    public PageResult<CouponTemplateDO> getCouponTemplatePage(CouponTemplatePageReqVO body) {
        actor("query");if(body==null || body.getPageNo()==null || body.getPageNo()<1 || body.getPageSize()==null || body.getPageSize()<1 || body.getPageSize()>100)throw exception0(400,"分页参数不正确");
        return mapper.selectPage(body);
    }
    private LoginUser actor(String permission) {
        LoginUser user=SecurityFrameworkUtils.getLoginUser();
        if(user==null)throw exception0(401,"请先登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(user.getUserType()) || user.getId()==null || user.getId()<=0 || user.getTenantId()==null
                || user.getTenantId()<=0 || TenantContextHolder.isIgnore() || !Objects.equals(user.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"优惠券管理仅限当前商家员工");
        if(!security.hasPermission("promotion:coupon-template:"+permission))throw exception0(403,"没有该优惠券操作权限");return user;
    }
    private LoginUser writeActor(String permission) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("模板写入口不能嵌套外层事务");
        LoginUser user=actor(permission);remote.checkTenantOperation();return user;
    }
    private void fresh(LoginUser user) {
        if(!Objects.equals(user.getTenantId(),TenantContextHolder.getTenantId()) || user.getExpiresTime()==null || !user.getExpiresTime().isAfter(now()))throw exception0(401,"优惠券操作身份已到期");
    }
    private CouponTemplateDO locked(Long id,Long expected) {
        if(id==null || id<=0 || expected==null || expected<0)throw exception0(400,"请提供模板编号和当前版本");
        CouponTemplateDO row=mapper.selectForUpdate(id);if(row==null)throw exception0(404,"优惠券模板不存在");rules.validateStored(row);
        if(!Objects.equals(row.getAuthVersion(),expected) || expected==Long.MAX_VALUE)throw changed();return row;
    }
    private void validateNew(CouponTemplateDO row) { if(row.getValidityType()==1 && !row.getValidEndTime().isAfter(now()))throw exception0(400,"模板结束时间必须晚于当前时间"); }
    private <T> T write(Supplier<T> action) {
        try{return transaction.execute(status -> action.get());}
        catch(org.springframework.dao.DataAccessException failure){throw exception0(503,"优惠券模板保存结果未确认，请重新读取");}
    }
    private void audit(LoginUser actor,Long id,String action,Long from,Long to,Object details) {
        if(mapper.audit(actor.getTenantId(),actor.getId(),id,action,from,to,JsonUtils.toJsonString(details))!=1)throw new IllegalStateException("优惠券配置审计保存失败");
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
    private cn.qinghe.mall.framework.common.exception.ServiceException changed(){return exception0(409,"优惠券模板已变化，请重新读取");}
}
