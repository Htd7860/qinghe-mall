package cn.qinghe.mall.module.trade.service.aftersale;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.controller.aftersale.vo.*;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.TradeDeliveryExpressRespVO;
import cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate.DeliveryAreaOptionRespVO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.AfterSaleQueryMapper;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.AfterSaleRefundMapper;
import cn.qinghe.mall.module.trade.dal.mysql.delivery.DeliveryExpressMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.service.aftersale.dto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Validator;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原售后分页/本人详情/原单商品组合/日志职责，GET不推进资金或状态。
 * 商家权限代理、会员实际Host核对在本地只读事务之前；不附带当前会员手机号或原始日志details。
 * @author 芋道源码
 */
@Service
public class AfterSaleQueryService {
    private final AfterSaleQueryMapper queries;
    private final AfterSaleRefundMapper refunds;
    private final DeliveryExpressMapper carriers;
    private final TradeMemberAccess members;
    private final Validator validator;
    private final TransactionTemplate read;
    public AfterSaleQueryService(AfterSaleQueryMapper queries,DeliveryExpressMapper carriers,TradeMemberAccess members,Validator validator,PlatformTransactionManager manager,AfterSaleRefundMapper refunds) {
        this.queries=queries;this.carriers=carriers;this.members=members;this.validator=validator;this.refunds=refunds;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
    }

