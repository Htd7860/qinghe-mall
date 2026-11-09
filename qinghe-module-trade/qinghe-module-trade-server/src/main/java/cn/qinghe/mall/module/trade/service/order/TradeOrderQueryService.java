package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.area.Area;
import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.trade.controller.app.order.vo.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.*;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Validator;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原本人订单查询/批量商品项/转换职责。GET只读，不夹带查渠道/付款/取消副作用。
 * @author 芋道源码
 */
@Service
public class TradeOrderQueryService {
    private final TradeMemberAccess access;
    private final TradeOrderMapper orders;
    private final TradeOrderPrepareMapper preparations;
    private final Validator validator;
    private final TransactionTemplate read;
    public TradeOrderQueryService(TradeMemberAccess access,TradeOrderMapper orders,TradeOrderPrepareMapper preparations,
            Validator validator,PlatformTransactionManager manager) {
        this.access=access;this.orders=orders;this.preparations=preparations;this.validator=validator;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
    }
    public PageResult<AppTradeOrderPageItemRespVO> page(HttpServletRequest request,String store,AppTradeOrderPageReqVO body) {
        if(body==null || !validator.validate(body).isEmpty() || body.getPageSize()>20
                || body.getStatus()!=null && !Arrays.asList(0,10,20,30,40).contains(body.getStatus()))throw exception0(400,"订单分页参数不正确");
        long offset=((long)body.getPageNo()-1)*body.getPageSize();if(offset>100_000)throw exception0(400,"分页范围过大，请按状态查询");
        access.check(request,store);LoginUser actor=access.actor();
        return read.execute(status -> {
            long total=orders.memberCount(actor.getTenantId(),actor.getId(),body.getStatus());
            List<TradeOrderDO> rows=orders.memberPage(actor.getTenantId(),actor.getId(),body.getStatus(),body.getPageSize(),offset);
            if(rows.isEmpty())return PageResult.empty(total);
            Map<Long,List<TradeOrderItemDO>> items=items(actor,rows.stream().map(TradeOrderDO::getId).collect(Collectors.toList()));
            long instant=epoch(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            List<AppTradeOrderPageItemRespVO> list=rows.stream().map(row -> summary(row,items.get(row.getId()),instant)).collect(Collectors.toList());
            access.actor();return new PageResult<>(total,list);
        });
    }
    public AppTradeOrderDetailRespVO detail(HttpServletRequest request,String store,Long id) {
        positive(id);access.check(request,store);LoginUser actor=access.actor();
        return read.execute(status -> {
            TradeOrderDO row=orders.owned(actor.getTenantId(),actor.getId(),id);
            if(row==null)throw exception0(404,"本人订单不存在");
            List<TradeOrderItemDO> lines=items(actor,Collections.singletonList(id)).get(id);
            AppTradeOrderDetailRespVO result=BeanUtils.toBean(summary(row,lines,epoch(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))),AppTradeOrderDetailRespVO.class);
            result.setItems(lines.stream().map(item -> line(item,true)).collect(Collectors.toList()));
            result.setUserRemark(row.getUserRemark());result.setReceiverName(row.getReceiverName());result.setReceiverMobile(row.getReceiverMobile());
            result.setReceiverAreaId(row.getReceiverAreaId());result.setReceiverAreaName(areaName(row.getReceiverAreaId()));
            result.setReceiverDetailAddress(row.getReceiverDetailAddress());access.actor();return result;
        });
    }
    public AppTradeOrderCreateRespVO creationResult(HttpServletRequest request,String store,String requestId) {
        access.check(request,store);return creationResultAfterCommand(requestId);
    }
    /** 同包已核对Host/会员的付款入口复用，只读本人主单与原项。 */
    AppTradeOrderPageItemRespVO orderAfterCommand(Long id) {
        positive(id);LoginUser actor=access.actor();
        return read.execute(status -> {
            TradeOrderDO row=orders.owned(actor.getTenantId(),actor.getId(),id);
            if(row==null)throw exception0(404,"本人订单不存在");
            AppTradeOrderPageItemRespVO result=summary(row,items(actor,Collections.singletonList(id)).get(id),epoch(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))));
            access.actor();return result;
        });
    }
    /** 仅同包已完成实际Host/会员写入口校验的编排调用，避免成功返回前重复一次远程入口读取。 */
    AppTradeOrderCreateRespVO creationResultAfterCommand(String requestId) {
        if(requestId==null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw exception0(400,"创建请求号不正确");
        LoginUser actor=access.actor();
        return read.execute(status -> {
            TradeOrderPrepareDO preparation=preparations.find(actor.getTenantId(),actor.getId(),requestId);
            if(preparation==null)return null; // 未查到不是没有在途创建的证明。
            AppTradeOrderCreateRespVO result=new AppTradeOrderCreateRespVO();result.setPreparationId(preparation.getId());result.setRequestId(requestId);
            result.setQuoteId(preparation.getQuoteId());
            result.setStage(preparation.getStage());result.setServerNow(epoch(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))));
            if("CREATED".equals(preparation.getStage())) {
                TradeOrderDO row=orders.owned(actor.getTenantId(),actor.getId(),preparation.getId());
                if(row==null)throw exception0(503,"原创建结果不完整，请查询原请求");
                result.setOrder(summary(row,items(actor,Collections.singletonList(row.getId())).get(row.getId()),result.getServerNow()));
            }
            access.actor();return result;
        });
    }
    private Map<Long,List<TradeOrderItemDO>> items(LoginUser actor,List<Long> ids) {
        if(ids.isEmpty() || ids.size()>20)throw new IllegalStateException("订单项批次不正确");
        List<TradeOrderItemDO> rows=orders.memberItems(actor.getTenantId(),actor.getId(),ids);
        if(rows.size()>4000)throw exception0(503,"历史订单商品快照超出范围");
        return rows.stream().collect(Collectors.groupingBy(TradeOrderItemDO::getOrderId));
    }
    private AppTradeOrderPageItemRespVO summary(TradeOrderDO row,List<TradeOrderItemDO> items,long instant) {
        if(items==null || items.isEmpty() || items.size()>200
                || items.stream().mapToLong(TradeOrderItemDO::getCount).sum()!=row.getProductCount()
                || items.stream().mapToLong(TradeOrderItemDO::getPayPrice).sum()!=row.getPayPrice())throw exception0(503,"原订单成交明细不完整");
        AppTradeOrderPageItemRespVO result=new AppTradeOrderPageItemRespVO();
        result.setId(row.getId());result.setNo(row.getNo());result.setStatus(row.getStatus());result.setPayStatus(row.getPayStatus());result.setPayOrderId(row.getPayOrderId());
        result.setProductCount(row.getProductCount());result.setItemCount(items.size());result.setTotalPrice(row.getTotalPrice());result.setDiscountPrice(row.getDiscountPrice());
        result.setDeliveryPrice(row.getDeliveryPrice());result.setPayPrice(row.getPayPrice());result.setRefundPrice(row.getRefundPrice());result.setCancelType(row.getCancelType());
        result.setCancelResourcesReleased(row.getCancelResourcesReleased());result.setAuthVersion(row.getAuthVersion());result.setServerNow(instant);
        result.setCreatedAt(epoch(row.getCreateTime()));result.setPayDeadline(epoch(row.getPayDeadline()));result.setPaidAt(epoch(row.getPayTime()));result.setCancelledAt(epoch(row.getCancelTime()));
        result.setLogisticsId(row.getLogisticsId());result.setLogisticsName(row.getLogisticsName());result.setLogisticsNo(row.getLogisticsNo());
        result.setDeliveredAt(epoch(row.getDeliveryTime()));result.setReceivedAt(epoch(row.getReceiveTime()));
        result.setAutoReceiveAt(epoch(row.getDeliveryTime()==null?null:row.getDeliveryTime().plusDays(14)));result.setAfterSalePendingCount(row.getAfterSalePendingCount());
        result.setRequiresPayment(row.getStatus()==0 && !Boolean.TRUE.equals(row.getPayStatus()) && row.getPayPrice()>0);
        result.setItems(items.stream().limit(3).map(item -> line(item,false)).collect(Collectors.toList()));return result;
    }
    private AppTradeOrderItemRespVO line(TradeOrderItemDO row,boolean full) {
        // properties在DO中为JSON，不能交给通用Bean复制器把String猜成List。
        AppTradeOrderItemRespVO result=new AppTradeOrderItemRespVO();result.setId(row.getId());result.setSpuId(row.getSpuId());result.setSkuId(row.getSkuId());
        result.setSpuName(row.getSpuName());result.setPicUrl(row.getPicUrl());result.setCount(row.getCount());result.setPrice(row.getPrice());
        result.setRefundCount(row.getRefundCount());result.setDeliveryCount(row.getDeliveryCount());
        result.setGoodsPrice(row.getGoodsPrice());result.setDiscountPrice(row.getDiscountPrice());result.setDeliveryPrice(row.getDeliveryPrice());result.setPayPrice(row.getPayPrice());
        if(full) {
            ProductTradeSkuRespDTO.Property[] properties=JsonUtils.parseObject(row.getProperties(),ProductTradeSkuRespDTO.Property[].class);
            if(properties==null)throw exception0(503,"原商品规格快照不完整");result.setProperties(Arrays.asList(properties));
        }
        return result;
    }
    private String areaName(Integer id) {
        List<String> names=new ArrayList<>();Set<Integer> seen=new HashSet<>();Area area=AreaUtils.getArea(id);
        while(area!=null && AreaUtils.isChinaRegion(area.getId()) && seen.add(area.getId())){names.add(0,area.getName());area=area.getParent();}
        return String.join(" / ",names);
    }
    private Long epoch(LocalDateTime value){return value==null?null:value.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
    private void positive(Long id){if(id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(400,"订单编号不正确");}
}
