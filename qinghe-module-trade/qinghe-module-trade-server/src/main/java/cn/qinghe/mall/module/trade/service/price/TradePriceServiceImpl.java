package cn.qinghe.mall.module.trade.service.price;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.common.util.area.*;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.promotion.api.coupon.dto.*;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.trade.controller.app.order.vo.*;
import cn.qinghe.mall.module.trade.dal.dataobject.cart.CartDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeSettlementQuoteDO;
import cn.qinghe.mall.module.trade.dal.mysql.cart.CartMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeSettlementQuoteMapper;
import cn.qinghe.mall.module.trade.dal.mysql.delivery.DeliveryExpressTemplateMapper;
import cn.qinghe.mall.module.trade.framework.cart.CartProperties;
import cn.qinghe.mall.module.trade.framework.delivery.DeliveryQuoteProperties;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.framework.product.TradeProductClient;
import cn.qinghe.mall.module.trade.framework.checkout.CheckoutReferencesClient;
import cn.qinghe.mall.module.trade.service.delivery.DeliveryExpressTemplateService;
import cn.qinghe.mall.module.trade.service.delivery.bo.*;
import cn.qinghe.mall.module.trade.service.price.bo.TradeSettlementSnapshotBO;
import cn.qinghe.mall.module.trade.service.price.calculator.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/**
 * 择取原 TradePriceServiceImpl 的商品读取、优惠券、运费和汇总流程。
 * 普通购物车报价不扣库存、不占券、不创建订单，下单必须重新核价。
 *
 * @author 芋道源码
 */
