package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.area.Area;
import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderAdminMapper;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOrderDeliveryLogDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原租户订单分页/原项组合/日志职责。不读取当前会员资料来覆盖成交快照。
 * 方法权限在服务代理上检查一次，远程权限读取发生在本地只读事务之前。
 * @author 芋道源码
 */
@Service
public class TradeOrderAdminQueryService {
    private final TradeOrderAdminMapper orders;
    private final Validator validator;
    private final TransactionTemplate read;
    public TradeOrderAdminQueryService(TradeOrderAdminMapper orders,Validator validator,PlatformTransactionManager manager) {
        this.orders=orders;this.validator=validator;
        read=new TransactionTemplate(manager);read.setReadOnly(true);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
    }

    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public PageResult<TradeOrderPageItemRespVO> page(TradeOrderPageReqVO request) {
        LoginUser actor=actor();long checkedAt=System.nanoTime(),offset=offset(request);
        if(request.getStatus()!=null && !Arrays.asList(0,10,20,30,40).contains(request.getStatus()))throw exception0(400,"订单状态筛选不正确");
        return read.execute(status -> {
            long total=orders.count(actor.getTenantId(),request);
            List<TradeOrderDO> rows=orders.page(actor.getTenantId(),request,offset);
            Map<Long,List<TradeOrderItemDO>> items=items(actor,rows.stream().map(TradeOrderDO::getId).collect(Collectors.toList()));
            long instant=epoch(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            List<TradeOrderPageItemRespVO> result=rows.stream().map(row -> summary(row,lines(actor,row,items.get(row.getId())),instant,false)).collect(Collectors.toList());
            fresh(actor,checkedAt);return new PageResult<>(total,result);
        });
    }

    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public TradeOrderDetailRespVO detail(Long id) {
        LoginUser actor=actor();positive(id);long checkedAt=System.nanoTime();
        return read.execute(status -> {
            TradeOrderDO row=required(actor.getTenantId(),id);
            List<TradeOrderItemDO> lines=lines(actor,row,items(actor,Collections.singletonList(id)).get(id));
            TradeOrderDetailRespVO result=BeanUtils.toBean(summary(row,lines,epoch(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))),true),TradeOrderDetailRespVO.class);
            result.setReceiverNameMasked(maskName(row.getReceiverName()));
            result.setReceiverMobileMasked(maskMobile(row.getReceiverMobile()));result.setReceiverAreaName(areaName(row.getReceiverAreaId()));
            fresh(actor,checkedAt);return result;
        });
    }

    @PreAuthorize("@ss.hasPermission('trade:order:delivery')")
    public TradeOrderDeliveryDetailRespVO deliveryDetail(Long id) {
        LoginUser actor=actor();positive(id);long checkedAt=System.nanoTime();
        return read.execute(status -> {
            TradeOrderDO row=orders.deliveryDetail(actor.getTenantId(),id);
            if(row==null)throw exception0(404,"当前没有可读取的本租户待发货订单");
            List<TradeOrderItemDO> lines=lines(actor,row,items(actor,Collections.singletonList(id)).get(id));
            if(lines.stream().mapToInt(line -> line.getCount()-line.getRefundCount()).sum()<=0)throw exception0(409,"原订单已无可履约商品");
            TradeOrderDeliveryDetailRespVO result=new TradeOrderDeliveryDetailRespVO();
            result.setId(row.getId());result.setNo(row.getNo());result.setAuthVersion(row.getAuthVersion());
            result.setReceiverName(row.getReceiverName());result.setReceiverMobile(row.getReceiverMobile());result.setReceiverAreaId(row.getReceiverAreaId());
            result.setReceiverAreaName(areaName(row.getReceiverAreaId()));result.setReceiverDetailAddress(row.getReceiverDetailAddress());result.setUserRemark(row.getUserRemark());
            result.setItems(lines.stream().map(line -> {
                TradeOrderDeliveryDetailRespVO.Item item=new TradeOrderDeliveryDetailRespVO.Item();
                item.setId(line.getId());item.setSkuId(line.getSkuId());item.setSpuName(line.getSpuName());item.setCount(line.getCount());
                item.setRefundCount(line.getRefundCount());item.setDeliveryCount(line.getDeliveryCount());item.setProperties(properties(line));return item;
            }).collect(Collectors.toList()));
            fresh(actor,checkedAt);return result;
        });
    }

    @PreAuthorize("@ss.hasPermission('trade:order:query')")
    public PageResult<TradeOrderLogRespVO> logs(Long id,PageParam request) {
        LoginUser actor=actor();positive(id);long checkedAt=System.nanoTime(),offset=offset(request);
        return read.execute(status -> {
            required(actor.getTenantId(),id);
            long total=orders.logCount(actor.getTenantId(),id);
            List<TradeOrderLogRespVO> result=orders.logs(actor.getTenantId(),id,request.getPageSize(),offset).stream().map(row -> {
                TradeOrderLogRespVO log=new TradeOrderLogRespVO();log.setId(row.getId());log.setActorId(row.getActorId());log.setActorType(row.getActorType());
                log.setAction(row.getAction());log.setFromStatus(row.getFromStatus());log.setToStatus(row.getToStatus());log.setOrderVersion(row.getOrderVersion());
                log.setCreatedAt(epoch(row.getCreateTime()));
                if(row.getDetails()!=null)log.setDetails(JsonUtils.parseObject(row.getDetails(),TradeOrderDeliveryLogDTO.class));return log;
            }).collect(Collectors.toList());
            fresh(actor,checkedAt);return new PageResult<>(total,result);
        });
    }

    private TradeOrderPageItemRespVO summary(TradeOrderDO row,List<TradeOrderItemDO> lines,long instant,boolean full) {
        TradeOrderPageItemRespVO result=BeanUtils.toBean(row,TradeOrderPageItemRespVO.class);
        result.setItemCount(lines.size());result.setCreatedAt(epoch(row.getCreateTime()));result.setPaidAt(epoch(row.getPayTime()));
        result.setDeliveredAt(epoch(row.getDeliveryTime()));result.setReceivedAt(epoch(row.getReceiveTime()));result.setCancelledAt(epoch(row.getCancelTime()));result.setServerNow(instant);
        result.setItems(lines.stream().limit(full?200:3).map(this::line).collect(Collectors.toList()));return result;
    }
    private TradeOrderItemRespVO line(TradeOrderItemDO row) {
        TradeOrderItemRespVO result=new TradeOrderItemRespVO();
        result.setId(row.getId());result.setSpuId(row.getSpuId());result.setSkuId(row.getSkuId());result.setSpuName(row.getSpuName());result.setPicUrl(row.getPicUrl());
        result.setCount(row.getCount());result.setRefundCount(row.getRefundCount());result.setDeliveryCount(row.getDeliveryCount());result.setPrice(row.getPrice());
        result.setGoodsPrice(row.getGoodsPrice());result.setDiscountPrice(row.getDiscountPrice());result.setDeliveryPrice(row.getDeliveryPrice());result.setPayPrice(row.getPayPrice());
        result.setProperties(properties(row));return result;
    }
    private List<ProductTradeSkuRespDTO.Property> properties(TradeOrderItemDO row) {
        ProductTradeSkuRespDTO.Property[] values=JsonUtils.parseObject(row.getProperties(),ProductTradeSkuRespDTO.Property[].class);
        if(values==null)throw exception0(503,"原商品规格快照不完整");return Arrays.asList(values);
    }
    private Map<Long,List<TradeOrderItemDO>> items(LoginUser actor,List<Long> ids) {
        if(ids.isEmpty())return Collections.emptyMap();
        if(ids.size()>20)throw new IllegalStateException("订单读取批次超出范围");
        List<TradeOrderItemDO> rows=orders.items(actor.getTenantId(),ids);
        if(rows.size()>4000)throw exception0(503,"订单商品快照超出范围");return rows.stream().collect(Collectors.groupingBy(TradeOrderItemDO::getOrderId));
    }
    private List<TradeOrderItemDO> lines(LoginUser actor,TradeOrderDO row,List<TradeOrderItemDO> lines) {
        if(!Objects.equals(actor.getTenantId(),row.getTenantId()) || lines==null || lines.isEmpty() || lines.size()>200)throw exception0(503,"原订单商品快照不完整");
        long quantity=0,goods=0,discount=0,delivery=0,pay=0;
        for(TradeOrderItemDO item:lines) {
            if(!Objects.equals(item.getTenantId(),row.getTenantId()) || !Objects.equals(item.getOrderId(),row.getId()) || !Objects.equals(item.getUserId(),row.getUserId())
                    || item.getCount()==null || item.getCount()<1 || item.getRefundCount()==null || item.getRefundCount()<0 || item.getRefundCount()>item.getCount()
                    || item.getDeliveryCount()==null || item.getDeliveryCount()<0 || item.getDeliveryCount()>item.getCount())throw exception0(503,"原订单归属或商品数量不完整");
            quantity=Math.addExact(quantity,item.getCount());goods=Math.addExact(goods,item.getGoodsPrice());discount=Math.addExact(discount,item.getDiscountPrice());
            delivery=Math.addExact(delivery,item.getDeliveryPrice());pay=Math.addExact(pay,item.getPayPrice());
        }
        if(quantity!=row.getProductCount() || goods!=row.getTotalPrice() || discount!=row.getDiscountPrice() || delivery!=row.getDeliveryPrice() || pay!=row.getPayPrice())
            throw exception0(503,"原订单成交明细不一致");return lines;
    }
    private TradeOrderDO required(Long tenant,Long id) {
        TradeOrderDO row=orders.detail(tenant,id);if(row==null)throw exception0(404,"本租户订单不存在");return row;
    }
    private LoginUser actor() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("订单查询入口不能在外层事务内");
        return current();
    }
    private LoginUser current() {
        LoginUser user=SecurityFrameworkUtils.getLoginUser();
        if(user==null || user.getExpiresTime()==null || !user.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(user.getUserType()) || user.getTenantId()==null || user.getTenantId()<=0 || user.getTenantId()>9_007_199_254_740_991L
                || user.getId()==null || user.getId()<=0 || user.getId()>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || !Objects.equals(user.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"仅当前商家员工可以查询订单");return user;
    }
    private void fresh(LoginUser actor,long checkedAt) {
        LoginUser user=current();
        if(!Objects.equals(user.getId(),actor.getId()) || !Objects.equals(user.getTenantId(),actor.getTenantId()) || System.nanoTime()-checkedAt>5_000_000_000L)
            throw exception0(503,"身份核对已过时，请重新查询");
    }
    private long offset(PageParam request) {
        if(request==null || !validator.validate(request).isEmpty() || request.getPageSize()>20)throw exception0(400,"订单分页参数不正确");
        long offset=((long)request.getPageNo()-1)*request.getPageSize();if(offset>100_000)throw exception0(400,"分页范围过大，请按原编号或状态查询");return offset;
    }
    private String maskName(String value){if(value==null || value.isEmpty())return "***";return new String(Character.toChars(value.codePointAt(0)))+"**";}
    private String maskMobile(String value){return value==null || value.length()<=4?"***":"***"+value.substring(value.length()-4);}
    private String areaName(Integer id) {
        List<String> names=new ArrayList<>();Set<Integer> seen=new HashSet<>();Area area=AreaUtils.getArea(id);
        while(area!=null && AreaUtils.isChinaRegion(area.getId()) && seen.add(area.getId())){names.add(0,area.getName());area=area.getParent();}return String.join(" / ",names);
    }
    private Long epoch(LocalDateTime value){return value==null?null:value.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
    private void positive(Long id){if(id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(400,"订单编号不正确");}
}
