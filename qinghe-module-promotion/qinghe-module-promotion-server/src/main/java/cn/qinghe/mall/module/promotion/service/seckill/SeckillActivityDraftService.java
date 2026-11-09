package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.SeckillProductSnapshotRespDTO;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillActivityMapper;
import cn.qinghe.mall.module.promotion.framework.product.SeckillProductClient;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillActivityPoolSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.validation.Validator;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原 createSeckillActivity/updateSeckillActivity/getSeckillActivity 的商品校验、主从保存与读取职责。
 * 保存首次草稿或经原号结清恢复的草稿；不设置已发布、不划拨、不覆盖历史账本。
 * @author halfninety
 */
@Service
public class SeckillActivityDraftService {
    private final SeckillActivityMapper mapper;
    private final SeckillProductClient products;
    private final SecurityFrameworkService security;
    private final RemoteSystemSecurityClient remote;
    private final Validator validator;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public SeckillActivityDraftService(SeckillActivityMapper mapper,SeckillProductClient products,
            SecurityFrameworkService security,RemoteSystemSecurityClient remote,Validator validator,PlatformTransactionManager manager) {
        this.mapper=mapper;this.products=products;this.security=security;this.remote=remote;this.validator=validator;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }

    public SeckillActivityDraftRespVO save(SeckillActivitySaveReqVO body) {
        outsideTransaction();validate(body);
        String permission=body.getId()==null ? "create" : "update";
        LoginUser actor=actor(permission);remote.checkTenantOperation();
        SeckillProductSnapshotRespDTO snapshot=products.snapshot(body.getProductSpuId(),
                body.getSkus().stream().map(SeckillActivitySaveReqVO.Sku::getSkuId).collect(Collectors.toList()));
        // RPC 后、开事务前重新确认权限；事务内只复核本次身份、到期与参数时间，不持锁做远程调用。
        sameActor(actor,actor(permission));
        return write.execute(tx -> {
            fresh(actor);validate(body);
            SeckillActivityDO row=configuration(body,snapshot,actor);
            Long previous=-1L;
            if (body.getId()==null) {
                row.setActivityNo(UUID.randomUUID().toString());row.setCreator(actor.getId().toString());
                if (mapper.insert(row)!=1 || !id(row.getId())) throw new IllegalStateException("活动草稿创建失败");
            } else {
                SeckillActivityDO old=mapper.lock(actor.getTenantId(),body.getId());
                if (old==null) throw exception0(404,"活动不存在");
                if (!Objects.equals(old.getVersion(),body.getExpectedVersion()) || old.getVersion()>=9_007_199_254_740_991L)
                    throw exception0(409,"活动已变化，请重新读取");
                if (!Integer.valueOf(0).equals(old.getStatus()) || old.getPublishOperationNo()!=null)
                    throw exception0(409,"仅首次草稿或已结清恢复的草稿可以修改");
                List<SeckillActivitySkuDO> oldSkus=mapper.skus(actor.getTenantId(),old.getId());
                if (oldSkus.isEmpty() || oldSkus.size()>200 || oldSkus.stream().anyMatch(this::hasObligation))
                    throw exception0(409,"原活动存在库存义务或规格异常，不能覆盖");
                if (mapper.updateDraft(row,body.getExpectedVersion())!=1
                        || mapper.removeDraftSkus(actor.getTenantId(),old.getId())!=oldSkus.size())
                    throw exception0(409,"草稿已变化，请重新读取");
                previous=old.getVersion();
            }
            List<SeckillActivitySkuDO> selected=skuRows(row,snapshot,body);
            for (SeckillActivitySkuDO sku:selected) {
                if (mapper.insertSku(sku)!=1) throw new IllegalStateException("活动规格保存失败");
            }
            Map<String,Object> details=new LinkedHashMap<>();details.put("configuration",row);details.put("skus",selected);
            if (mapper.audit(actor.getTenantId(),row.getId(),actor.getId(),previous<0?"CREATE":"UPDATE",previous,previous+1,
                    JsonUtils.toJsonString(details))!=1) throw new IllegalStateException("活动配置审计保存失败");
            return project(mapper.get(actor.getTenantId(),row.getId()),mapper.skus(actor.getTenantId(),row.getId()));
        });
    }

