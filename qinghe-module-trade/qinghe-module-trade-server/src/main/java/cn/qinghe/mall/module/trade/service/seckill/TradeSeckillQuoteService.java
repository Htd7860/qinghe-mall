package cn.qinghe.mall.module.trade.service.seckill;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.TradeSeckillQuoteDO;
import cn.qinghe.mall.module.trade.dal.mysql.cart.CartMapper;
import cn.qinghe.mall.module.trade.dal.mysql.delivery.DeliveryExpressTemplateMapper;
import cn.qinghe.mall.module.trade.dal.mysql.seckill.TradeSeckillQuoteMapper;
import cn.qinghe.mall.module.trade.framework.cart.CartProperties;
import cn.qinghe.mall.module.trade.framework.checkout.CheckoutReferencesClient;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.framework.product.TradeProductClient;
import cn.qinghe.mall.module.trade.service.delivery.DeliveryExpressTemplateService;
import cn.qinghe.mall.module.trade.service.delivery.bo.*;
import cn.qinghe.mall.module.trade.service.price.calculator.TradeDeliveryPriceCalculator;
import cn.qinghe.mall.module.trade.service.seckill.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原TradeSeckillActivityPriceCalculator活动价与单品约束，直接复用既有运费计算器。
 * 不调用普通结算/订单准备，不扣普通库存、不占券、不改变购物车内容或版本。
 * @author HUIHUI
 */
@Service
public class TradeSeckillQuoteService {
    private final TradeMemberAccess access;
    private final CheckoutReferencesClient references;
    private final TradeProductClient products;
    private final DeliveryExpressTemplateService templates;
    private final TradeDeliveryPriceCalculator delivery;
    private final DeliveryExpressTemplateMapper templateRows;
    private final TradeSeckillQuoteMapper quotes;
    private final CartMapper members;
    private final CartProperties limits;
    private final TradeSeckillQuoteCipher cipher;
    private final TransactionTemplate write;

