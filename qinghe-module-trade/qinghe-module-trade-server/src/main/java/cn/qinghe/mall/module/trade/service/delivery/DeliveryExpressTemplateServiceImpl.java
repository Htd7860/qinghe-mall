package cn.qinghe.mall.module.trade.service.delivery;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate.*;
import cn.qinghe.mall.module.trade.service.delivery.bo.DeliveryTemplateSnapshotBO;
import org.springframework.dao.DuplicateKeyException;
import javax.validation.Validator;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import cn.qinghe.mall.framework.common.util.area.Area;
import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.*;
import cn.qinghe.mall.module.trade.dal.mysql.delivery.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取yudao validateDeliveryExpressTemplate及模板内主/从表读取；按B05补合法计量/地域无歧义检查。
 * @author jason
 */
@Service
public class DeliveryExpressTemplateServiceImpl implements DeliveryExpressTemplateService {
    private final DeliveryExpressTemplateMapper templates;
    private final DeliveryExpressTemplateChargeMapper charges;
    private final DeliveryExpressTemplateFreeMapper frees;
    private final SecurityFrameworkService security;
    private final RemoteSystemSecurityClient remote;
    private final Validator validator;
    private static final String PERMISSION="trade:delivery:express-template:";
    public DeliveryExpressTemplateServiceImpl(DeliveryExpressTemplateMapper templates,DeliveryExpressTemplateChargeMapper charges,
            DeliveryExpressTemplateFreeMapper frees,SecurityFrameworkService security,RemoteSystemSecurityClient remote,Validator validator) {
        this.templates=templates;this.charges=charges;this.frees=frees;this.security=security;this.remote=remote;this.validator=validator;
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public DeliveryExpressTemplateDO validateDeliveryExpressTemplate(Long templateId) {
        return getSnapshot(templateId).getTemplate();
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public DeliveryTemplateSnapshotBO getSnapshot(Long templateId) {
        actor();return loadSnapshot(templateId);
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public Map<Long,DeliveryTemplateSnapshotBO> getCheckoutSnapshots(Collection<Long> ids) {
        LoginUser user=SecurityFrameworkUtils.getLoginUser();
        if(user==null || !UserTypeEnum.MEMBER.getValue().equals(user.getUserType()) || user.getTenantId()==null || user.getTenantId()<=0
                || TenantContextHolder.isIgnore() || !Objects.equals(user.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"结算配送需要本店会员身份");
        if(ids==null || ids.isEmpty() || ids.size()>200 || ids.stream().anyMatch(id->id==null || id<=0))throw exception0(400,"配送模板编号不正确");
        Map<Long,DeliveryTemplateSnapshotBO> result=new TreeMap<>();
        for(Long id:new TreeSet<>(ids))result.put(id,loadSnapshot(id));return result;
    }
    private DeliveryTemplateSnapshotBO loadSnapshot(Long templateId) {
        DeliveryExpressTemplateDO template=required(templateId,false);
        if (!Integer.valueOf(0).equals(template.getStatus())) { throw exception0(400,"配送模板已停用"); }
        List<DeliveryExpressTemplateChargeDO> chargeList=charges.selectListByTemplateId(templateId);
        List<DeliveryExpressTemplateFreeDO> freeList=frees.selectListByTemplateId(templateId);
        configuration(template,chargeList,freeList);
        DeliveryTemplateSnapshotBO result=new DeliveryTemplateSnapshotBO();
        result.setTemplate(template);result.setCharges(chargeList);result.setFrees(freeList);return result;
    }

    private void configuration(DeliveryExpressTemplateDO template,List<DeliveryExpressTemplateChargeDO> chargeList,
            List<DeliveryExpressTemplateFreeDO> freeList) {
        if (!Integer.valueOf(1).equals(template.getChargeMode()) && !Integer.valueOf(2).equals(template.getChargeMode())) {
            throw exception0(400,"配送模板计费方式不正确");
        }
        if (chargeList.isEmpty() || chargeList.size()>200) { throw exception0(400,"请配置合法的配送地区及计费规则"); }
        Set<Integer> selected=new HashSet<>(),ancestors=new HashSet<>();boolean defaultSeen=false;
        for (DeliveryExpressTemplateChargeDO rule:chargeList) {
            if (!Objects.equals(rule.getChargeMode(),template.getChargeMode()) || !positive(rule.getStartCount())
                    || !positive(rule.getExtraCount()) || rule.getStartPrice()==null || rule.getStartPrice()<0
                    || rule.getExtraPrice()==null || rule.getExtraPrice()<0
                    || (template.getChargeMode()==1 && (rule.getStartCount().stripTrailingZeros().scale()>0
                    || rule.getExtraCount().stripTrailingZeros().scale()>0))) { throw exception0(400,"配送计费规则不合法"); }
            defaultSeen=regions(rule.getDefaultRule(),rule.getAreaIds(),defaultSeen,selected,ancestors);
        }
        if (freeList.size()>200) { throw exception0(400,"包邮规则数量超过上限"); }
        selected.clear();ancestors.clear();defaultSeen=false;
        for (DeliveryExpressTemplateFreeDO rule:freeList) {
            if (rule.getFreePrice()==null || rule.getFreePrice()<0 || rule.getFreeCount()==null || rule.getFreeCount()<0
                    || (rule.getFreePrice()==0 && rule.getFreeCount()==0)) { throw exception0(400,"包邮规则须至少设置金额或件数门槛"); }
            defaultSeen=regions(rule.getDefaultRule(),rule.getAreaIds(),defaultSeen,selected,ancestors);
        }
    }

    @Override
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=15)
    public Long createDeliveryExpressTemplate(DeliveryExpressTemplateCreateReqVO body) {
        LoginUser user=authorize("create"); validate(body);
        if (body.getCharges().stream().anyMatch(row -> row.getId()!=null) || body.getFrees().stream().anyMatch(row -> row.getId()!=null)) {
            throw exception0(400,"新增模板不能指定规则编号");
        }
        DeliveryExpressTemplateDO row=BeanUtils.toBean(body,DeliveryExpressTemplateDO.class);row.setName(body.getName().trim());
        List<DeliveryExpressTemplateChargeDO> chargeRows=chargeRows(null,row.getChargeMode(),body.getCharges());
        List<DeliveryExpressTemplateFreeDO> freeRows=freeRows(null,body.getFrees());
        configuration(row,chargeRows,freeRows); uniqueName(null,row.getName());
        row.setAuthVersion(0L);created(row,user);
        try {
            if (templates.insert(row)!=1 || row.getId()==null) { throw new IllegalStateException("配送模板保存失败"); }
            syncCharges(row.getId(),chargeRows,Collections.emptyList(),user);
            syncFrees(row.getId(),freeRows,Collections.emptyList(),user);
        } catch (DuplicateKeyException error) { throw exception0(409,"模板名称或默认规则已存在"); }
        audit(user,"DELIVERY.CREATE",row.getId(),-1L,0L,null,detail(row,chargeRows,freeRows));
        authorize("create");return row.getId();
    }

    @Override
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=15)
    public void updateDeliveryExpressTemplate(DeliveryExpressTemplateUpdateReqVO body) {
        LoginUser user=authorize("update");validate(body);
        DeliveryExpressTemplateDO current=required(body.getId(),true);version(current.getAuthVersion(),body.getExpectedVersion());
        List<DeliveryExpressTemplateChargeDO> oldCharges=charges.selectListByTemplateId(current.getId());
        List<DeliveryExpressTemplateFreeDO> oldFrees=frees.selectListByTemplateId(current.getId());
        DeliveryExpressTemplateDO patch=BeanUtils.toBean(body,DeliveryExpressTemplateDO.class);patch.setName(body.getName().trim());
        List<DeliveryExpressTemplateChargeDO> nextCharges=chargeRows(current.getId(),patch.getChargeMode(),body.getCharges());
        List<DeliveryExpressTemplateFreeDO> nextFrees=freeRows(current.getId(),body.getFrees());
        configuration(patch,nextCharges,nextFrees);validateRuleIds(nextCharges.stream().map(DeliveryExpressTemplateChargeDO::getId).collect(Collectors.toList()),
                oldCharges.stream().map(DeliveryExpressTemplateChargeDO::getId).collect(Collectors.toSet()));
        validateRuleIds(nextFrees.stream().map(DeliveryExpressTemplateFreeDO::getId).collect(Collectors.toList()),oldFrees.stream().map(DeliveryExpressTemplateFreeDO::getId).collect(Collectors.toSet()));
        uniqueName(current.getId(),patch.getName());
        try {
            syncCharges(current.getId(),nextCharges,oldCharges,user);syncFrees(current.getId(),nextFrees,oldFrees,user);
            if (templates.updateConfiguration(user.getTenantId(),current.getId(),current.getAuthVersion(),patch,user.getId().toString())!=1) {
                throw exception0(409,"配送模板已变化，请重新读取");
            }
        } catch (DuplicateKeyException error) { throw exception0(409,"模板名称或默认规则已存在"); }
        patch.setId(current.getId());patch.setAuthVersion(current.getAuthVersion()+1);
        audit(user,"DELIVERY.UPDATE",current.getId(),current.getAuthVersion(),patch.getAuthVersion(),detail(current,oldCharges,oldFrees),detail(patch,nextCharges,nextFrees));
        authorize("update");
    }

    @Override
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=15)
    public void deleteDeliveryExpressTemplate(Long id,Long expectedVersion) {
        LoginUser user=authorize("delete");DeliveryExpressTemplateDO current=required(id,true);version(current.getAuthVersion(),expectedVersion);
        List<DeliveryExpressTemplateChargeDO> oldCharges=charges.selectListByTemplateId(id);
        List<DeliveryExpressTemplateFreeDO> oldFrees=frees.selectListByTemplateId(id);
        for (DeliveryExpressTemplateChargeDO row:oldCharges) { retireCharge(id,row.getId(),user); }
        for (DeliveryExpressTemplateFreeDO row:oldFrees) { retireFree(id,row.getId(),user); }
        if (templates.retire(user.getTenantId(),id,current.getAuthVersion(),user.getId().toString())!=1) { throw exception0(409,"配送模板已变化"); }
        audit(user,"DELIVERY.DELETE",id,current.getAuthVersion(),current.getAuthVersion()+1,detail(current,oldCharges,oldFrees),null);
        authorize("delete");
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=10)
    public DeliveryExpressTemplateDetailRespVO getDeliveryExpressTemplate(Long id) {
        readAccess();DeliveryExpressTemplateDO row=required(id,false);
        return detail(row,charges.selectListByTemplateId(id),frees.selectListByTemplateId(id));
    }
    @Override
    public PageResult<DeliveryExpressTemplateDO> getDeliveryExpressTemplatePage(DeliveryExpressTemplatePageReqVO body) {
        readAccess();validate(body);return templates.selectPage(body);
    }
    @Override
    public List<DeliveryExpressTemplateDO> getDeliveryExpressTemplateList(Collection<Long> ids) {
        readAccess();if (ids==null || ids.isEmpty()) { return Collections.emptyList(); }
        if (ids.size()>2000 || ids.stream().anyMatch(id -> id==null || id<=0)) { throw exception0(400,"模板编号列表不正确"); }
        return templates.selectByIds(ids);
    }
    @Override
    public List<DeliveryExpressTemplateDO> getDeliveryExpressTemplateList() {
        actor();if (!security.hasAnyPermissions(PERMISSION+"query","product:spu:create","product:spu:update")) { throw exception0(403,"没有模板选项权限"); }
        return templates.selectList(new cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX<DeliveryExpressTemplateDO>()
                .eq(DeliveryExpressTemplateDO::getStatus,0).orderByAsc(DeliveryExpressTemplateDO::getSort).orderByAsc(DeliveryExpressTemplateDO::getId));
    }

    private List<DeliveryExpressTemplateChargeDO> chargeRows(Long id,Integer mode,List<DeliveryExpressTemplateChargeBaseVO> source) {
        return source.stream().map(body -> {
            DeliveryExpressTemplateChargeDO row=BeanUtils.toBean(body,DeliveryExpressTemplateChargeDO.class);
            row.setTemplateId(id);row.setChargeMode(mode);row.setAreaIds(new ArrayList<>(body.getAreaIds()));return row;
        }).collect(Collectors.toList());
    }
    private List<DeliveryExpressTemplateFreeDO> freeRows(Long id,List<DeliveryExpressTemplateFreeBaseVO> source) {
        return source.stream().map(body -> {
            DeliveryExpressTemplateFreeDO row=BeanUtils.toBean(body,DeliveryExpressTemplateFreeDO.class);
            row.setTemplateId(id);row.setAreaIds(new ArrayList<>(body.getAreaIds()));return row;
        }).collect(Collectors.toList());
    }
    private void validateRuleIds(List<Long> requested,Set<Long> old) {
        Set<Long> seen=new HashSet<>();
        for (Long id:requested) {
            if (id!=null && (!seen.add(id) || !old.contains(id))) { throw exception0(400,"规则编号重复或不属于当前有效模板配置"); }
        }
    }
    private void syncCharges(Long id,List<DeliveryExpressTemplateChargeDO> next,List<DeliveryExpressTemplateChargeDO> old,LoginUser user) {
        Set<Long> kept=next.stream().map(DeliveryExpressTemplateChargeDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        // 先释放旧默认唯一位，允许默认规则在两个保留ID之间切换；仍在父锁/同事务内。
        for (DeliveryExpressTemplateChargeDO row:old) {
            if (Boolean.TRUE.equals(row.getDefaultRule()) || !kept.contains(row.getId())) { retireCharge(id,row.getId(),user); }
        }
        for (DeliveryExpressTemplateChargeDO row:next) {
            row.setTemplateId(id);
            if (row.getId()==null) { created(row,user);if (charges.insert(row)!=1) { throw new IllegalStateException("计费规则保存失败"); } }
            else if (charges.updateRetained(user.getTenantId(),id,row.getId(),row,JsonUtils.toJsonString(row.getAreaIds()),user.getId().toString())!=1) {
                throw new IllegalStateException("计费规则更新失败");
            }
        }
    }
    private void syncFrees(Long id,List<DeliveryExpressTemplateFreeDO> next,List<DeliveryExpressTemplateFreeDO> old,LoginUser user) {
        Set<Long> kept=next.stream().map(DeliveryExpressTemplateFreeDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (DeliveryExpressTemplateFreeDO row:old) {
            if (Boolean.TRUE.equals(row.getDefaultRule()) || !kept.contains(row.getId())) { retireFree(id,row.getId(),user); }
        }
        for (DeliveryExpressTemplateFreeDO row:next) {
            row.setTemplateId(id);
            if (row.getId()==null) { created(row,user);if (frees.insert(row)!=1) { throw new IllegalStateException("包邮规则保存失败"); } }
            else if (frees.updateRetained(user.getTenantId(),id,row.getId(),row,JsonUtils.toJsonString(row.getAreaIds()),user.getId().toString())!=1) {
                throw new IllegalStateException("包邮规则更新失败");
            }
        }
    }
    private void retireCharge(Long templateId,Long id,LoginUser user) {
        if (charges.retire(user.getTenantId(),templateId,id,user.getId().toString())!=1) { throw new IllegalStateException("计费规则删除失败"); }
    }
    private void retireFree(Long templateId,Long id,LoginUser user) {
        if (frees.retire(user.getTenantId(),templateId,id,user.getId().toString())!=1) { throw new IllegalStateException("包邮规则删除失败"); }
    }
    private DeliveryExpressTemplateDetailRespVO detail(DeliveryExpressTemplateDO template,List<DeliveryExpressTemplateChargeDO> chargeRows,List<DeliveryExpressTemplateFreeDO> freeRows) {
        DeliveryExpressTemplateDetailRespVO result=BeanUtils.toBean(template,DeliveryExpressTemplateDetailRespVO.class);
        result.setCharges(BeanUtils.toBean(chargeRows,DeliveryExpressTemplateChargeBaseVO.class));
        result.setFrees(BeanUtils.toBean(freeRows,DeliveryExpressTemplateFreeBaseVO.class));return result;
    }
    private DeliveryExpressTemplateDO required(Long id,boolean lock) {
        DeliveryExpressTemplateDO row=id==null || id<=0?null:(lock?templates.lockById(id):templates.selectById(id));
        if (row==null) { throw exception0(404,"配送模板不存在或不属于当前租户"); }return row;
    }
    private void version(Long actual,Long expected) {
        if (expected==null || expected<0) { throw exception0(400,"请提供配送模板当前版本"); }
        if (!Objects.equals(actual,expected)) { throw exception0(409,"配送模板已变化，请重新读取"); }
        if (actual==Long.MAX_VALUE) { throw new IllegalStateException("配送模板版本达到上限"); }
    }
    private void uniqueName(Long id,String name) {
        DeliveryExpressTemplateDO same=templates.selectByName(name);
        if (same!=null && !Objects.equals(same.getId(),id)) { throw exception0(409,"配送模板名称已存在"); }
    }
    private LoginUser actor() {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (actor==null || !UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getTenantId()==null
                || actor.getTenantId()<=0 || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())) {
            throw exception0(403,"配送模板身份不正确");
        }return actor;
    }
    private LoginUser authorize(String action) {
        LoginUser user=actor();if (!security.hasPermission(PERMISSION+action)) { throw exception0(403,"没有配送模板操作权限"); }
        remote.checkTenantOperation();return user;
    }
    private void readAccess() {
        actor();if (!security.hasPermission(PERMISSION+"query")) { throw exception0(403,"没有配送模板查询权限"); }
    }
    private void validate(Object body) {
        if (body==null || !validator.validate(body).isEmpty()) { throw exception0(400,"配送模板字段不完整或超出范围"); }
    }
    private void created(cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo row,LoginUser user) {
        row.setTenantId(user.getTenantId());row.setCreator(user.getId().toString());row.setUpdater(user.getId().toString());
        row.setCreateTime(LocalDateTime.now());row.setUpdateTime(row.getCreateTime());row.setDeleted(false);
    }
    private void audit(LoginUser user,String action,Long id,Long from,Long to,Object before,Object after) {
        Map<String,Object> value=new LinkedHashMap<>();value.put("before",before);value.put("after",after);
        if (templates.audit(user.getTenantId(),user.getId(),action,id,from,to,JsonUtils.toJsonString(value))!=1) {
            throw new IllegalStateException("配送模板审计保存失败");
        }
    }

    private boolean positive(BigDecimal value) { return value!=null && value.signum()>0; }
    private boolean regions(Boolean defaultRule,List<Integer> ids,boolean defaultSeen,Set<Integer> selected,Set<Integer> ancestors) {
        if (defaultRule==null || ids==null || ids.size()>500) { throw exception0(400,"配送区域配置不正确"); }
        if (defaultRule) {
            if (defaultSeen || !ids.isEmpty()) { throw exception0(400,"默认配送规则只能有一条且不指定区域"); }
            return true;
        }
        if (ids.isEmpty()) { throw exception0(400,"地区规则必须选择区域"); }
        for (Integer id:ids) {
            if (!AreaUtils.isChinaRegion(id) || ancestors.contains(id)) { throw exception0(400,"区域不存在或与其他地区规则重叠"); }
            Area cursor=AreaUtils.getArea(id);Set<Integer> visited=new HashSet<>();
            while (cursor!=null && visited.add(cursor.getId())) {
                if (selected.contains(cursor.getId())) { throw exception0(400,"省市区规则存在重叠"); }
                cursor=cursor.getParent();
            }
            selected.add(id);cursor=AreaUtils.getArea(id).getParent();
            while (cursor!=null) { ancestors.add(cursor.getId());cursor=cursor.getParent(); }
        }
        return defaultSeen;
    }
}