    public SeckillActivityDraftRespVO get(Long activityId) {
        outsideTransaction();LoginUser actor=actor("query");
        if (!id(activityId)) throw exception0(400,"活动编号不正确");
        return read.execute(tx -> {
            fresh(actor);SeckillActivityDO row=mapper.get(actor.getTenantId(),activityId);
            if (row==null) throw exception0(404,"活动不存在");
            return project(row,mapper.skus(actor.getTenantId(),activityId));
        });
    }

    /** 沿用原活动分页后批量拼接商品的方式，改用已保存的快照和本页额度汇总，读取无RPC。 */
    public PageResult<SeckillActivityPageRespVO> page(SeckillActivityPageReqVO body) {
        outsideTransaction();LoginUser actor=actor("query");
        if (body==null || !validator.validate(body).isEmpty() || body.getPageNo()>10000 || body.getPageSize()>20
                || body.getName()!=null && body.getName().chars().anyMatch(Character::isISOControl)
                || body.getStatus()!=null && !Arrays.asList(0,10,20,30,40,50,60).contains(body.getStatus()))
            throw exception0(400,"活动查询条件不正确，每页最多20条");
        if (body.getName()!=null) body.setName(body.getName().trim().isEmpty()?null:body.getName().trim());
        int offset=(body.getPageNo()-1)*body.getPageSize();
        return read.execute(tx -> {
            fresh(actor);Long total=mapper.countPage(actor.getTenantId(),body);
            if (total==null || total<0 || total>9_007_199_254_740_991L) throw exception0(503,"活动分页数量无法投影");
            if (offset>=total) return PageResult.empty(total);
            List<SeckillActivityDO> rows=mapper.page(actor.getTenantId(),body,offset,body.getPageSize());
            if (rows==null || rows.isEmpty() || rows.size()>body.getPageSize()) throw exception0(503,"活动分页数据不完整");
            List<Long> ids=rows.stream().map(SeckillActivityDO::getId).collect(Collectors.toList());
            Map<Long,SeckillActivityPoolSummary> pools=mapper.summarizePage(actor.getTenantId(),ids).stream()
                    .collect(Collectors.toMap(SeckillActivityPoolSummary::getActivityId,x->x));
            List<SeckillActivityPageRespVO> result=new ArrayList<>();
            long serverTime=System.currentTimeMillis();
            for (SeckillActivityDO row:rows) result.add(pageRow(row,pools.get(row.getId()),serverTime));
            return new PageResult<>(total,result);
        });
    }

    private SeckillActivityPageRespVO pageRow(SeckillActivityDO row,SeckillActivityPoolSummary pool,long serverTime) {
        if (!id(row.getId()) || pool==null || pool.getSkuCount()==null || pool.getSkuCount()<1 || pool.getSkuCount()>200)
            throw exception0(503,"活动规格汇总不完整");
        List<Long> quantities=Arrays.asList(pool.getPlannedQuantity(),pool.getAllocatedQuantity(),pool.getAvailableQuantity(),
                pool.getHeldQuantity(),pool.getSoldQuantity(),pool.getReturnedQuantity(),pool.getReturnPendingQuantity());
        if (quantities.stream().anyMatch(x->x==null || x<0 || x>9_007_199_254_740_991L)
                || pool.getPlannedQuantity()<1 || pool.getReturnPendingQuantity()>pool.getAvailableQuantity()
                || pool.getAllocatedQuantity()!=pool.getAvailableQuantity()+pool.getHeldQuantity()+pool.getSoldQuantity()+pool.getReturnedQuantity())
            throw exception0(503,"原活动库存余额需要核对");
        SeckillActivityPageRespVO result=BeanUtils.toBean(pool,SeckillActivityPageRespVO.class);
        result.setId(row.getId());result.setActivityNo(row.getActivityNo());result.setName(row.getName());
        result.setProductSpuId(row.getProductSpuId());result.setProductName(row.getProductName());result.setPicUrl(row.getPicUrl());
        result.setStartTime(row.getStartTime().atOffset(ZoneOffset.UTC));result.setEndTime(row.getEndTime().atOffset(ZoneOffset.UTC));
        result.setSingleLimit(row.getSingleLimit());result.setMemberLimit(row.getMemberLimit());result.setStatus(row.getStatus());
        result.setVersion(row.getVersion());result.setGeneration(row.getGeneration());result.setServerTime(serverTime);return result;
    }

