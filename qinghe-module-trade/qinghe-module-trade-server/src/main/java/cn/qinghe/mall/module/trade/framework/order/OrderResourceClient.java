package cn.qinghe.mall.module.trade.framework.order;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.stock.dto.*;
import cn.qinghe.mall.module.promotion.api.coupon.dto.*;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillResourceLookupDTO;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillPaymentEligibilityDTO;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillRefundResourceLookupDTO;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillRefundResourceReceiptDTO;
import cn.qinghe.mall.module.pay.api.order.dto.*;
import cn.qinghe.mall.module.pay.api.refund.dto.*;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.AfterSaleRefundDO;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.AfterSaleDecisionMapper;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.AfterSaleRefundMapper;
import cn.qinghe.mall.module.pay.enums.order.PayOrderStatusEnum;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderPrepareItemDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderPrepareMapper;
import cn.qinghe.mall.module.trade.dal.mysql.seckill.TradeSeckillOrderBindingMapper;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillResultReadService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import cn.qinghe.mall.module.trade.service.price.bo.TradeSettlementSnapshotBO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原 trade 库存/券/支付 API 调用职责。参数仅从本租户持久准备/主订单构造，不接受自由数量/金额/会员。
 * 创建失败补偿只读取不可逆COMPENSATING记录；RPC在本地事务外，不自动换号重试。
 */
@Service
public class OrderResourceClient {
    private final TradeOrderPrepareMapper preparations;
    private final OrderStockFeignApi stocks;
    private final OrderCouponFeignApi coupons;
    private final OrderPayFeignApi payments;
    private final AfterSaleRefundMapper refundPlans;
    private final AfterSaleDecisionMapper afterSales;
    private final TradeOrderMapper orders;
    private final TradeSeckillOrderBindingMapper seckillBindings;
    private final boolean seckillPaymentsEnabled;
    private final OrderResourceProperties properties;
    private final TransactionTemplate read;
    private final Semaphore calls = new Semaphore(16);

    public OrderResourceClient(TradeOrderPrepareMapper preparations, OrderStockFeignApi stocks,
            OrderCouponFeignApi coupons, OrderPayFeignApi payments, TradeOrderMapper orders, OrderResourceProperties properties, PlatformTransactionManager manager,
            AfterSaleRefundMapper refundPlans, AfterSaleDecisionMapper afterSales,TradeSeckillOrderBindingMapper seckillBindings,
            @Value("${qinghe.trade.product-base-url:http://127.0.0.1:8082}") String productOrigin,
            @Value("${qinghe.trade.promotion-base-url:http://127.0.0.1:8085}") String promotionOrigin,
            @Value("${qinghe.trade.pay-base-url:http://127.0.0.1:8086}") String payOrigin,
            @Value("${qinghe.trade.seckill-orders.enabled:false}") boolean seckillPaymentsEnabled) {
        origin(productOrigin);
        origin(promotionOrigin);
        origin(payOrigin);
        tokenFormat(properties.getProductToken());
        tokenFormat(properties.getPromotionToken());
        tokenFormat(properties.getPayToken());
        Set<String> configured=new HashSet<>();
        for(String token:Arrays.asList(properties.getProductToken(),properties.getPromotionToken(),properties.getPayToken()))
            if(!token.isEmpty() && !configured.add(token))throw new IllegalArgumentException("订单资源接收方必须配置独立服务凭据");
        this.preparations = preparations;
        this.stocks = stocks;
        this.coupons = coupons;
        this.payments = payments;
        this.refundPlans = refundPlans;
        this.afterSales = afterSales;
        this.orders = orders;
        this.seckillBindings=seckillBindings;
        this.seckillPaymentsEnabled=seckillPaymentsEnabled;
        this.properties = properties;
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(5);
    }

    public ProductOrderStockRespDTO deductStock(Long preparationId) {
        Context context = load(preparationId, true);
        ProductOrderStockReqDTO request = new ProductOrderStockReqDTO();
        request.setOrderId(context.order.getId());
        request.setUserId(context.order.getUserId());
        List<ProductOrderStockReqDTO.Item> items = new ArrayList<>();
        for (TradeOrderPrepareItemDO line : context.items) {
            ProductOrderStockReqDTO.Item item = new ProductOrderStockReqDTO.Item();
            item.setOrderItemId(line.getId());
            item.setSpuId(line.getSpuId());
            item.setSkuId(line.getSkuId());
            item.setQuantity(line.getCount());
            item.setUnitPrice(line.getPrice());
            item.setSpuVersion(line.getSpuVersion());
            item.setSkuVersion(line.getSkuVersion());
            items.add(item);
        }
        request.setItems(items);
        ProductOrderStockRespDTO result = call(() -> stocks.deduct(token(properties.getProductToken()), context.tenant, request));
        validateStock(context, result);
        if (!Integer.valueOf(ProductOrderStockRespDTO.DEDUCTED).equals(result.getStatus())) {
            throw exception0(409, "原订单库存已关闭或回补，不能继续创建");
        }
        return result;
    }

    public ProductOrderStockRespDTO stockResult(Long preparationId) {
        Context context = load(preparationId, false);
        ProductOrderStockResultReqDTO request = new ProductOrderStockResultReqDTO();
        request.setOrderId(context.order.getId());
        request.setUserId(context.order.getUserId());
        ProductOrderStockRespDTO result = call(() -> stocks.result(token(properties.getProductToken()), context.tenant, request));
        if (result != null) validateStock(context, result);
        return result;
    }

    /** 未选券返回 null；这是持久订单事实，不是远程失败的降级。 */
    public CouponOrderResultDTO reserveCoupon(Long preparationId) {
        Context context = load(preparationId, true);
        if (context.snapshot.getCoupon() == null) return null;
        CouponOrderReserveReqDTO request = new CouponOrderReserveReqDTO();
        request.setOrderId(context.order.getId());
        request.setUserId(context.order.getUserId());
        request.setCouponId(context.snapshot.getCoupon().getId());
        // 原报价按cartId稳定分摊；保留此行身份，不换成另一个顺序重新分优惠。
        request.setLines(BeanUtils.toBean(context.snapshot.getCouponQuote().getLines(), CouponOrderLineDTO.class));
        request.setExpectedQuote(context.snapshot.getCouponQuote());
        CouponOrderResultDTO result = call(() -> coupons.reserve(token(properties.getPromotionToken()), context.tenant, request));
        validateCoupon(context, result);
        if (result.getState() != 1 && result.getState() != 2) {
            throw exception0(409, "原订单优惠券已释放或退回，不能继续创建");
        }
        return result;
    }