    public TradeSeckillQuoteService(TradeMemberAccess access,CheckoutReferencesClient references,TradeProductClient products,
            DeliveryExpressTemplateService templates,TradeDeliveryPriceCalculator delivery,DeliveryExpressTemplateMapper templateRows,
            TradeSeckillQuoteMapper quotes,CartMapper members,CartProperties limits,TradeSeckillQuoteCipher cipher,
            PlatformTransactionManager manager) {
        this.access=access;this.references=references;this.products=products;this.templates=templates;this.delivery=delivery;
        this.templateRows=templateRows;this.quotes=quotes;this.members=members;this.limits=limits;this.cipher=cipher;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    public TradeSeckillQuoteReceiptDTO quote(HttpServletRequest request,String store,TradeSeckillQuoteReqDTO body) {
        outside();validate(body);
        MemberStorefrontRespDTO entry=access.check(request,store);LoginUser actor=access.actor();
        if (!id(actor.getTenantId())) throw exception0(403,"当前会员租户身份不正确");
        String digest=DigestUtil.sha256Hex("seckill-quote-v1:"+actor.getTenantId()+":"+actor.getId()+":"+body.getActivityId()
                +":"+body.getGeneration()+":"+body.getSkuId()+":"+body.getQuantity()+":"+body.getAddressId());
        TradeSeckillQuoteDO previous=quotes.request(actor.getTenantId(),actor.getId(),body.getRequestId());
        if (previous!=null) { same(previous,digest);return view(previous); }
        if (!Boolean.TRUE.equals(entry.getCanPurchase())) throw exception0(403,"店铺暂不支持购买");
        LocalDateTime started=quotes.now();
        SeckillCheckoutContextDTO activity=references.seckill(body.getActivityId(),body.getGeneration(),body.getSkuId(),body.getQuantity());
        ProductTradeSkuRespDTO product=products.list(Collections.singleton(body.getSkuId())).get(body.getSkuId());
        if (product==null || !Objects.equals(product.getSpuId(),activity.getSpuId()) || !Objects.equals(product.getSpuVersion(),activity.getSpuVersion())
                || !Objects.equals(product.getSkuVersion(),activity.getSkuVersion())) throw exception0(409,"活动商品资料已变化，请等待商家处理");
        // 秒杀库存已单独划拨；普通可售为0仍可报价，绝不拿product.stock当活动池余额。
        TradeSeckillQuoteSnapshot snapshot=new TradeSeckillQuoteSnapshot();snapshot.setSchemaVersion(1);
        snapshot.setActivity(activity);snapshot.setProduct(product);snapshot.setAddress(references.address(body.getAddressId()));
        snapshot.setQuantity(body.getQuantity());
        long goods=Math.multiplyExact((long)activity.getPriceCent(),body.getQuantity());
        TradeDeliveryLineBO line=new TradeDeliveryLineBO();line.setSkuId(product.getSkuId());line.setTemplateId(product.getDeliveryTemplateId());
        line.setQuantity(body.getQuantity());line.setPaidPrice(goods);line.setUnitWeight(product.getWeight()==null?null:new BigDecimal(product.getWeight()));
        TradeDeliveryQuoteBO fees=delivery.calculate(actor.getTenantId(),snapshot.getAddress().getAreaId(),Collections.singletonList(line),
                templates.getCheckoutSnapshots(Collections.singleton(product.getDeliveryTemplateId())));
        long payable=Math.addExact(goods,fees.getDeliveryPrice());safe(goods);safe(fees.getDeliveryPrice());safe(payable);
        snapshot.setDelivery(fees);snapshot.setGoodsAmount(goods);snapshot.setShippingAmount(fees.getDeliveryPrice());snapshot.setPayAmount(payable);
        TradeSeckillQuoteDO row=new TradeSeckillQuoteDO();row.setTenantId(actor.getTenantId());row.setMemberId(actor.getId());
        row.setQuoteId(UUID.randomUUID().toString());row.setRequestId(body.getRequestId());row.setRequestDigest(digest);
        row.setActivityId(activity.getActivityId());row.setGeneration(activity.getGeneration());row.setActivityVersion(activity.getActivityVersion());
        row.setRedisEpoch(activity.getRedisEpoch());row.setSkuId(activity.getSkuId());row.setQuantity(body.getQuantity());row.setPriceCent(activity.getPriceCent());
        row.setGoodsAmount(goods);row.setShippingAmount(fees.getDeliveryPrice());row.setPayAmount(payable);row.setCreatedAt(started);
        row.setExpiresAt(started.plusSeconds(120).isBefore(activity.getEndTime())?started.plusSeconds(120):activity.getEndTime());
        String json=JsonUtils.toJsonString(snapshot);row.setQuoteDigest(DigestUtil.sha256Hex(json));row.setSnapshotCipher(cipher.seal(row,json));
        TradeSeckillQuoteDO saved=write.execute(tx -> {
            LoginUser current=access.actor();
            if (!Objects.equals(actor.getTenantId(),current.getTenantId()) || !Objects.equals(actor.getId(),current.getId())) throw exception0(401,"报价身份已变化");
            // 复用现有同会员写锁，不改购物车版本；同原请求与有效报价上限在锁内裁决。
            members.ensureUser(actor.getTenantId(),actor.getId());
            if (members.lockVersion(actor.getTenantId(),actor.getId())==null) throw new IllegalStateException("会员报价锁不存在");
            TradeSeckillQuoteDO found=quotes.request(actor.getTenantId(),actor.getId(),body.getRequestId());
            if (found!=null) { same(found,digest);return found; }
            for (TradeDeliveryQuoteBO.Group group:fees.getGroups()) {
                cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO currentTemplate=templateRows.lockById(group.getTemplateId());
                if (currentTemplate==null || !Integer.valueOf(0).equals(currentTemplate.getStatus()) || !Objects.equals(currentTemplate.getAuthVersion(),group.getTemplateVersion()))
                    throw exception0(409,"运费配置已变化，请重新报价");
            }
            LocalDateTime now=quotes.now();
            if (!now.isBefore(row.getExpiresAt()) || now.isBefore(row.getCreatedAt())) throw exception0(409,"报价已过期或系统时间发生变化");
            Long count=quotes.active(actor.getTenantId(),actor.getId());
            if (count==null || count>=limits.getMaxActiveQuotesPerMember()) throw exception0(429,"有效秒杀报价较多，请稍后重试");
            if (quotes.insert(row)!=1) throw new IllegalStateException("秒杀报价保存失败");return row;
        });
        if (saved==null) throw new IllegalStateException("秒杀报价事务未返回结果");return view(saved);
    }

    /** 原请求结果查询；停业/报价过期仍能查询原事实，不续期、不重算、不申请库存。 */
    public TradeSeckillQuoteReceiptDTO result(HttpServletRequest request,String store,String requestId) {
        outside();if (!uuid(requestId)) throw exception0(400,"请提供原报价请求号");
        access.check(request,store);LoginUser actor=access.actor();
        if (!id(actor.getTenantId())) throw exception0(403,"当前会员租户身份不正确");
        TradeSeckillQuoteDO row=quotes.request(actor.getTenantId(),actor.getId(),requestId);
        return row==null?null:view(row); // null仅代表未观察到已提交记录，不证明没有在途请求。
    }

    private TradeSeckillQuoteReceiptDTO view(TradeSeckillQuoteDO row) {
        LoginUser actor=access.actor();
        if (!Objects.equals(actor.getTenantId(),row.getTenantId()) || !Objects.equals(actor.getId(),row.getMemberId())) throw exception0(404,"报价不存在");
        TradeSeckillQuoteReceiptDTO result=new TradeSeckillQuoteReceiptDTO();result.setQuoteId(row.getQuoteId());result.setRequestId(row.getRequestId());
        result.setQuoteDigest(row.getQuoteDigest());result.setActivityId(row.getActivityId());result.setGeneration(row.getGeneration());
        result.setActivityVersion(row.getActivityVersion());result.setRedisEpoch(row.getRedisEpoch());result.setSkuId(row.getSkuId());
        result.setQuantity(row.getQuantity());result.setPriceCent(row.getPriceCent());result.setGoodsAmount(row.getGoodsAmount());
        result.setShippingAmount(row.getShippingAmount());result.setPayAmount(row.getPayAmount());
        LocalDateTime now=quotes.now();result.setCreatedAt(row.getCreatedAt().atOffset(ZoneOffset.UTC));result.setExpiresAt(row.getExpiresAt().atOffset(ZoneOffset.UTC));
        result.setExpired(!now.isBefore(row.getExpiresAt()));result.setSnapshotPresent(row.getSnapshotCipher()!=null);
        result.setBound(row.getBoundRequestNo()!=null);result.setServerTime(now.toInstant(ZoneOffset.UTC).toEpochMilli());return result;
    }
    private void validate(TradeSeckillQuoteReqDTO body) {
        if (body==null || !uuid(body.getRequestId()) || !id(body.getActivityId()) || !id(body.getSkuId()) || !id(body.getAddressId())
                || body.getGeneration()==null || body.getGeneration()<1 || body.getQuantity()==null || body.getQuantity()<1 || body.getQuantity()>9999)
            throw exception0(400,"秒杀报价参数不正确");
    }
    private void same(TradeSeckillQuoteDO row,String digest) {
        if (!Objects.equals(row.getRequestDigest(),digest)) throw exception0(409,"原报价请求号不能更改活动、规格、数量或地址");
    }
    private void outside() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("报价RPC不能嵌套外层事务"); }
    private boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    private boolean id(Long x) { return x!=null && x>0 && x<=9007199254740991L; }
    private void safe(long x) { if (x<0 || x>9007199254740991L) throw exception0(400,"秒杀报价金额超出支持范围"); }
}