    @PreAuthorize("@ss.hasPermission('trade:after-sale:query')")
    public PageResult<AfterSaleSummaryRespVO> merchantPage(AfterSalePageReqVO body) {
        outside();long offset=offset(body);filter(body);return page(merchant(),false,body,offset);
    }
    public PageResult<AfterSaleSummaryRespVO> memberPage(HttpServletRequest request,String store,AfterSalePageReqVO body) {
        long offset=offset(body);filter(body);return page(member(request,store),true,body,offset);
    }
    private PageResult<AfterSaleSummaryRespVO> page(LoginUser actor,boolean member,AfterSalePageReqVO body,long offset) {
        long checkedAt=System.nanoTime();return read.execute(status -> {
            LocalDateTime instant=now();Long owner=member?actor.getId():null;
            long total=queries.count(actor.getTenantId(),owner,body,instant);
            List<AfterSaleDO> rows=queries.page(actor.getTenantId(),owner,body,instant,offset);
            List<AfterSaleSummaryRespVO> values=summaries(actor,rows,instant);fresh(actor,member,checkedAt);return new PageResult<>(total,values);
        });
    }
    @PreAuthorize("@ss.hasPermission('trade:after-sale:query')")
    public AfterSaleDetailRespVO merchantDetail(Long id){outside();positive(id);return detail(merchant(),false,id);}
    public AfterSaleDetailRespVO memberDetail(HttpServletRequest request,String store,Long id){positive(id);return detail(member(request,store),true,id);}
    private AfterSaleDetailRespVO detail(LoginUser actor,boolean member,Long id) {
        long checkedAt=System.nanoTime();return read.execute(status -> {
            AfterSaleDO row=required(actor,member,id);
            AfterSaleDetailRespVO result=BeanUtils.toBean(summaries(actor,Collections.singletonList(row),now()).get(0),AfterSaleDetailRespVO.class);
            result.setApplyReason(row.getApplyReason());result.setApplyDescription(row.getApplyDescription());
            result.setQualityApproved(row.getQualityApproved());result.setAuditReason(row.getAuditReason());result.setAuditedAt(epoch(row.getAuditTime()));
            result.setDeliveredAt(epoch(row.getDeliveryTime()));result.setInspectedAt(epoch(row.getReceiveTime()));
            result.setLogisticsId(row.getLogisticsId());result.setLogisticsName(row.getLogisticsName());result.setLogisticsNo(row.getLogisticsNo());
            result.setReceivedCount(row.getReceivedCount());result.setSalableCount(row.getSalableCount());
            result.setUnsalableCount(row.getReceivedCount()-row.getSalableCount());result.setReceiveReason(row.getReceiveReason());
            AfterSaleRefundDO plan=refunds.get(actor.getTenantId(),id);
            if (plan!=null) {
                if (!Objects.equals(plan.getUserId(),row.getUserId()) || !Objects.equals(plan.getOrderId(),row.getOrderId())
                        || !Objects.equals(plan.getOrderItemId(),row.getOrderItemId())
                        || "SUCCESS".equals(plan.getStatus()) != (row.getStatus()==50)
                        || !Objects.equals(plan.getRefundTime(),row.getRefundTime())) throw exception0(503,"原退款进度需核对");
                AfterSaleDetailRespVO.Refund progress=BeanUtils.toBean(plan,AfterSaleDetailRespVO.Refund.class);
                progress.setConfirmedAt(epoch(plan.getRefundTime()));result.setRefund(progress);
            } else if (row.getStatus()==45 || row.getStatus()==50) throw exception0(503,"原退款记录缺失");
            fresh(actor,member,checkedAt);return result;
        });
    }
    @PreAuthorize("@ss.hasPermission('trade:after-sale:query')")
    public PageResult<AfterSaleLogRespVO> merchantLogs(Long id,PageParam body){outside();positive(id);long offset=offset(body);return logs(merchant(),false,id,body,offset);}
    public PageResult<AfterSaleLogRespVO> memberLogs(HttpServletRequest request,String store,Long id,PageParam body){positive(id);long offset=offset(body);return logs(member(request,store),true,id,body,offset);}
    private PageResult<AfterSaleLogRespVO> logs(LoginUser actor,boolean member,Long id,PageParam body,long offset) {
        long checkedAt=System.nanoTime();return read.execute(status -> {
            required(actor,member,id);long total=queries.logCount(actor.getTenantId(),id);
            List<AfterSaleLogRespVO> result=queries.logs(actor.getTenantId(),id,body.getPageSize(),offset).stream().map(this::log).collect(Collectors.toList());
            fresh(actor,member,checkedAt);return new PageResult<>(total,result);
        });
    }
    @PreAuthorize("@ss.hasPermission('trade:after-sale:audit')")
    public AfterSaleReturnAddress merchantReturnAddress(Long id){outside();positive(id);return address(merchant(),false,id);}
    @PreAuthorize("@ss.hasPermission('trade:after-sale:audit')")
    public List<DeliveryAreaOptionRespVO> merchantAreas() {
        outside();LoginUser actor=merchant();long checkedAt=System.nanoTime();
        List<DeliveryAreaOptionRespVO> result=AreaUtils.getChinaRegions().stream().map(area -> {
            DeliveryAreaOptionRespVO option=new DeliveryAreaOptionRespVO();option.setId(area.getId());option.setName(area.getName());option.setType(area.getType());
            option.setParentId(area.getParent()!=null && area.getParent().getType()>=2?area.getParent().getId():0);return option;
        }).collect(Collectors.toList());fresh(actor,false,checkedAt);return result;
    }
    public AfterSaleReturnAddress memberReturnAddress(HttpServletRequest request,String store,Long id){positive(id);return address(member(request,store),true,id);}
    private AfterSaleReturnAddress address(LoginUser actor,boolean member,Long id) {
        long checkedAt=System.nanoTime();return read.execute(status -> {
            AfterSaleDO row=required(actor,member,id);
            if(row.getWay()!=20 || row.getReturnAddress()==null || member&&(row.getStatus()!=20 || row.getReturnDeadline()==null || !now().isBefore(row.getReturnDeadline())))
                throw exception0(409,"当前没有可用于寄回的地址，请查询原申请进度");
            AfterSaleReturnAddress value=JsonUtils.parseObject(row.getReturnAddress(),AfterSaleReturnAddress.class);
            if(value==null || !validator.validate(value).isEmpty() || !AreaUtils.isChinaRegion(value.getAreaId()))throw exception0(503,"原寄回地址不完整");
            fresh(actor,member,checkedAt);return value;
        });
    }
    @PreAuthorize("@ss.hasPermission('trade:after-sale:query')")
    public AfterSaleDecisionResult merchantDecisionResult(String requestId){outside();requestId(requestId);return receipt(merchant(),false,requestId);}
    public AfterSaleDecisionResult memberDecisionResult(HttpServletRequest request,String store,String requestId){requestId(requestId);return receipt(member(request,store),true,requestId);}
    private AfterSaleDecisionResult receipt(LoginUser actor,boolean member,String requestId) {
        long checkedAt=System.nanoTime();return read.execute(status -> {
            AfterSaleLogDO row=queries.receipt(actor.getTenantId(),actor.getId(),member?"MEMBER":"ADMIN",requestId);
            fresh(actor,member,checkedAt);return row==null?null:new AfterSaleDecisionResult(row.getAfterSaleId(),row.getToStatus(),row.getAfterSaleVersion());
        });
    }
    /** 查询当前申请进度；未找到不能证明没有在途提交，也不能据此换新请求号重试。 */
    public AfterSaleSummaryRespVO memberApplicationResult(HttpServletRequest request,String store,String requestId) {
        requestId(requestId);LoginUser actor=member(request,store);long checkedAt=System.nanoTime();
        return read.execute(status -> {
            AfterSaleDO row=queries.application(actor.getTenantId(),actor.getId(),requestId);
            AfterSaleSummaryRespVO value=row==null?null:summaries(actor,Collections.singletonList(row),now()).get(0);
            fresh(actor,true,checkedAt);return value;
        });
    }
    public List<TradeDeliveryExpressRespVO> memberCarriers(HttpServletRequest request,String store) {
        LoginUser actor=member(request,store);long checkedAt=System.nanoTime();return read.execute(status -> {
            List<DeliveryExpressDO> rows=carriers.enabled();if(rows.size()>200)throw exception0(503,"承运商选项超出范围");
            List<TradeDeliveryExpressRespVO> result=BeanUtils.toBean(rows,TradeDeliveryExpressRespVO.class);fresh(actor,true,checkedAt);return result;
        });
    }

