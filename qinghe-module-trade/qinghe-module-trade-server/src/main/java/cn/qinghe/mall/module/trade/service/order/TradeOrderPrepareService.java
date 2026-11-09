package cn.qinghe.mall.module.trade.service.order;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.cart.CartMapper;
import cn.qinghe.mall.module.trade.dal.mysql.delivery.DeliveryExpressTemplateMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderPrepareMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeSettlementQuoteMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.service.delivery.bo.TradeDeliveryQuoteBO;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOrderPrepareResult;
import cn.qinghe.mall.module.trade.service.price.TradePriceServiceImpl;
import cn.qinghe.mall.module.trade.service.price.bo.TradeSettlementSnapshotBO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原 TradeOrderUpdateServiceImpl.createOrder 的核价、构造订单/商品项和保存职责。
 * B06/B10：准备/分摊/报价绑定/outbox 同事务提交；跨服务副作用后续由持久流程推进。
 * HTTP先准备再调用正式创建；事件发送器/消费器仍待，PREPARED不能作为可付款订单。
 *
 * @author 芋道源码
 */
@Service
public class TradeOrderPrepareService {
    private final TradeMemberAccess access;
    private final TradePriceServiceImpl prices;
    private final TradeSettlementQuoteMapper quotes;
    private final TradeOrderPrepareMapper preparations;
    private final CartMapper carts;
    private final DeliveryExpressTemplateMapper deliveries;
    private final Validator validator;
    private final TransactionTemplate write;

    public TradeOrderPrepareService(TradeMemberAccess access, TradePriceServiceImpl prices,
            TradeSettlementQuoteMapper quotes, TradeOrderPrepareMapper preparations, CartMapper carts,
            DeliveryExpressTemplateMapper deliveries, Validator validator, PlatformTransactionManager manager) {
        this.access = access;
        this.prices = prices;
        this.quotes = quotes;
        this.preparations = preparations;
        this.carts = carts;
        this.deliveries = deliveries;
        this.validator = validator;
        write = new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(15);
    }

    public TradeOrderPrepareResult prepare(HttpServletRequest request, String store, AppTradeOrderCreateReqVO body) {
        validate(body);
        MemberStorefrontRespDTO entry = access.check(request, store);
        LoginUser user = access.actor();
        String remark = body.getRemark() == null ? "" : body.getRemark().trim();
        String hash = DigestUtil.sha256Hex("create-v1:" + user.getTenantId() + ":" + user.getId()
                + ":" + body.getQuoteId() + ":" + DigestUtil.sha256Hex(remark));
        TradeOrderPrepareDO previous = preparations.find(user.getTenantId(), user.getId(), body.getRequestId());
        if (previous != null) {
            sameRequest(previous, hash);
            return result(previous);
        }
        if (!Boolean.TRUE.equals(entry.getCanPurchase())) {
            throw exception0(403, "店铺暂不支持新购买");
        }
        TradeSettlementQuoteDO quote = quotes.findQuote(user.getTenantId(), user.getId(), body.getQuoteId());
        usable(quote);
        TradeSettlementSnapshotBO old = JsonUtils.parseObject(quote.getSnapshot(), TradeSettlementSnapshotBO.class);

        // 真实商品/本人地址/优惠券读取在本地事务之前；报价只作用户确认依据，不作当前库存锁。
        TradeSettlementSnapshotBO fresh = prices.recalculateForOrder(quote);
        fresh.setStorefront(entry);
        samePrice(old, fresh);
        String snapshot = JsonUtils.toJsonString(fresh);
        if (snapshot.getBytes(StandardCharsets.UTF_8).length > 2_000_000) {
            throw exception0(400, "订单快照超出支持范围");
        }
        TradeOrderPrepareDO saved = write.execute(status -> {
            Long version = carts.lockVersion(user.getTenantId(), user.getId());
            TradeOrderPrepareDO found = preparations.find(user.getTenantId(), user.getId(), body.getRequestId());
            if (found != null) {
                sameRequest(found, hash);
                return found;
            }
            access.actor();
            if (!Objects.equals(version, quote.getCartVersion())) {
                throw exception0(409, "购物车已变化，请重新报价");
            }
            TradeSettlementQuoteDO locked = quotes.lockQuote(user.getTenantId(), user.getId(), body.getQuoteId());
            usable(locked);
            // 运费模板写入先锁父行并递增版本；本地核价结果保存前再核对。
            for (TradeDeliveryQuoteBO.Group group : fresh.getDelivery().getGroups()) {
                DeliveryExpressTemplateDO template = deliveries.lockById(group.getTemplateId());
                if (template == null || !Integer.valueOf(0).equals(template.getStatus())
                        || !Objects.equals(template.getAuthVersion(), group.getTemplateVersion())) {
                    throw exception0(409, "运费配置已变化，请重新报价");
                }
            }
            TradeOrderPrepareDO row = build(user, body, hash, remark, locked, fresh, snapshot);
            if (preparations.insert(row) != 1 || row.getId() == null || row.getId() > 9_007_199_254_740_991L) {
                throw new IllegalStateException("订单准备记录保存失败");
            }
            for (TradeSettlementSnapshotBO.Item line : fresh.getItems()) {
                TradeOrderPrepareItemDO item = buildItem(row, line);
                if (preparations.insertItem(item) != 1 || item.getId() == null || item.getId() > 9_007_199_254_740_991L) {
                    throw new IllegalStateException("订单商品快照保存失败");
                }
            }
            if (quotes.bind(user.getTenantId(), user.getId(), locked.getQuoteId(), row.getId(), now()) != 1) {
                throw exception0(409, "报价已过期或用于另一笔订单");
            }
            String payload = JsonUtils.toJsonString(Collections.singletonMap("preparationId", row.getId()));
            if (preparations.insertPreparedEvent(UUID.randomUUID().toString(), user.getTenantId(), row.getId(), payload, now()) != 1) {
                throw new IllegalStateException("订单待处理事件保存失败");
            }
            access.actor();
            if (!now().isBefore(row.getPrepareDeadline())) {
                throw exception0(409, "保存期间报价已到期，请重新报价");
            }
            return row;
        });
        if (saved == null) {
            throw new IllegalStateException("订单准备事务未返回结果");
        }
        return result(saved);
    }