    public CouponOrderResultDTO useCoupon(Long preparationId) {
        Context context = load(preparationId, true);
        if (context.snapshot.getCoupon() == null) return null;
        CouponOrderResultDTO result = call(() -> coupons.use(token(properties.getPromotionToken()), context.tenant, couponCommand(context)));
        validateCoupon(context, result);
        if (result.getState() != 2) throw exception0(409, "原订单优惠券未核销");
        return result;
    }

    /** null 表示本单未选券或暂未查到已提交结果，调用方须结合原准备快照判断，不能当失败证明。 */
    public CouponOrderResultDTO couponResult(Long preparationId) {
        Context context = load(preparationId, false);
        if (context.snapshot.getCoupon() == null) return null;
        CouponOrderResultDTO result = call(() -> coupons.result(token(properties.getPromotionToken()), context.tenant, couponCommand(context)));
        if (result != null) validateCoupon(context, result);
        return result;
    }

    /** 原准备已不可逆取消且尚无可付款订单；关闭记录先到也能阻止晚到扣库存。 */
    public ProductOrderStockRespDTO closeCreationStock(Long preparationId) {
        Context context=loadCompensation(preparationId);
        ProductOrderStockResultReqDTO request=new ProductOrderStockResultReqDTO();
        request.setOrderId(context.order.getId());request.setUserId(context.order.getUserId());
        ProductOrderStockRespDTO result=call(() -> stocks.closeCreation(token(properties.getProductToken()),context.tenant,request));
        validateStock(context,result);
        if(!Arrays.asList(ProductOrderStockRespDTO.CLOSED,ProductOrderStockRespDTO.RELEASED).contains(result.getStatus())
                || !"CREATE_FAILED".equals(result.getReleaseReason()))throw exception0(503,"创建失败库存关闭结果待确认");
        return result;
    }

    public CouponOrderCloseResultDTO closeCreationCoupon(Long preparationId) {
        Context context=loadCompensation(preparationId);
        if(context.snapshot.getCoupon()==null)return null;
        CouponOrderCloseResultDTO result=call(() -> coupons.closeCreation(token(properties.getPromotionToken()),context.tenant,couponCommand(context)));
        if(result==null || !Objects.equals(context.order.getId(),result.getOrderId())
                || !Objects.equals(context.snapshot.getCoupon().getId(),result.getCouponId())
                || !Boolean.TRUE.equals(result.getClosed()) || !"CREATE_FAILED".equals(result.getReason())
                || result.getState()!=null && !Arrays.asList(3,4).contains(result.getState()))throw exception0(503,"创建失败优惠券关闭结果待确认");
        return result;
    }

    private Context loadCompensation(Long id) {
        Context context=load(id,false);
        // 并发恢复者可能已收敛FAILED；终态仍只允许幂等关闭，绝不允许重新扣减/占券。
        if(!Arrays.asList("COMPENSATING","FAILED").contains(context.order.getStage()))throw exception0(409,"仅已持久取消的创建流程可以释放资源");
        return context;
    }

    /** 正式订单已持久未付取消；数量与原商品项来自持久快照，不能复用创建失败理由。 */
    public ProductOrderStockRespDTO closeCancelledStock(Long orderId) {
        Context context=loadCancelled(orderId);
        ProductOrderStockResultReqDTO request=new ProductOrderStockResultReqDTO();
        request.setOrderId(orderId);request.setUserId(context.order.getUserId());
        ProductOrderStockRespDTO result=call(() -> stocks.closeUnpaid(token(properties.getProductToken()),context.tenant,request));
        validateStock(context,result);
        if(!Integer.valueOf(ProductOrderStockRespDTO.RELEASED).equals(result.getStatus())
                || !"UNPAID_CLOSED".equals(result.getReleaseReason()))throw exception0(503,"正式订单库存回补回执待确认");
        for(ProductOrderStockRespDTO.Item item:result.getItems()) {
            if(item.getReleaseBeforeStock()==null || item.getReleaseBeforeStock()<0 || item.getReleaseAfterStock()==null
                    || (long)item.getReleaseAfterStock()-item.getReleaseBeforeStock()!=item.getQuantity())
                throw exception0(503,"原订单库存回补明细待核查");
        }
        return result;
    }

    /** 未选券是持久订单事实；已选券必须查到原核销退回回执，不能把null当作完成。 */
    public CouponOrderResultDTO closeCancelledCoupon(Long orderId) {
        Context context=loadCancelled(orderId);
        if(context.snapshot.getCoupon()==null)return null;
        CouponOrderResultDTO result=call(() -> coupons.closeUnpaid(token(properties.getPromotionToken()),context.tenant,couponCommand(context)));
        validateCoupon(context,result);
        if(!Integer.valueOf(4).equals(result.getState()) || !"UNPAID_CLOSED".equals(result.getReturnReason()))
            throw exception0(503,"原订单优惠券退回回执待确认");
        return result;
    }

    private Context loadCancelled(Long id) {
        Context context=load(id,false);
        TradeOrderDO order=orders.paymentOrder(context.tenant,id);
        if(!"CREATED".equals(context.order.getStage()) || order==null
                || !Objects.equals(context.order.getUserId(),order.getUserId())
                || !Integer.valueOf(40).equals(order.getStatus()) || !Boolean.FALSE.equals(order.getPayStatus())
                || order.getCancelTime()==null || !Arrays.asList(10,30).contains(order.getCancelType())
                || !Objects.equals(context.snapshot.getPayPrice(),order.getPayPrice())
                || !Objects.equals(context.snapshot.getCoupon()==null?null:context.snapshot.getCoupon().getId(),order.getCouponId()))
            throw exception0(409,"仅已持久取消的未付正式订单可以清理资源");
        return context;
    }

    /** 创建支付账只接受已CREATED的正式订单；不能拿准备报价建立付款入口。 */
    public PayOrderRespDTO ensurePayment(Long orderId) {
        TradeOrderDO order=paymentOrder(orderId);
        requireSeckillPaymentEligibility(order);
        if(order.getPayPrice()==null || order.getPayPrice()<=0)throw exception0(409,"零元订单不创建渠道支付单");
        PayOrderRespDTO result=call(() -> payments.create(token(properties.getPayToken()),order.getTenantId(),paymentRequest(order)));
        validatePayment(order,result);return result;
    }