@Service
public class TradePriceServiceImpl {
    private final TradeMemberAccess access;
    private final TradeProductClient products;
    private final CheckoutReferencesClient references;
    private final CartMapper carts;
    private final TradeSettlementQuoteMapper quotes;
    private final DeliveryExpressTemplateMapper deliveryRows;
    private final DeliveryExpressTemplateService deliveryTemplates;
    private final TradeCouponPriceCalculator couponCalculator;
    private final TradeDeliveryPriceCalculator deliveryCalculator;
    private final CartProperties limits;
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public TradePriceServiceImpl(TradeMemberAccess access, TradeProductClient products,
            CheckoutReferencesClient references, CartMapper carts, TradeSettlementQuoteMapper quotes,
            DeliveryExpressTemplateMapper deliveryRows, DeliveryExpressTemplateService deliveryTemplates,
            TradeCouponPriceCalculator couponCalculator, TradeDeliveryPriceCalculator deliveryCalculator,
            CartProperties limits, DeliveryQuoteProperties deliveryLimits, PlatformTransactionManager manager) {
        if (limits.getMaxOrderLines() > deliveryLimits.getMaxQuoteLines()
                || limits.getMaxItemQuantity() > deliveryLimits.getMaxItemQuantity()) {
            throw new IllegalArgumentException("结算资源配置不能超过运费计算器保护上界");
        }
        this.access=access;
        this.products=products;
        this.references=references;
        this.carts=carts;
        this.quotes=quotes;
        this.deliveryRows=deliveryRows;
        this.deliveryTemplates=deliveryTemplates;
        this.couponCalculator=couponCalculator;
        this.deliveryCalculator=deliveryCalculator;
        this.limits=limits;
        read=new TransactionTemplate(manager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(5);
        write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(10);
    }

    public AppTradeSettlementRespVO settlement(HttpServletRequest request, String store,
            AppTradeSettlementReqVO body) {
        LocalDateTime started = now();
        MemberStorefrontRespDTO entry = access.check(request, store);
        LoginUser user = access.actor();
        String digest = digest(user, body);
        // 原请求返回原报价；暂停或过期也不续期、不重新计算。
        TradeSettlementQuoteDO previous = quotes.findRequest(
                user.getTenantId(), user.getId(), body.getRequestId());
        if (previous != null) {
            same(previous, digest);
            return view(previous, entry);
        }
        if (!Boolean.TRUE.equals(entry.getCanPurchase())) {
            throw exception0(403, "店铺暂不支持购买");
        }
        List<CartDO> selected = loadSelectedCart(user, body.getCartVersion());
        TradeSettlementSnapshotBO snapshot = calculatePrice(user, body, selected);
        snapshot.setStorefront(entry);
        TradeSettlementQuoteDO saved = saveQuote(user, body, digest, started, snapshot);
        return view(saved, entry);
    }

    /** 订单准备使用：从服务端原报价重建输入，重新读取商品/本人地址/券，不另存一份报价。 */
    public TradeSettlementSnapshotBO recalculateForOrder(TradeSettlementQuoteDO quote) {
        LoginUser user = access.actor();
        if (quote == null || !Objects.equals(user.getTenantId(), quote.getTenantId())
                || !Objects.equals(user.getId(), quote.getUserId())) {
            throw exception0(404, "报价不存在");
        }
        AppTradeSettlementReqVO body = new AppTradeSettlementReqVO();
        body.setCartVersion(quote.getCartVersion());
        body.setAddressId(quote.getAddressId());
        body.setCouponId(quote.getCouponId());
        return calculatePrice(user, body, loadSelectedCart(user, quote.getCartVersion()));
    }

    /** 同一个 RR 快照中读取购物车版本和选中项，不在这里进行 RPC。 */
    private List<CartDO> loadSelectedCart(LoginUser user, Long expectedVersion) {
        List<CartDO> selected = read.execute(status -> {
            Long version = carts.readVersion(user.getTenantId(), user.getId());
            if (version == null) {
                version = 0L;
            }
            if (!Objects.equals(expectedVersion, version)) {
                throw exception0(409, "购物车已变化，请重新读取");
            }
            List<CartDO> all = carts.list(user.getTenantId(), user.getId());
            if (all.size() > 200) {
                throw exception0(503, "购物车超出支持范围");
            }
            return all.stream().filter(row -> Boolean.TRUE.equals(row.getSelected()))
                    .sorted(Comparator.comparing(CartDO::getId)).collect(Collectors.toList());
        });
        if (selected == null || selected.isEmpty() || selected.size() > limits.getMaxOrderLines()) {
            throw exception0(400, "请选择允许数量的有效商品");
        }
        return selected;
    }

    /** 原版读取商品后依次运行计算器；本项目只保留优惠券和运费。 */
    private TradeSettlementSnapshotBO calculatePrice(LoginUser user, AppTradeSettlementReqVO body,
            List<CartDO> selected) {
        Map<Long,ProductTradeSkuRespDTO> skus=products.list(selected.stream().map(CartDO::getSkuId).collect(Collectors.toList()));
        TradeSettlementSnapshotBO snapshot=new TradeSettlementSnapshotBO();
        snapshot.setAddress(references.address(body.getAddressId()));
        List<CouponOrderLineDTO> couponLines=new ArrayList<>();
        List<TradeSettlementSnapshotBO.Item> items=new ArrayList<>();
        long goods=0;
        for(CartDO row:selected) {
            ProductTradeSkuRespDTO product=skus.get(row.getSkuId());
            if(product==null || !Objects.equals(row.getSpuId(),product.getSpuId()) || row.getCount()<1 || row.getCount()>limits.getMaxItemQuantity() || row.getCount()>product.getStock())throw exception0(409,"所选商品已失效、缺货或数量超限，请调整购物车");
            CouponOrderLineDTO input=new CouponOrderLineDTO();
            input.setLineId(row.getId());
            input.setSpuId(product.getSpuId());
            input.setSkuId(product.getSkuId());
            input.setCategoryId(product.getCategoryId());
            input.setQuantity(row.getCount());
            input.setUnitPrice(product.getPrice().longValue());
            couponLines.add(input);
            TradeSettlementSnapshotBO.Item item=new TradeSettlementSnapshotBO.Item();
            item.setCartId(row.getId());
            item.setCartLineVersion(row.getAuthVersion());
            item.setCount(row.getCount());
            item.setProduct(product);
            item.setGoodsPrice(Math.multiplyExact(product.getPrice().longValue(),row.getCount()));
            item.setDiscountPrice(0L);
            item.setUnitDiscountBase(0L);
            item.setUnitDiscountRemainder(0);
            items.add(item);
            goods=Math.addExact(goods,item.getGoodsPrice());
        }
        long discount=0;
        if(body.getCouponId()!=null) {
            CouponRespDTO coupon=references.coupon(body.getCouponId());
            CouponQuoteDTO calculated=couponCalculator.calculate(user.getId(),coupon,couponLines,true);
            snapshot.setCoupon(coupon);
            snapshot.setCouponQuote(calculated);
            discount=calculated.getDiscountPrice();
            Map<Long,CouponQuoteDTO.Line> byLine=calculated.getLines().stream().collect(Collectors.toMap(CouponQuoteDTO.Line::getLineId,x->x));
            for(TradeSettlementSnapshotBO.Item item:items) {
                CouponQuoteDTO.Line line=byLine.get(item.getCartId());
                if(line==null)throw new IllegalStateException("优惠分摊缺失商品行");
                item.setDiscountPrice(line.getDiscountPrice());
                item.setUnitDiscountBase(line.getUnitDiscountBase());
                item.setUnitDiscountRemainder(line.getUnitDiscountRemainder());
            }
        }
        Map<Long,DeliveryTemplateSnapshotBO> templates=deliveryTemplates.getCheckoutSnapshots(items.stream().map(x->x.getProduct().getDeliveryTemplateId()).collect(Collectors.toSet()));
        List<TradeDeliveryLineBO> deliveryLines=new ArrayList<>();
        for(TradeSettlementSnapshotBO.Item item:items) {
            TradeDeliveryLineBO line=new TradeDeliveryLineBO();
            line.setSkuId(item.getProduct().getSkuId());
            line.setTemplateId(item.getProduct().getDeliveryTemplateId());
            line.setQuantity(item.getCount());
            line.setUnitWeight(item.getProduct().getWeight()==null?null:new BigDecimal(item.getProduct().getWeight()));
            line.setPaidPrice(Math.subtractExact(item.getGoodsPrice(),item.getDiscountPrice()));
            deliveryLines.add(line);
        }
        TradeDeliveryQuoteBO delivery=deliveryCalculator.calculate(user.getTenantId(),snapshot.getAddress().getAreaId(),deliveryLines,templates);
        Map<Long,Long> fees=new HashMap<>();
        for(TradeDeliveryQuoteBO.Group group:delivery.getGroups())for(TradeDeliveryQuoteBO.LineFee line:group.getLineFees())if(fees.put(line.getSkuId(),line.getDeliveryPrice())!=null)throw new IllegalStateException("运费重复分摊");
        long lineDiscount=0,lineFee=0,linePay=0;
        for(TradeSettlementSnapshotBO.Item item:items) {
            Long fee=fees.get(item.getProduct().getSkuId());
            if(fee==null)throw new IllegalStateException("运费分摊缺失商品行");
            item.setDeliveryPrice(fee);
            item.setUnitDeliveryBase(fee/item.getCount());
            item.setUnitDeliveryRemainder(Math.toIntExact(fee%item.getCount()));
            item.setPayPrice(Math.addExact(Math.subtractExact(item.getGoodsPrice(),item.getDiscountPrice()),fee));
            safe(item.getGoodsPrice());
            safe(item.getPayPrice());
            lineDiscount=Math.addExact(lineDiscount,item.getDiscountPrice());
            lineFee=Math.addExact(lineFee,fee);
            linePay=Math.addExact(linePay,item.getPayPrice());
        }
        long payable=Math.addExact(Math.subtractExact(goods,discount),delivery.getDeliveryPrice());
        safe(goods);
        safe(discount);
        safe(delivery.getDeliveryPrice());
        safe(payable);
        if(lineDiscount!=discount || lineFee!=delivery.getDeliveryPrice() || linePay!=payable)throw new IllegalStateException("报价总额和行分摊不一致");
        snapshot.setItems(items);
        snapshot.setDelivery(delivery);
        snapshot.setGoodsPrice(goods);
        snapshot.setDiscountPrice(discount);
        snapshot.setDeliveryPrice(delivery.getDeliveryPrice());
        snapshot.setPayPrice(payable);
        return snapshot;
    }

    /** RPC 完成后才进入短写事务，复查购物车与运费版本并保存报价。 */
    private TradeSettlementQuoteDO saveQuote(LoginUser user, AppTradeSettlementReqVO body, String digest,
            LocalDateTime started, TradeSettlementSnapshotBO snapshot) {
        LocalDateTime expiry = started.plusSeconds(limits.getQuoteTtlSeconds());
        if (snapshot.getCoupon() != null && snapshot.getCoupon().getValidEndTime().isBefore(expiry)) {
            expiry = snapshot.getCoupon().getValidEndTime();
        }
        TradeSettlementQuoteDO row=new TradeSettlementQuoteDO();
        row.setTenantId(user.getTenantId());
        row.setUserId(user.getId());
        row.setQuoteId(UUID.randomUUID().toString());
        row.setRequestId(body.getRequestId());
        row.setRequestDigest(digest);
        row.setCartVersion(body.getCartVersion());
        row.setAddressId(body.getAddressId());
        row.setCouponId(body.getCouponId());
        row.setCreatedAt(started);
        row.setExpiresAt(expiry);
        row.setSnapshot(JsonUtils.toJsonString(snapshot));
        if(row.getSnapshot().getBytes(StandardCharsets.UTF_8).length>2_000_000)throw exception0(400,"报价快照超出支持范围");
        TradeSettlementQuoteDO saved=write.execute(status-> {
            Long version=carts.lockVersion(user.getTenantId(),user.getId());
            TradeSettlementQuoteDO found=quotes.findRequest(user.getTenantId(),user.getId(),body.getRequestId());if(found!=null) {
                same(found,digest);return found;
            }
            access.actor();if(!Objects.equals(version,body.getCartVersion()))throw exception0(409,"购物车已变化，请重新报价");
            for(TradeDeliveryQuoteBO.Group item:snapshot.getDelivery().getGroups()) {
                cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO current=deliveryRows.lockById(item.getTemplateId());
                if(current==null || !Integer.valueOf(0).equals(current.getStatus()) || !Objects.equals(current.getAuthVersion(),item.getTemplateVersion()))throw exception0(409,"运费配置已变化，请重新报价");
            }
            LocalDateTime instant=now();if(!instant.isBefore(row.getExpiresAt()))throw exception0(409,"处理期间报价或优惠券已到期，请重新报价");
            quotes.pruneSnapshots(user.getTenantId(),user.getId(),version,instant);
            if(quotes.countActive(user.getTenantId(),user.getId(),instant)>=limits.getMaxActiveQuotesPerMember())throw exception0(429,"有效报价较多，请稍后重新计算");
            if(quotes.insert(row)!=1 || row.getId()==null)throw new IllegalStateException("报价保存失败");return row;
        });
        if(saved==null)throw new IllegalStateException("报价事务未返回结果");
        return saved;
    }
    public AppTradeSettlementRespVO result(HttpServletRequest request,String store,String requestId) {
        MemberStorefrontRespDTO entry=access.check(request,store);
        LoginUser user=access.actor();
        TradeSettlementQuoteDO row=quotes.findRequest(user.getTenantId(),user.getId(),requestId);
        return row==null?null:view(row,entry);
    }
    private AppTradeSettlementRespVO view(TradeSettlementQuoteDO row,MemberStorefrontRespDTO entry) {
        LocalDateTime instant=now();
        AppTradeSettlementRespVO result=new AppTradeSettlementRespVO();
        result.setQuoteId(row.getQuoteId());
        result.setRequestId(row.getRequestId());
        result.setCartVersion(row.getCartVersion());
        result.setAddressId(row.getAddressId());
        result.setCouponId(row.getCouponId());
        result.setServerNow(epoch(instant));
        result.setExpiresAt(epoch(row.getExpiresAt()));
        result.setExpired(!instant.isBefore(row.getExpiresAt()));
        result.setCanPurchase(entry.getCanPurchase());
        result.setCartChanged(!Objects.equals(row.getCartVersion(),carts.readVersion(row.getTenantId(),row.getUserId())));
        result.setBound(row.getBoundOrderId()!=null);
        result.setSnapshotPresent(row.getSnapshot()!=null);
        if(row.getSnapshot()!=null) {
            TradeSettlementSnapshotBO saved=JsonUtils.parseObject(row.getSnapshot(),TradeSettlementSnapshotBO.class);
            if(saved==null || saved.getAddress()==null || saved.getItems()==null || saved.getItems().isEmpty() || !Objects.equals(row.getAddressId(),saved.getAddress().getId()))throw exception0(503,"报价快照不完整");
            result.setCouponName(saved.getCoupon()==null?null:saved.getCoupon().getName());
            result.setGoodsPrice(saved.getGoodsPrice());
            result.setDiscountPrice(saved.getDiscountPrice());
            result.setDeliveryPrice(saved.getDeliveryPrice());
            result.setPayPrice(saved.getPayPrice());
            result.setItems(BeanUtils.toBean(saved.getItems(),AppTradeSettlementRespVO.Line.class));
            AppTradeSettlementRespVO.Address address=BeanUtils.toBean(saved.getAddress(),AppTradeSettlementRespVO.Address.class);
            List<String> names=new ArrayList<>();
            Area area=AreaUtils.getArea(address.getAreaId());
            Set<Integer> seen=new HashSet<>();
            while(area!=null && AreaUtils.isChinaRegion(area.getId()) && seen.add(area.getId())) {
                names.add(0,area.getName());
                area=area.getParent();
            }
            address.setAreaName(String.join(" / ",names));
            result.setAddress(address);
        }
        return result;
    }
    private String digest(LoginUser user,AppTradeSettlementReqVO body) {
        return DigestUtil.sha256Hex("settlement-v1:"+user.getTenantId()+":"+user.getId()+":"+body.getCartVersion()+":"+body.getAddressId()+":"+body.getCouponId());
    }
    private void same(TradeSettlementQuoteDO row,String digest) {
        if(!Objects.equals(row.getRequestDigest(),digest))throw exception0(409,"原报价请求号不能更改购物车、地址或优惠券");
    }
    private void safe(long amount) {
        if(amount<0 || amount>9_007_199_254_740_991L)throw exception0(400,"报价金额超出支持范围");
    }
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
    }
    private long epoch(LocalDateTime time) {
        return time.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    }
}