    /** 已有准备结果允许在报价过期或店铺停止新购买后查询；null 不能证明没有在途请求。 */
    public TradeOrderPrepareResult get(HttpServletRequest request, String store, String requestId) {
        if (requestId == null || !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw exception0(400, "创建请求号不正确");
        }
        access.check(request, store);
        LoginUser user = access.actor();
        TradeOrderPrepareDO row = preparations.find(user.getTenantId(), user.getId(), requestId);
        return row == null ? null : result(row);
    }

    /**
     * 关闭未登记准备的原报价。与prepare保持购物车→报价锁序；迟到请求在usable(locked)处拒绝。
     * 已存在同请求准备时返回false，后续由原创建/补偿流程收敛，绝不根据一次null释放资源。
     */
    public boolean stopUnprepared(HttpServletRequest request,String store,AppTradeOrderCreateReqVO body) {
        validate(body);long checkedAt=System.nanoTime();access.check(request,store);LoginUser actor=access.actor();
        String remark=body.getRemark()==null?"":body.getRemark().trim();
        String hash=DigestUtil.sha256Hex("create-v1:"+actor.getTenantId()+":"+actor.getId()+":"+body.getQuoteId()+":"+DigestUtil.sha256Hex(remark));
        Boolean stopped=write.execute(status -> {
            if(carts.lockVersion(actor.getTenantId(),actor.getId())==null)throw exception0(409,"原购物车主体不存在，需核对订单");
            TradeOrderPrepareDO previous=preparations.find(actor.getTenantId(),actor.getId(),body.getRequestId());
            if(previous!=null){sameRequest(previous,hash);return false;}
            TradeSettlementQuoteDO quote=quotes.lockQuote(actor.getTenantId(),actor.getId(),body.getQuoteId());
            if(quote==null)throw exception0(404,"本人原报价不存在，需核对订单");
            // 另一请求已经绑定时，原报价同样不可能再创建；不清理它的订单或资源。
            if(quote.getBoundOrderId()==null && quote.getSnapshot()!=null
                    && quotes.closeUnprepared(actor.getTenantId(),actor.getId(),body.getQuoteId())!=1)
                throw exception0(409,"原报价状态已变化，请重新查询");
            LoginUser current=access.actor();
            if(!Objects.equals(actor.getId(),current.getId()) || !Objects.equals(actor.getTenantId(),current.getTenantId()))
                throw exception0(403,"当前会员身份已变化");
            if(System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"资格核对已过时，请查询原请求");
            return true;
        });
        if(stopped==null)throw new IllegalStateException("终止准备事务无结果");return stopped;
    }

    private TradeOrderPrepareDO build(LoginUser user, AppTradeOrderCreateReqVO body, String hash,
            String remark, TradeSettlementQuoteDO quote, TradeSettlementSnapshotBO snapshot, String json) {
        TradeOrderPrepareDO row = new TradeOrderPrepareDO();
        row.setTenantId(user.getTenantId());
        row.setUserId(user.getId());
        row.setRequestId(body.getRequestId());
        row.setRequestHash(hash);
        row.setQuoteId(quote.getQuoteId());
        row.setNo("QH" + UUID.randomUUID().toString().replace("-", ""));
        row.setStage("PREPARED");
        row.setAuthVersion(0L);
        row.setRemark(remark);
        row.setSnapshot(json);
        row.setProductCount(snapshot.getItems().stream().mapToInt(TradeSettlementSnapshotBO.Item::getCount).sum());
        row.setTotalPrice(snapshot.getGoodsPrice());
        row.setDiscountPrice(snapshot.getDiscountPrice());
        row.setDeliveryPrice(snapshot.getDeliveryPrice());
        row.setPayPrice(snapshot.getPayPrice());
        row.setPrepareDeadline(quote.getExpiresAt());
        row.setCreatedAt(now());
        row.setUpdatedAt(row.getCreatedAt());
        return row;
    }