    /** 关闭先到也必须持久记录，null/过期/查询未付都不是关单回执。仅可信取消流程调用。 */
    public PayOrderRespDTO closePayment(Long orderId) {
        TradeOrderDO order=paymentOrder(orderId);
        return closePayment(order);
    }
    /** 单独的后台入口只开放原秒杀支付关闭，不开放支付创建或二维码提交。 */
    public PayOrderRespDTO closeSeckillPayment(Long orderId) {
        return closePayment(seckillPaymentOrder(orderId));
    }
    public PayOrderRespDTO seckillPaymentResult(Long orderId) {
        return paymentResult(seckillPaymentOrder(orderId));
    }
    public PayOrderRespDTO synchronizeSeckillPayment(Long orderId) {
        return synchronizePayment(seckillPaymentOrder(orderId));
    }
    private PayOrderRespDTO closePayment(TradeOrderDO order) {
        if(order.getPayPrice()==null || order.getPayPrice()<=0)throw exception0(409,"零元订单不使用支付关闭接口");
        PayOrderRespDTO result=call(() -> payments.close(token(properties.getPayToken()),order.getTenantId(),paymentRequest(order)));
        validatePayment(order,result);
        if(Boolean.TRUE.equals(result.getReviewRequired()))throw exception0(409,"原支付存在异常付款，需要核查");
        if(PayOrderStatusEnum.isClosed(result.getStatus()) && !Boolean.TRUE.equals(result.getClosing()))
            throw exception0(503,"支付关闭回执缺少持久关闭标记");
        return result;
    }

    private PayOrderCreateReqDTO paymentRequest(TradeOrderDO order) {
        PayOrderCreateReqDTO body=new PayOrderCreateReqDTO();
        body.setAppKey("qinghe-trade");body.setUserId(order.getUserId());body.setUserType(UserTypeEnum.MEMBER.getValue());
        body.setMerchantOrderId(order.getId().toString());body.setSubject("商城订单 "+order.getId());
        body.setPrice(order.getPayPrice());body.setExpireTime(order.getPayDeadline());
        return body;
    }

    public PayOrderRespDTO paymentResult(Long orderId) {
        TradeOrderDO order=paymentOrder(orderId);
        return paymentResult(order);
    }
    private PayOrderRespDTO paymentResult(TradeOrderDO order) {
        if(order.getPayPrice()==null || order.getPayPrice()<=0)return null;
        PayOrderGetReqDTO body=new PayOrderGetReqDTO();body.setAppKey("qinghe-trade");
        body.setUserId(order.getUserId());body.setMerchantOrderId(order.getId().toString());
        PayOrderRespDTO result=call(() -> payments.result(token(properties.getPayToken()),order.getTenantId(),body));
        if(result!=null)validatePayment(order,result);return result;
    }

    /** 有界主动查原渠道尝试，补偿通知丢失；无账返回null不证明没有在途创建。 */
    public PayOrderRespDTO synchronizePayment(Long orderId) {
        TradeOrderDO order=paymentOrder(orderId);
        return synchronizePayment(order);
    }
    private PayOrderRespDTO synchronizePayment(TradeOrderDO order) {
        if(order.getPayPrice()==null || order.getPayPrice()<=0)return null;
        PayOrderGetReqDTO body=new PayOrderGetReqDTO();body.setAppKey("qinghe-trade");
        body.setUserId(order.getUserId());body.setMerchantOrderId(order.getId().toString());
        PayOrderRespDTO result=call(() -> payments.synchronize(token(properties.getPayToken()),order.getTenantId(),body));
        if(result!=null)validatePayment(order,result);return result;
    }

    /** 参数仍只取持久主单；submit是显式用户命令，false只读原支付账/二维码快照。 */
    public PayOrderSubmissionRespDTO paymentSubmission(Long orderId,boolean submit) {
        return paymentSubmission(orderId,submit?PaymentAction.SUBMIT:PaymentAction.READ);
    }
    public PayOrderSubmissionRespDTO recoverPaymentQr(Long orderId) {
        return paymentSubmission(orderId,PaymentAction.RECOVER);
    }
    private enum PaymentAction {READ,SUBMIT,RECOVER}
    private PayOrderSubmissionRespDTO paymentSubmission(Long orderId,PaymentAction action) {
        TradeOrderDO order=paymentOrder(orderId);
        if(action!=PaymentAction.READ)requireSeckillPaymentEligibility(order);
        if(order.getPayPrice()==null || order.getPayPrice()<=0)return null;
        PayOrderGetReqDTO body=new PayOrderGetReqDTO();body.setAppKey("qinghe-trade");
        body.setUserId(order.getUserId());body.setMerchantOrderId(orderId.toString());
        PayOrderSubmissionRespDTO result=call(() -> {
            switch(action) {
                case SUBMIT:return payments.submit(token(properties.getPayToken()),order.getTenantId(),body);
                case RECOVER:return payments.recoverQr(token(properties.getPayToken()),order.getTenantId(),body);
                default:return payments.submission(token(properties.getPayToken()),order.getTenantId(),body);
            }
        });
        if(result==null){if(action!=PaymentAction.READ)throw exception0(503,"原支付提交结果待确认");return null;}
        validatePayment(order,result.getPayment());
        if(result.getQrRecoveryCount()==null || result.getQrRecoveryCount()<0 || result.getQrRecoveryCount()>3
                || result.getQrRecoveryCount()>0 && result.getQrRecoveryAfter()==null
                || result.getQrRecoveryAfter()!=null && result.getQrRecoveryAfter().isBefore(order.getCreateTime()))
            throw exception0(503,"原付款二维码恢复进度不完整");
        if(order.getPayOrderId()!=null && !Objects.equals(order.getPayOrderId(),result.getPayment().getId())
                || !Arrays.asList("NOT_SUBMITTED","SUBMITTING","WAITING","SUCCESS","CLOSED","CLOSING","REVIEW").contains(result.getState()))
            throw exception0(503,"原支付提交身份或状态不一致");
        if(result.getQrCode()!=null) {
            try {
                URI qr=URI.create(result.getQrCode());
                if(!"WAITING".equals(result.getState()) || result.getPayment().getStatus()!=0
                        || Boolean.TRUE.equals(result.getPayment().getClosing()) || Boolean.TRUE.equals(result.getPayment().getReviewRequired())
                        || result.getQrCode().length()>2048 || !"https".equals(qr.getScheme()) || qr.getUserInfo()!=null || qr.getFragment()!=null
                        || qr.getPort()!=-1 && qr.getPort()!=443 || !Arrays.asList("qr.alipay.com","qr.alipaydev.com").contains(qr.getHost()))
                    throw new IllegalArgumentException();
            }catch(RuntimeException invalid){throw exception0(503,"原付款二维码不完整，请查询原订单");}
        }
        return result;
    }