    private void validate(SeckillActivitySaveReqVO body) {
        if (body==null || !validator.validate(body).isEmpty() || (body.getId()==null)!=(body.getExpectedVersion()==null)
                || body.getName().chars().anyMatch(Character::isISOControl) || body.getSingleLimit()>body.getMemberLimit()
                || body.getSkus().stream().anyMatch(Objects::isNull)) throw exception0(400,"活动配置、商品规格或版本不正确");
        Instant start=body.getStartTime().toInstant(),end=body.getEndTime().toInstant();
        if (!start.isAfter(Instant.now()) || !start.isBefore(end) || start.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
                || !end.isBefore(Instant.parse("2100-01-01T00:00:00Z"))
                || start.getNano()%1_000_000!=0 || end.getNano()%1_000_000!=0)
            throw exception0(400,"请提供尚未开始的有效毫秒精度活动时间，结束不包含在活动区间内");
        Set<Long> ids=new HashSet<>();long total=0;
        for (SeckillActivitySaveReqVO.Sku sku:body.getSkus()) {
            if (!ids.add(sku.getSkuId())) throw exception0(400,"活动规格不能重复");
            total+=sku.getPlannedQuantity();
        }
        if (total>Integer.MAX_VALUE) throw exception0(400,"活动计划总数量超过上限");
    }

    private SeckillActivityDO configuration(SeckillActivitySaveReqVO body,SeckillProductSnapshotRespDTO snapshot,LoginUser actor) {
        SeckillActivityDO row=new SeckillActivityDO();row.setId(body.getId());row.setTenantId(actor.getTenantId());
        row.setName(body.getName().trim());row.setProductSpuId(snapshot.getSpuId());row.setProductSpuVersion(snapshot.getSpuVersion());
        row.setProductName(snapshot.getProductName());row.setPicUrl(snapshot.getPicUrl());
        row.setStartTime(LocalDateTime.ofInstant(body.getStartTime().toInstant(),ZoneOffset.UTC));
        row.setEndTime(LocalDateTime.ofInstant(body.getEndTime().toInstant(),ZoneOffset.UTC));
        row.setSingleLimit(body.getSingleLimit());row.setMemberLimit(body.getMemberLimit());
        row.setUpdater(actor.getId().toString());return row;
    }

    private List<SeckillActivitySkuDO> skuRows(SeckillActivityDO activity,SeckillProductSnapshotRespDTO snapshot,SeckillActivitySaveReqVO body) {
        Map<Long,SeckillActivitySaveReqVO.Sku> plans=body.getSkus().stream().collect(Collectors.toMap(SeckillActivitySaveReqVO.Sku::getSkuId,x->x));
        List<SeckillActivitySkuDO> rows=new ArrayList<>();
        for (SeckillProductSnapshotRespDTO.Sku sku:snapshot.getSkus()) {
            SeckillActivitySaveReqVO.Sku plan=plans.get(sku.getSkuId());
            SeckillActivitySkuDO row=new SeckillActivitySkuDO();row.setTenantId(activity.getTenantId());row.setActivityId(activity.getId());
            row.setSkuId(sku.getSkuId());row.setSkuVersion(sku.getSkuVersion());row.setSpecSnapshot(JsonUtils.toJsonString(sku.getProperties()));
            row.setPriceCent(plan.getPriceCent());row.setPlannedQuantity(plan.getPlannedQuantity());rows.add(row);
        }
        return rows;
    }