    private TradeOrderPrepareItemDO buildItem(TradeOrderPrepareDO row, TradeSettlementSnapshotBO.Item line) {
        TradeOrderPrepareItemDO item = BeanUtils.toBean(line, TradeOrderPrepareItemDO.class);
        ProductTradeSkuRespDTO product = line.getProduct();
        item.setTenantId(row.getTenantId());
        item.setPreparationId(row.getId());
        item.setSpuId(product.getSpuId());
        item.setSpuName(product.getName());
        item.setSkuId(product.getSkuId());
        item.setSpuVersion(product.getSpuVersion());
        item.setSkuVersion(product.getSkuVersion());
        item.setProperties(JsonUtils.toJsonString(product.getProperties()));
        item.setPicUrl(product.getPicUrl());
        item.setPrice(product.getPrice());
        return item;
    }

    private void samePrice(TradeSettlementSnapshotBO old, TradeSettlementSnapshotBO fresh) {
        if (old == null || old.getItems() == null || old.getItems().size() != fresh.getItems().size()
                || !Objects.equals(old.getAddress(), fresh.getAddress()) || !Objects.equals(old.getCoupon(), fresh.getCoupon())
                || !Objects.equals(old.getCouponQuote(), fresh.getCouponQuote()) || !Objects.equals(old.getDelivery(), fresh.getDelivery())
                || !Objects.equals(old.getStorefront(), fresh.getStorefront())
                || !Objects.equals(old.getGoodsPrice(), fresh.getGoodsPrice()) || !Objects.equals(old.getDiscountPrice(), fresh.getDiscountPrice())
                || !Objects.equals(old.getDeliveryPrice(), fresh.getDeliveryPrice()) || !Objects.equals(old.getPayPrice(), fresh.getPayPrice())) {
            throw exception0(409, "商品、收货信息、优惠或运费已变化，请重新确认报价");
        }
        for (int i = 0; i < old.getItems().size(); i++) {
            TradeSettlementSnapshotBO.Item before = old.getItems().get(i), after = fresh.getItems().get(i);
            if (!Objects.equals(before.getCartId(), after.getCartId()) || !Objects.equals(before.getCartLineVersion(), after.getCartLineVersion())
                    || !Objects.equals(before.getCount(), after.getCount()) || !sameProduct(before.getProduct(), after.getProduct())) {
                throw exception0(409, "所选商品已变化，请重新确认报价");
            }
        }
    }

    private boolean sameProduct(ProductTradeSkuRespDTO a, ProductTradeSkuRespDTO b) {
        // 库存允许减少但仍够买，真正扣减时由 product 条件 SQL 保护；资料或价格变化须重新确认。
        return a != null && b != null && Objects.equals(a.getTenantId(), b.getTenantId())
                && Objects.equals(a.getSkuId(), b.getSkuId()) && Objects.equals(a.getSpuId(), b.getSpuId())
                && Objects.equals(a.getSpuVersion(), b.getSpuVersion()) && Objects.equals(a.getSkuVersion(), b.getSkuVersion())
                && Objects.equals(a.getPrice(), b.getPrice()) && Objects.equals(a.getCategoryId(), b.getCategoryId())
                && Objects.equals(a.getDeliveryTemplateId(), b.getDeliveryTemplateId()) && Objects.equals(a.getWeight(), b.getWeight())
                && Objects.equals(a.getProperties(), b.getProperties()) && Objects.equals(a.getName(), b.getName())
                && Objects.equals(a.getPicUrl(), b.getPicUrl());
    }

    private void usable(TradeSettlementQuoteDO quote) {
        if (quote == null) {
            throw exception0(404, "报价不存在");
        }
        if (quote.getBoundOrderId() != null || quote.getSnapshot() == null || !now().isBefore(quote.getExpiresAt())) {
            throw exception0(409, "报价已失效或用于原订单，请查询原结果或重新报价");
        }
    }

    private void sameRequest(TradeOrderPrepareDO row, String hash) {
        if (!hash.equals(row.getRequestHash())) {
            throw exception0(409, "原创建请求号不能更换报价或备注");
        }
    }

    private void validate(AppTradeOrderCreateReqVO body) {
        if (body == null || !validator.validate(body).isEmpty()
                || body.getRemark()!=null && body.getRemark().chars().anyMatch(Character::isISOControl)) {
            throw exception0(400, "创建请求号、报价或备注不正确");
        }
    }

    private TradeOrderPrepareResult result(TradeOrderPrepareDO row) {
        TradeOrderPrepareResult result = BeanUtils.toBean(row, TradeOrderPrepareResult.class);
        result.setPreparationId(row.getId());
        return result;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);
    }
}