    /** 原退款回补白名单回执；普通库存保持原逻辑，秒杀只接受真实原池恢复+缓存完成。 */
    public Map<String,Object> restoreRefundStockReceipt(Long id) {
        RefundResources context=refundResources(id);AfterSaleRefundDO plan=context.plan;
        if(plan.getRestoreCount()<=0)throw exception0(409,"本次退款没有库存恢复义务");
        if(Integer.valueOf(0).equals(context.order.getType())) {
            ProductAfterSaleStockRespDTO result=restoreRefundStock(id);
            Map<String,Object> receipt=new LinkedHashMap<>();
            receipt.put("orderId",result.getOrderId());receipt.put("afterSaleId",result.getAfterSaleId());receipt.put("orderItemId",result.getOrderItemId());
            receipt.put("spuId",result.getSpuId());receipt.put("skuId",result.getSkuId());receipt.put("quantity",result.getQuantity());
            receipt.put("beforeStock",result.getBeforeStock());receipt.put("afterStock",result.getAfterStock());
            receipt.put("restoredAt",result.getRestoredTime().toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli());return receipt;
        }
        if(!Integer.valueOf(1).equals(context.order.getType()) || !seckillPaymentsEnabled)
            throw exception0(503,"原秒杀退款恢复尚未启用");
        SeckillRefundResourceLookupDTO q=read.execute(tx->{
            TradeOrderPrepareDO p=preparations.get(plan.getTenantId(),plan.getOrderId());
            if(p==null || !Integer.valueOf(1).equals(p.getSourceType()))throw exception0(503,"原秒杀准备缺失");
            TradeOrderDO o=TradeSeckillResultReadService.validatedOrder(orders,preparations,plan.getTenantId(),seckillBindings.get(plan.getTenantId(),p.getRequestId()));
            if(!Objects.equals(o.getId(),plan.getOrderId()) || !Objects.equals(o.getUserId(),plan.getUserId())
                    || !Objects.equals(o.getPayOrderId(),plan.getPayOrderId()) || !Integer.valueOf(1).equals(context.item.getSourceType()))
                throw exception0(503,"原秒杀订单或退款归属不一致");
            SeckillRefundResourceLookupDTO query=new SeckillRefundResourceLookupDTO();query.setRequestNo(p.getRequestId());query.setRequestDigest(p.getRequestHash());
            query.setMemberId(plan.getUserId());query.setOrderId(plan.getOrderId());query.setAfterSaleId(id);return query;
        });
        SeckillRefundResourceReceiptDTO result=call(()->coupons.seckillRefundResult(token(properties.getPromotionToken()),plan.getTenantId(),q));
        if(result!=null)refundReceiptIdentity(result,q,plan.getTenantId());
        if(result==null || !"RESTORED".equals(result.getState()) || !"SYNCED".equals(result.getCacheState()))
            result=call(()->coupons.seckillRefundRecover(token(properties.getPromotionToken()),plan.getTenantId(),q));
        if(result==null)throw exception0(503,"原秒杀退款资源尚未确认");
        refundReceiptIdentity(result,q,plan.getTenantId());
        if(!"RESTORED".equals(result.getState()) || !"SYNCED".equals(result.getCacheState())
                || !Objects.equals(result.getOrderItemId(),plan.getOrderItemId()) || !Objects.equals(result.getRefundNo(),plan.getRefundNo())
                || !Objects.equals(result.getRefundedCount(),context.sale.getCount()) || !Objects.equals(result.getRestoreCount(),plan.getRestoreCount())
                || !Objects.equals(result.getRefundAmount(),plan.getTotalPrice()) || !Objects.equals(result.getPayRefundId(),plan.getPayRefundId())
                || result.getRefundTime()==null || !result.getRefundTime().toInstant().equals(plan.getRefundTime()
                    .truncatedTo(java.time.temporal.ChronoUnit.MILLIS).atZone(ZoneId.of("Asia/Shanghai")).toInstant())
                || result.getOrderVersion()==null || result.getOrderVersion()<0 || result.getOrderVersion()>context.order.getAuthVersion()
                || result.getAfterSaleVersion()==null || result.getAfterSaleVersion()<0 || result.getAfterSaleVersion()>context.sale.getAuthVersion()
                || result.getRestoredTotal()==null || result.getRestoredTotal()<plan.getRestoreCount() || result.getRestoredTotal()>context.item.getCount()
                || result.getRestoredTime()==null || result.getRestoredTime().toInstant().isBefore(result.getRefundTime().toInstant()))
            throw exception0(503,"原秒杀退款库存或缓存恢复仍待核对");
        Map<String,Object> receipt=new LinkedHashMap<>();receipt.put("kind","SECKILL_POOL");receipt.put("requestNo",result.getRequestNo());
        receipt.put("orderId",result.getOrderId());receipt.put("afterSaleId",result.getAfterSaleId());receipt.put("orderItemId",result.getOrderItemId());
        receipt.put("refundNo",result.getRefundNo());receipt.put("payRefundId",result.getPayRefundId());receipt.put("quantity",result.getRestoreCount());
        receipt.put("refundedCount",result.getRefundedCount());receipt.put("refundAmount",result.getRefundAmount());receipt.put("restoredTotal",result.getRestoredTotal());
        receipt.put("refundTime",result.getRefundTime().toInstant().toEpochMilli());receipt.put("restoredAt",result.getRestoredTime().toInstant().toEpochMilli());
        receipt.put("orderVersion",result.getOrderVersion());receipt.put("afterSaleVersion",result.getAfterSaleVersion());
        receipt.put("state",result.getState());receipt.put("cacheState",result.getCacheState());return receipt;
    }
    private void refundReceiptIdentity(SeckillRefundResourceReceiptDTO result,SeckillRefundResourceLookupDTO q,Long tenant) {
        if(!Objects.equals(result.getTenantId(),tenant) || !Objects.equals(result.getRequestNo(),q.getRequestNo())
                || !Objects.equals(result.getRequestDigest(),q.getRequestDigest()) || !Objects.equals(result.getMemberId(),q.getMemberId())
                || !Objects.equals(result.getOrderId(),q.getOrderId()) || !Objects.equals(result.getAfterSaleId(),q.getAfterSaleId()))
            throw exception0(503,"秒杀退款回执不属于原订单售后");
    }