    private boolean hasObligation(SeckillActivitySkuDO sku) {
        return !Integer.valueOf(0).equals(sku.getAllocatedQuantity()) || !Integer.valueOf(0).equals(sku.getAvailableQuantity())
                || !Integer.valueOf(0).equals(sku.getHeldQuantity()) || !Integer.valueOf(0).equals(sku.getSoldQuantity())
                || !Integer.valueOf(0).equals(sku.getReturnedQuantity()) || !Integer.valueOf(0).equals(sku.getReturnPendingQuantity())
                || !Integer.valueOf(0).equals(sku.getAllocationStatus()) || sku.getAllocationOperationNo()!=null;
    }

    private SeckillActivityDraftRespVO project(SeckillActivityDO row,List<SeckillActivitySkuDO> items) {
        if (row==null || !id(row.getId()) || items==null || items.isEmpty() || items.size()>200)
            throw exception0(503,"原活动配置不完整");
        SeckillActivityDraftRespVO result=new SeckillActivityDraftRespVO();
        result.setId(row.getId());result.setActivityNo(row.getActivityNo());result.setName(row.getName());
        result.setProductSpuId(row.getProductSpuId());result.setProductSpuVersion(row.getProductSpuVersion());
        result.setProductName(row.getProductName());result.setPicUrl(row.getPicUrl());
        result.setStartTime(row.getStartTime().atOffset(ZoneOffset.UTC));result.setEndTime(row.getEndTime().atOffset(ZoneOffset.UTC));
        result.setSingleLimit(row.getSingleLimit());result.setMemberLimit(row.getMemberLimit());result.setStatus(row.getStatus());
        result.setVersion(row.getVersion());result.setGeneration(row.getGeneration());result.setServerTime(System.currentTimeMillis());
        List<SeckillActivityDraftRespVO.Sku> selected=new ArrayList<>();
        for (SeckillActivitySkuDO sku:items) {
            ProductTradeSkuRespDTO.Property[] properties=JsonUtils.parseObject(sku.getSpecSnapshot(),ProductTradeSkuRespDTO.Property[].class);
            if (properties==null || properties.length==0 || properties.length>20) throw exception0(503,"原规格快照不完整");
            SeckillActivityDraftRespVO.Sku item=BeanUtils.toBean(sku,SeckillActivityDraftRespVO.Sku.class);
            item.setProperties(Arrays.asList(properties));selected.add(item);
        }
        result.setSkus(selected);return result;
    }

    private LoginUser actor(String operation) {
        LoginUser user=SecurityFrameworkUtils.getLoginUser();
        if (user==null) throw exception0(401,"请先登录");
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType()) || !id(user.getId()) || !id(user.getTenantId())
                || TenantContextHolder.isIgnore() || !Objects.equals(user.getTenantId(),TenantContextHolder.getTenantId()))
            throw exception0(403,"活动配置仅限当前商家员工");
        fresh(user);
        if (!security.hasPermission("promotion:seckill-activity:"+operation)) throw exception0(403,"没有该活动操作权限");
        return user;
    }
    private void fresh(LoginUser user) {
        if (TenantContextHolder.isIgnore() || !Objects.equals(user.getTenantId(),TenantContextHolder.getTenantId())
                || user.getExpiresTime()==null || !user.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
            throw exception0(401,"活动操作身份已到期或发生变化");
    }
    private void sameActor(LoginUser first,LoginUser next) {
        if (!Objects.equals(first.getId(),next.getId()) || !Objects.equals(first.getTenantId(),next.getTenantId()))
            throw exception0(401,"活动操作身份已变化");
    }
    private void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("活动草稿入口不能嵌套外部事务");
    }
    private boolean id(Long value) { return value!=null && value>0 && value<=9_007_199_254_740_991L; }
}