    private List<AfterSaleSummaryRespVO> summaries(LoginUser actor,List<AfterSaleDO> rows,LocalDateTime instant) {
        if(rows.isEmpty())return Collections.emptyList();if(rows.size()>20)throw new IllegalStateException("售后查询批次过大");
        List<Long> itemIds=rows.stream().map(AfterSaleDO::getOrderItemId).distinct().collect(Collectors.toList());
        List<Long> orderIds=rows.stream().map(AfterSaleDO::getOrderId).distinct().collect(Collectors.toList());
        Map<Long,TradeOrderItemDO> items=queries.items(actor.getTenantId(),itemIds).stream().collect(Collectors.toMap(TradeOrderItemDO::getId,value -> value));
        Map<Long,TradeOrderDO> orders=queries.orders(actor.getTenantId(),orderIds).stream().collect(Collectors.toMap(TradeOrderDO::getId,value -> value));
        List<AfterSaleSummaryRespVO> result=new ArrayList<>();
        for(AfterSaleDO row:rows) {
            TradeOrderItemDO item=items.get(row.getOrderItemId());TradeOrderDO order=orders.get(row.getOrderId());
            if(!Objects.equals(row.getTenantId(),actor.getTenantId()) || item==null || order==null || !Objects.equals(item.getOrderId(),row.getOrderId())
                    || !Objects.equals(item.getUserId(),row.getUserId()) || !Objects.equals(order.getUserId(),row.getUserId())
                    || !Objects.equals(item.getTenantId(),actor.getTenantId()) || !Objects.equals(order.getTenantId(),actor.getTenantId()))
                throw exception0(503,"原售后成交归属不完整");
            AfterSaleSummaryRespVO value=new AfterSaleSummaryRespVO();
            value.setId(row.getId());value.setNo(row.getNo());value.setOrderId(row.getOrderId());value.setOrderItemId(row.getOrderItemId());
            value.setCount(row.getCount());value.setWay(row.getWay());value.setStatus(row.getStatus());value.setAuthVersion(row.getAuthVersion());value.setGoodsRefundPrice(row.getGoodsRefundPrice());
            value.setOrderNo(order.getNo());value.setSkuId(item.getSkuId());value.setSpuName(item.getSpuName());value.setPicUrl(item.getPicUrl());
            value.setCreatedAt(epoch(row.getCreateTime()));value.setUpdatedAt(epoch(row.getUpdateTime()));value.setReturnDeadline(epoch(row.getReturnDeadline()));value.setServerNow(epoch(instant));
            LocalDateTime since=row.getStatus()==10?row.getCreateTime():row.getStatus()==30?row.getDeliveryTime():row.getStatus()==35?row.getReceiveTime():null;
            value.setOverdue(since!=null && !instant.isBefore(since.plusHours(48)));result.add(value);
        }
        return result;
    }
    private AfterSaleLogRespVO log(AfterSaleLogDO row) {
        AfterSaleLogRespVO value=BeanUtils.toBean(row,AfterSaleLogRespVO.class);value.setCreatedAt(epoch(row.getCreateTime()));
        if("ADMIN_RECEIVE".equals(row.getAction()) || "ADMIN_RECEIVE_DISPUTE".equals(row.getAction())) {
            AfterSaleLogRespVO.Inspection facts=JsonUtils.parseObject(row.getDetails(),AfterSaleLogRespVO.Inspection.class);
            if(facts==null || facts.getReceivedCount()==null || facts.getReceivedCount()<0 || facts.getReceivedCount()>9999
                    || facts.getSalableCount()==null || facts.getSalableCount()<0 || facts.getSalableCount()>facts.getReceivedCount()
                    || !Objects.equals(facts.getUnsalableCount(),facts.getReceivedCount()-facts.getSalableCount())
                    || !Objects.equals(facts.getAccepted(),"ADMIN_RECEIVE".equals(row.getAction())))throw exception0(503,"原验货记录不完整");
            value.setInspection(facts);
        }
        return value;
    }
    private AfterSaleDO required(LoginUser actor,boolean member,Long id) {
        AfterSaleDO row=queries.get(actor.getTenantId(),member?actor.getId():null,id);if(row==null)throw exception0(404,"本入口售后不存在");return row;
    }
    private LoginUser member(HttpServletRequest request,String store){outside();members.check(request,store);return members.actor();}
    private LoginUser merchant() {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getTenantId()==null || actor.getTenantId()<=0 || actor.getTenantId()>9_007_199_254_740_991L
                || actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || !Objects.equals(TenantContextHolder.getTenantId(),actor.getTenantId()))throw exception0(403,"仅当前商家员工可以查询售后");return actor;
    }
    private void fresh(LoginUser actor,boolean member,long checkedAt) {
        LoginUser current=member?members.actor():merchant();
        if(!Objects.equals(actor.getId(),current.getId()) || !Objects.equals(actor.getTenantId(),current.getTenantId()) || System.nanoTime()-checkedAt>5_000_000_000L)
            throw exception0(503,"身份核对已过时，请重新查询");
    }
    private long offset(PageParam body) {
        if(body==null || !validator.validate(body).isEmpty() || body.getPageSize()>20)throw exception0(400,"售后分页参数不正确");
        long offset=((long)body.getPageNo()-1)*body.getPageSize();if(offset>100_000)throw exception0(400,"分页范围过大，请按编号或状态查询");return offset;
    }
    private void filter(AfterSalePageReqVO body) {
        if(body.getStatus()!=null && !Arrays.asList(10,20,30,35,40,45,50,61,62,64).contains(body.getStatus())
                || body.getWay()!=null && !Arrays.asList(10,20).contains(body.getWay()))throw exception0(400,"售后筛选条件不正确");
    }
    private void requestId(String value){if(value==null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw exception0(400,"原请求号不正确");}
    private void positive(Long value){if(value==null || value<=0 || value>9_007_199_254_740_991L)throw exception0(400,"售后编号不正确");}
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("售后查询入口不能在外层事务内");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
    private Long epoch(LocalDateTime value){return value==null?null:value.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
}