    /** 原退款已确认后的部分库存回补；先读原售后回执，null时同号恢复，绝不调用整单release。 */
    public ProductAfterSaleStockRespDTO restoreRefundStock(Long id) {
        RefundResources context=refundResources(id);
        if(!Integer.valueOf(0).equals(context.order.getType()))throw exception0(409,"秒杀退款必须按原活动池/清算协议恢复，不能回补普通库存");
        AfterSaleRefundDO plan=context.plan;
        if (plan.getRestoreCount()<=0) throw exception0(409,"本次原退款没有库存恢复义务");
        ProductAfterSaleStockReqDTO body=new ProductAfterSaleStockReqDTO();
        body.setOrderId(plan.getOrderId());body.setUserId(plan.getUserId());body.setAfterSaleId(id);body.setOrderItemId(plan.getOrderItemId());
        body.setSpuId(context.item.getSpuId());body.setSkuId(context.item.getSkuId());body.setQuantity(plan.getRestoreCount());
        ProductAfterSaleStockRespDTO result=call(() -> stocks.refundResult(token(properties.getProductToken()),plan.getTenantId(),body));
        if (result==null) result=call(() -> stocks.restoreRefund(token(properties.getProductToken()),plan.getTenantId(),body));
        if (result==null || !Objects.equals(result.getOrderId(),plan.getOrderId()) || !Objects.equals(result.getUserId(),plan.getUserId())
                || !Objects.equals(result.getAfterSaleId(),id) || !Objects.equals(result.getOrderItemId(),plan.getOrderItemId())
                || !Objects.equals(result.getSpuId(),body.getSpuId()) || !Objects.equals(result.getSkuId(),body.getSkuId())
                || !Objects.equals(result.getQuantity(),plan.getRestoreCount()) || result.getBeforeStock()==null || result.getBeforeStock()<0
                || result.getAfterStock()==null || result.getAfterStock()<0
                || (long)result.getAfterStock()-result.getBeforeStock()!=plan.getRestoreCount() || result.getRestoredTime()==null
                || result.getRestoredTime().isBefore(plan.getRefundTime().minusSeconds(1))
                || result.getRestoredTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusSeconds(1)))
            throw exception0(503,"原售后库存回补回执需核对");
        return result;
    }

    /** 仅已确认整单原实付全额退款；复用旧券领域归还，过期/风控事实继续保留。 */
    public CouponOrderResultDTO returnRefundCoupon(Long id) {
        RefundResources context=refundResources(id);
        AfterSaleRefundDO plan=context.plan;
        if (!Boolean.TRUE.equals(plan.getCouponReturnRequired()) || context.order.getCouponId()==null
                || context.order.getStatus()!=40 || !Integer.valueOf(20).equals(context.order.getCancelType())
                || !Objects.equals(context.order.getRefundPrice(),context.order.getPayPrice()))
            throw exception0(409,"只有原整单实付全额退款可处理原券");
        Context original=load(plan.getOrderId(),false);
        if (original.snapshot.getCoupon()==null || !Objects.equals(original.snapshot.getCoupon().getId(),context.order.getCouponId())
                || !Objects.equals(original.order.getUserId(),plan.getUserId()) || !"CREATED".equals(original.order.getStage()))
            throw exception0(503,"原订单券快照不完整");
        CouponOrderCommandReqDTO body=couponCommand(original);
        CouponOrderResultDTO result=call(() -> coupons.result(token(properties.getPromotionToken()),plan.getTenantId(),body));
        validateCoupon(original,result);
        if (Integer.valueOf(2).equals(result.getState())) {
            result=call(() -> coupons.returnRefund(token(properties.getPromotionToken()),plan.getTenantId(),body));
            validateCoupon(original,result);
        }
        if (!Integer.valueOf(4).equals(result.getState()) || !"FULL_REFUND".equals(result.getReturnReason()))
            throw exception0(503,"原券整单退款处理回执待核查");
        return result;
    }

    private RefundResources refundResources(Long id) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("售后资源RPC不能置于本地事务内");
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || id==null || id<=0 || id>9_007_199_254_740_991L) throw exception0(403,"售后资源恢复需要可信原租户");
        RefundResources result=read.execute(transaction -> {
            AfterSaleRefundDO plan=refundPlans.get(tenant,id);AfterSaleDO sale=afterSales.get(tenant,id);
            TradeOrderDO order=plan==null?null:orders.owned(tenant,plan.getUserId(),plan.getOrderId());
            TradeOrderItemDO item=plan==null?null:refundPlans.originalItem(tenant,plan.getOrderId(),plan.getOrderItemId());
            if (plan==null || sale==null || order==null || item==null || !"SUCCESS".equals(plan.getStatus()) || sale.getStatus()!=50
                    || plan.getRefundTime()==null || !Objects.equals(plan.getRefundTime(),sale.getRefundTime()) || !Boolean.TRUE.equals(order.getPayStatus())
                    || !Objects.equals(sale.getOrderId(),plan.getOrderId()) || !Objects.equals(sale.getUserId(),plan.getUserId())
                    || !Objects.equals(sale.getOrderItemId(),plan.getOrderItemId()) || !Objects.equals(item.getUserId(),plan.getUserId())
                    || item.getRefundCount()<sale.getCount() || plan.getRestoreCount()<0 || plan.getRestoreCount()>sale.getCount())
                throw exception0(409,"只有原已确认退款可处理原资源");
            return new RefundResources(order,item,plan,sale);
        });
        if (result==null) throw new IllegalStateException("售后资源计划读取无结果");
        return result;
    }
    private static class RefundResources {
        final TradeOrderDO order;final TradeOrderItemDO item;final AfterSaleRefundDO plan;final AfterSaleDO sale;
        RefundResources(TradeOrderDO order,TradeOrderItemDO item,AfterSaleRefundDO plan,AfterSaleDO sale) {this.order=order;this.item=item;this.plan=plan;this.sale=sale;}
    }

    public enum RefundAction {REGISTER, READ, SUBMIT, QUERY, RETRY}

    /** 只能提供原售后id与固定动作；金额、会员、支付和退款号一律从原不可撤销计划读取。 */
    public PayRefundResultDTO refundPayment(Long id, RefundAction action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("退款RPC不能置于trade事务内");
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || tenant > 9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || id == null || id <= 0 || id > 9_007_199_254_740_991L || action == null) throw exception0(403,"退款调用需要可信租户与原售后");
        RefundContext context = read.execute(transaction -> {
            AfterSaleRefundDO plan = refundPlans.get(tenant,id);
            AfterSaleDO sale = afterSales.get(tenant,id);
            TradeOrderDO order = plan == null ? null : orders.paymentOrder(tenant,plan.getOrderId());
            TradeOrderPrepareDO preparation = order == null ? null : preparations.progress(tenant,order.getId());
            if (plan == null || sale == null || order == null || preparation == null || !"CREATED".equals(preparation.getStage())
                    || !Arrays.asList(45,50).contains(sale.getStatus()) || !Boolean.TRUE.equals(order.getPayStatus())
                    || !Objects.equals(order.getId(),sale.getOrderId()) || !Objects.equals(order.getUserId(),sale.getUserId())
                    || !Objects.equals(order.getUserId(),plan.getUserId()) || !Objects.equals(order.getPayOrderId(),plan.getPayOrderId())
                    || !Objects.equals(plan.getOrderItemId(),sale.getOrderItemId()) || !Objects.equals(plan.getGoodsPrice(),sale.getGoodsRefundPrice())
                    || plan.getTotalPrice() <= 0 || plan.getTotalPrice() > order.getPayPrice()
                    || !Objects.equals(plan.getTotalPrice(),Math.addExact(plan.getGoodsPrice(),plan.getDeliveryPrice())))
                throw exception0(409,"原不可撤销退款计划或支付归属不完整");
            return new RefundContext(order,plan);
        });
        if (context == null) throw new IllegalStateException("原退款计划读取无结果");
        PayRefundCreateReqDTO body = new PayRefundCreateReqDTO();
        body.setAppKey("qinghe-trade");body.setUserId(context.order.getUserId());body.setOrderId(context.plan.getPayOrderId());
        body.setMerchantOrderId(context.order.getId().toString());body.setMerchantRefundId(id.toString());body.setNo(context.plan.getRefundNo());
        body.setPayPrice(context.order.getPayPrice());body.setRefundPrice(context.plan.getTotalPrice());body.setReason("售后退款 "+id);
        PayRefundGetReqDTO lookup = new PayRefundGetReqDTO();
        lookup.setAppKey(body.getAppKey());lookup.setUserId(body.getUserId());lookup.setOrderId(body.getOrderId());
        lookup.setMerchantOrderId(body.getMerchantOrderId());lookup.setMerchantRefundId(body.getMerchantRefundId());lookup.setNo(body.getNo());
        PayRefundResultDTO result = call(() -> {
            switch (action) {
                case REGISTER: return payments.createRefund(token(properties.getPayToken()),tenant,body);
                case SUBMIT: return payments.submitRefund(token(properties.getPayToken()),tenant,lookup);
                case QUERY: return payments.queryRefund(token(properties.getPayToken()),tenant,lookup);
                case RETRY: return payments.retryRefund(token(properties.getPayToken()),tenant,lookup);
                default: return payments.refundResult(token(properties.getPayToken()),tenant,lookup);
            }
        });
        if (result == null) {
            if (action != RefundAction.READ) throw exception0(503,"原退款结果待确认，请查询原号");
            return null;
        }
        validateRefund(context,result);
        return result;
    }

    /** 仅已持久化财务依据的调用方使用；该RPC本身不提交退款。 */
    public PayRefundResultDTO authorizeRefundContinuation(Long id,Long actor,String requestId,String reason,Long expectedPayVersion) {
        PayRefundResultDTO original=refundPayment(id,RefundAction.READ);
        if (original==null)throw exception0(409,"请先查询已登记的原支付退款");
        PayRefundContinuationReqDTO command=new PayRefundContinuationReqDTO();
        command.setAppKey("qinghe-trade");command.setUserId(original.getUserId());command.setOrderId(original.getOrderId());
        command.setMerchantOrderId(original.getMerchantOrderId());command.setMerchantRefundId(original.getMerchantRefundId());command.setNo(original.getNo());
        command.setActorId(actor);command.setRequestId(requestId);command.setReason(reason);command.setExpectedVersion(expectedPayVersion);
        Long tenant=TenantContextHolder.getTenantId();
        PayRefundResultDTO result=call(() -> payments.authorizeRefundContinuation(token(properties.getPayToken()),tenant,command));
        RefundContext context=read.execute(transaction -> {
            AfterSaleRefundDO plan=refundPlans.get(tenant,id);
            TradeOrderDO order=plan==null?null:orders.paymentOrder(tenant,plan.getOrderId());
            if (plan==null || order==null)throw exception0(503,"原人工退款计划缺失");
            return new RefundContext(order,plan);
        });
        if (result==null || !Objects.equals(result.getId(),original.getId()))throw exception0(503,"原人工继续回包需核对");
        validateRefund(context,result);return result;
    }

    /** 原actor/UUID授权回执只读RPC，与授权和资金提交隔离。 */
    public PayRefundContinuationResultDTO refundContinuationResult(Long id,Long actor,String requestId,Long fromVersion) {
        PayRefundResultDTO original=refundPayment(id,RefundAction.READ);
        if(original==null)throw exception0(503,"原人工授权支付账缺失");
        PayRefundContinuationGetReqDTO body=new PayRefundContinuationGetReqDTO();
        body.setAppKey("qinghe-trade");body.setUserId(original.getUserId());body.setOrderId(original.getOrderId());
        body.setMerchantOrderId(original.getMerchantOrderId());body.setMerchantRefundId(original.getMerchantRefundId());body.setNo(original.getNo());
        body.setActorId(actor);body.setRequestId(requestId);
        PayRefundContinuationResultDTO receipt=call(() -> payments.refundContinuationResult(token(properties.getPayToken()),TenantContextHolder.getTenantId(),body));
        if(receipt==null)return null;
        if(!Objects.equals(receipt.getRefundId(),original.getId()) || !Objects.equals(receipt.getNo(),original.getNo())
                || !Objects.equals(receipt.getRequestId(),requestId) || !Objects.equals(receipt.getFromVersion(),fromVersion)
                || receipt.getFromLimit()==null || receipt.getFromLimit()<3 || receipt.getFromLimit()==Integer.MAX_VALUE
                || !Objects.equals(receipt.getToLimit(),Math.addExact(receipt.getFromLimit(),1)))
            throw exception0(503,"原人工授权回执与原计划不一致");
        return receipt;
    }

    private void validateRefund(RefundContext context,PayRefundResultDTO result) {
        AfterSaleRefundDO plan = context.plan;
        if (result.getId() == null || result.getId() <= 0 || result.getId() > 9_007_199_254_740_991L
                || !Objects.equals(result.getTenantId(),plan.getTenantId()) || !"qinghe-trade".equals(result.getAppKey())
                || !Objects.equals(result.getOrderId(),plan.getPayOrderId()) || !Objects.equals(result.getUserId(),plan.getUserId())
                || !UserTypeEnum.MEMBER.getValue().equals(result.getUserType()) || !plan.getOrderId().toString().equals(result.getMerchantOrderId())
                || !plan.getAfterSaleId().toString().equals(result.getMerchantRefundId()) || !plan.getRefundNo().equals(result.getNo())
                || !Objects.equals(context.order.getPayPrice(),result.getPayPrice()) || !Objects.equals(plan.getTotalPrice(),result.getRefundPrice())
                || !Arrays.asList("PREPARED","PROCESSING","UNKNOWN","SUCCESS","FAILED").contains(result.getStatus())
                || result.getAuthVersion() == null || result.getAuthVersion() < 0 || result.getAuthVersion() > 9_007_199_254_740_991L
                || result.getSubmissionLimit() == null || result.getSubmissionLimit() < 3
                || result.getSubmitCount() == null || result.getSubmitCount() < 0 || result.getSubmitCount() > result.getSubmissionLimit()
                || "SUCCESS".equals(result.getStatus()) != (result.getSuccessTime() != null)
                || result.getSuccessTime() != null && (result.getSuccessTime().isBefore(plan.getCreateTime().minusSeconds(1))
                    || result.getSuccessTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusSeconds(1))))
            throw exception0(503,"支付退款回包与原业务计划不一致");
    }

    private static class RefundContext {
        final TradeOrderDO order; final AfterSaleRefundDO plan;
        RefundContext(TradeOrderDO order,AfterSaleRefundDO plan) {this.order=order;this.plan=plan;}
    }

    private TradeOrderDO paymentOrder(Long id) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("支付RPC不能置于trade事务内");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(403,"支付调用需要可信租户与订单编号");
        TradeOrderDO row=read.execute(status -> {
            TradeOrderDO order=orders.paymentOrder(tenant,id);
            if(order!=null && Integer.valueOf(1).equals(order.getType())) {
                if(!seckillPaymentsEnabled)throw exception0(503,"秒杀付款尚未启用");
                TradeOrderPrepareDO original=preparations.get(tenant,id);
                if(original==null || !Integer.valueOf(1).equals(original.getSourceType()))throw exception0(409,"原秒杀准备记录缺失");
                TradeOrderDO verified=TradeSeckillResultReadService.validatedOrder(orders,preparations,tenant,seckillBindings.get(tenant,original.getRequestId()));
                if(!Objects.equals(verified.getId(),id))throw exception0(409,"原秒杀订单引用不一致");return verified;
            }
            if(order!=null && !Integer.valueOf(0).equals(order.getType()))throw exception0(409,"不支持的付款订单类型");
            TradeOrderPrepareDO preparation=preparations.progress(tenant,id);
            if(order==null || preparation==null || !"CREATED".equals(preparation.getStage())
                    || !Objects.equals(order.getUserId(),preparation.getUserId()))throw exception0(409,"订单尚未完成创建");
            return order;
        });
        if(row==null)throw new IllegalStateException("支付订单读取无结果");return row;
    }

    private void requireSeckillPaymentEligibility(TradeOrderDO order) {
        if(!Integer.valueOf(1).equals(order.getType()))return;
        if(!seckillPaymentsEnabled)throw exception0(503,"秒杀付款尚未启用");
        SeckillResourceLookupDTO q=read.execute(tx -> {
            TradeOrderPrepareDO p=preparations.get(order.getTenantId(),order.getId());
            if(p==null || !Objects.equals(p.getUserId(),order.getUserId()) || !Integer.valueOf(1).equals(p.getSourceType()))throw exception0(409,"原申请引用缺失");
            SeckillResourceLookupDTO ref=new SeckillResourceLookupDTO();
            ref.setRequestNo(p.getRequestId());ref.setRequestDigest(p.getRequestHash());ref.setMemberId(order.getUserId());ref.setOrderId(order.getId());return ref;
        });
        SeckillPaymentEligibilityDTO proof=call(()->coupons.seckillPaymentEligibility(token(properties.getPromotionToken()),order.getTenantId(),q));
        if(proof==null || !Objects.equals(proof.getTenantId(),order.getTenantId()) || !Objects.equals(proof.getMemberId(),order.getUserId())
                || !Objects.equals(proof.getOrderId(),order.getId()) || !Objects.equals(proof.getRequestNo(),q.getRequestNo())
                || !Objects.equals(proof.getRequestDigest(),q.getRequestDigest()) || proof.getRequestVersion()==null || proof.getRequestVersion()<0
                || proof.getRequestVersion()>9007199254740991L || proof.getPayDeadline()==null || proof.getCanPay()==null
                || !proof.getPayDeadline().toInstant().equals(order.getPayDeadline().atZone(ZoneId.of("Asia/Shanghai")).toInstant()))throw exception0(503,"原秒杀付款资格需核对");
        if(!Boolean.TRUE.equals(proof.getCanPay()))throw exception0(409,"原申请已关闭、终结或超过付款期限");
    }

    private TradeOrderDO seckillPaymentOrder(Long id) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("秒杀支付查账/关闭仅允许事务外可信后台");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || id==null || id<=0 || id>9007199254740991L)throw exception0(403,"需要可信租户及原订单号");
        TradeOrderDO row=read.execute(tx -> {
            TradeOrderPrepareDO p=preparations.get(tenant,id);
            if(p==null || !Integer.valueOf(1).equals(p.getSourceType()) || !"CREATED".equals(p.getStage()))throw exception0(409,"原秒杀订单尚未创建");
            TradeOrderDO o=TradeSeckillResultReadService.validatedOrder(orders,preparations,tenant,seckillBindings.get(tenant,p.getRequestId()));
            if(!Objects.equals(o.getId(),id) || o.getPayPrice()==null || o.getPayPrice()<=0)throw exception0(409,"原秒杀订单引用不一致");
            return o;
        });
        if(row==null)throw new IllegalStateException("原秒杀支付订单读取无结果");return row;
    }

    private void validatePayment(TradeOrderDO order,PayOrderRespDTO result) {
        if(result==null || result.getId()==null || result.getId()<=0 || result.getId()>9_007_199_254_740_991L
                || !"qinghe-trade".equals(result.getAppKey()) || !Objects.equals(order.getTenantId(),result.getTenantId())
                || !Objects.equals(order.getUserId(),result.getUserId()) || !UserTypeEnum.MEMBER.getValue().equals(result.getUserType())
                || !order.getId().toString().equals(result.getMerchantOrderId()) || !Objects.equals(order.getPayPrice(),result.getPrice())
                || !Objects.equals(order.getPayDeadline(),result.getExpireTime()) || !("商城订单 "+order.getId()).equals(result.getSubject())
                || result.getClosing()==null || result.getReviewRequired()==null
                || !Arrays.asList(PayOrderStatusEnum.WAITING.getStatus(),PayOrderStatusEnum.SUCCESS.getStatus(),PayOrderStatusEnum.REFUND.getStatus(),PayOrderStatusEnum.CLOSED.getStatus()).contains(result.getStatus())
                || result.getRefundPrice()==null || result.getRefundPrice()<0 || result.getRefundPrice()>order.getPayPrice())
            throw exception0(503,"支付结果与原订单不一致，请查询原结果");
    }

    private CouponOrderCommandReqDTO couponCommand(Context context) {
        CouponOrderCommandReqDTO request = new CouponOrderCommandReqDTO();
        request.setOrderId(context.order.getId());
        request.setUserId(context.order.getUserId());
        request.setCouponId(context.snapshot.getCoupon().getId());
        return request;
    }

    private Context load(Long id, boolean command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("订单资源RPC不能放在trade事务或锁内");
        }
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || tenant > 9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || id == null || id <= 0 || id > 9_007_199_254_740_991L) {
            throw exception0(403, "订单资源需要可信租户和准备编号");
        }
        Context context = read.execute(status -> {
            TradeOrderPrepareDO order = preparations.get(tenant, id);
            if (order == null) throw exception0(404, "本租户订单准备记录不存在");
            if (!Integer.valueOf(0).equals(order.getSourceType())) throw exception0(409,"秒杀订单不使用普通库存和优惠券资源链");
            List<TradeOrderPrepareItemDO> items = preparations.items(tenant, id);
            TradeSettlementSnapshotBO snapshot = JsonUtils.parseObject(order.getSnapshot(), TradeSettlementSnapshotBO.class);
            if (snapshot == null || snapshot.getItems() == null || items.isEmpty() || items.size() > 200
                    || items.size() != snapshot.getItems().size() || order.getUserId() == null || order.getUserId() <= 0) {
                throw exception0(503, "订单准备快照不完整");
            }
            if (snapshot.getCoupon() != null && (snapshot.getCouponQuote() == null
                    || snapshot.getCouponQuote().getLines() == null || snapshot.getCouponQuote().getLines().size() != items.size()
                    || !Objects.equals(order.getUserId(), snapshot.getCoupon().getUserId())
                    || !Objects.equals(tenant, snapshot.getCoupon().getTenantId()))) {
                throw exception0(503, "订单优惠券快照不完整");
            }
            if (command && (!Arrays.asList("PREPARED", "PROCESSING").contains(order.getStage())
                    || !LocalDateTime.now(ZoneId.of("Asia/Shanghai")).isBefore(order.getPrepareDeadline()))) {
                throw exception0(409, "订单准备已关闭或到期，只能查询原资源结果");
            }
            return new Context(tenant, order, items, snapshot);
        });
        if (context == null) throw new IllegalStateException("订单准备读取未返回结果");
        return context;
    }

    private void validateStock(Context context, ProductOrderStockRespDTO result) {
        if (result == null || !Objects.equals(context.order.getId(), result.getOrderId())
                || !Objects.equals(context.order.getUserId(), result.getUserId()) || result.getStatus() == null
                || !Arrays.asList(0,1,2).contains(result.getStatus()) || result.getItems() == null) {
            throw exception0(503, "库存结果待确认，请查询原订单");
        }
        if (result.getStatus() == ProductOrderStockRespDTO.CLOSED) {
            if (!result.getItems().isEmpty()) throw exception0(503, "关闭库存结果不完整");
            return;
        }
        Map<Long, TradeOrderPrepareItemDO> expected = context.items.stream()
                .collect(Collectors.toMap(TradeOrderPrepareItemDO::getId, item -> item));
        if (result.getItems().size() != expected.size()) throw exception0(503, "库存结果明细不完整");
        for (ProductOrderStockRespDTO.Item item : result.getItems()) {
            TradeOrderPrepareItemDO original = item == null ? null : expected.remove(item.getOrderItemId());
            if (original == null || !Objects.equals(original.getSpuId(), item.getSpuId())
                    || !Objects.equals(original.getSkuId(), item.getSkuId()) || !Objects.equals(original.getCount(), item.getQuantity())
                    || !Objects.equals(original.getPrice(), item.getUnitPrice()) || !Objects.equals(original.getSpuVersion(), item.getSpuVersion())
                    || !Objects.equals(original.getSkuVersion(), item.getSkuVersion())) {
                throw exception0(503, "库存结果与原订单不一致");
            }
        }
    }

    private void validateCoupon(Context context, CouponOrderResultDTO result) {
        if (result == null || !Objects.equals(context.order.getId(), result.getOrderId())
                || !Objects.equals(context.snapshot.getCoupon().getId(), result.getCouponId())
                || result.getState() == null || !Arrays.asList(1,2,3,4).contains(result.getState())
                || !Objects.equals(context.snapshot.getCouponQuote(), result.getQuote())) {
            throw exception0(503, "优惠券结果与原订单不一致，请查询原结果");
        }
    }

    private <T> T call(Supplier<CommonResult<T>> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("订单资源RPC不能放在trade事务内");
        }
        if (!calls.tryAcquire()) throw exception0(503, "订单资源服务繁忙");
        try {
            CommonResult<T> result = action.get();
            if (result == null || result.getCode() == null) throw exception0(503, "订单资源结果待确认，请查询原订单");
            result.checkError();
            return result.getData();
        } catch (ServiceException error) {
            throw error;
        } catch (RuntimeException error) {
            throw exception0(503, "订单资源结果待确认，请查询原订单");
        } finally {
            calls.release();
        }
    }

    private String token(String value) {
        if (value.isEmpty()) throw exception0(503, "订单服务凭据未配置，本次未发起资源调用");
        return "Bearer " + value;
    }

    private void tokenFormat(String value) {
        if (value == null || !value.isEmpty() && !value.matches("[A-Za-z0-9_-]{43,128}")) {
            throw new IllegalArgumentException("订单资源服务凭据格式不正确");
        }
    }

    private void origin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && Arrays.asList("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost()))) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("订单资源地址必须是固定可信origin");
        }
    }

    private static class Context {
        private final Long tenant;
        private final TradeOrderPrepareDO order;
        private final List<TradeOrderPrepareItemDO> items;
        private final TradeSettlementSnapshotBO snapshot;
        private Context(Long tenant, TradeOrderPrepareDO order, List<TradeOrderPrepareItemDO> items, TradeSettlementSnapshotBO snapshot) {
            this.tenant=tenant; this.order=order; this.items=items; this.snapshot=snapshot;
        }
    }
}
