package cn.qinghe.mall.module.product.service.stock;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.stock.ProductStockReleaseReason;
import cn.qinghe.mall.module.product.api.stock.dto.ProductOrderStockReqDTO;
import cn.qinghe.mall.module.product.api.stock.dto.ProductOrderStockRespDTO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuSkuSummary;
import cn.qinghe.mall.module.product.dal.dataobject.stock.ProductOrderStockDO;
import cn.qinghe.mall.module.product.dal.dataobject.stock.ProductOrderStockItemDO;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.qinghe.mall.module.product.dal.mysql.stock.ProductOrderStockMapper;
import cn.qinghe.mall.module.product.service.search.ProductSearchChanges;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.product.api.stock.dto.ProductOrderStockRespDTO.*;

/**
 * 择取 yudao updateSkuStock 的批量条件增减，补订单幂等和取消先到的关闭记录。
 * 仅供可信订单流程使用：不得直接把会员 Bearer 或浏览器字段当库存写入授权。
 * 扣减/查询/创建失败关闭已有专用trade身份入口；已创建订单的未付取消仍须先核实支付结果。
 * 扣减不是付款，不提前增加销量；部分售后由ProductAfterSaleStockService按原售后号回补。
 *
 * @author 芋道源码
 */
@Service
public class ProductOrderStockService {
    private final ProductOrderStockMapper orders;
    private final ProductSkuMapper skus;
    private final ProductSearchChanges searchChanges;

    public ProductOrderStockService(ProductOrderStockMapper orders, ProductSkuMapper skus, ProductSearchChanges searchChanges) {
        this.orders = orders;
        this.skus = skus;
        this.searchChanges = searchChanges;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW, rollbackFor=Exception.class,
            isolation=Isolation.READ_COMMITTED, timeout=15)
    public ProductOrderStockRespDTO deduct(ProductOrderStockReqDTO input) {
        Long tenant = tenant();
        ProductOrderStockReqDTO request = normalize(input);
        String hash = digest(tenant, request);
        ProductOrderStockDO seed = new ProductOrderStockDO();
        seed.setTenantId(tenant);
        seed.setOrderId(request.getOrderId());
        seed.setUserId(request.getUserId());
        seed.setRequestHash(hash);
        seed.setRequestSnapshot(JsonUtils.toJsonString(request));
        seed.setStatus(DEDUCTED);
        int inserted = orders.ensure(seed);
        ProductOrderStockDO operation = required(tenant, request.getOrderId(), request.getUserId(), true);

        // 取消先到时已有 CLOSED 记录，晚到扣减不能重新开启订单库存。
        if (operation.getStatus() == CLOSED) {
            return result(operation);
        }
        if (!hash.equals(operation.getRequestHash())) {
            throw exception0(409, "原订单库存请求不能更改商品、数量、价格或版本");
        }
        if (inserted == 0) {
            return result(operation);
        }
        if (inserted != 1 || operation.getStatus() != DEDUCTED) {
            throw new IllegalStateException("订单库存登记异常");
        }

        Map<Long, List<ProductOrderStockReqDTO.Item>> grouped = request.getItems().stream()
                .collect(Collectors.groupingBy(ProductOrderStockReqDTO.Item::getSpuId, TreeMap::new, Collectors.toList()));
        Map<Long, ProductSpuDO> parents = lockParents(tenant, grouped.keySet());
        String actor = "order:" + request.getOrderId();
        for (Map.Entry<Long, List<ProductOrderStockReqDTO.Item>> group : grouped.entrySet()) {
            ProductSpuDO spu = parents.get(group.getKey());
            List<ProductOrderStockReqDTO.Item> items = group.getValue();
            items.sort(Comparator.comparing(ProductOrderStockReqDTO.Item::getSkuId));
            for (ProductOrderStockReqDTO.Item item : items) {
                if (!Boolean.FALSE.equals(spu.getDeleted()) || !Integer.valueOf(1).equals(spu.getStatus())
                        || !Objects.equals(spu.getAuthVersion(), item.getSpuVersion())) {
                    throw exception0(409, "商品状态或资料已变化，请重新确认订单");
                }
                ProductSkuDO sku = orders.lockSku(tenant, item.getSpuId(), item.getSkuId());
                if (sku == null || !Boolean.FALSE.equals(sku.getDeleted()) || !Integer.valueOf(0).equals(sku.getStatus())
                        || !Objects.equals(sku.getAuthVersion(), item.getSkuVersion())
                        || !Objects.equals(sku.getPrice(), item.getUnitPrice()) || sku.getStock() < item.getQuantity()) {
                    throw exception0(409, "商品规格、价格或库存已变化，请重新确认订单");
                }
                if (orders.deduct(tenant, item, actor) != 1) {
                    throw exception0(409, "库存不足或商品已变化");
                }
                ProductOrderStockItemDO record = BeanUtils.toBean(item, ProductOrderStockItemDO.class);
                record.setTenantId(tenant);
                record.setOperationId(operation.getId());
                record.setBeforeStock(sku.getStock());
                record.setAfterStock(sku.getStock() - item.getQuantity());
                if (orders.insertItem(record) != 1) {
                    throw new IllegalStateException("订单库存明细保存失败");
                }
            }
            summarize(tenant, group.getKey(), actor);
        }
        // 全部SPU/SKU操作完成后只标记一次，避免在下一件商品加锁前持有搜索进度锁。
        searchChanges.mark(tenant);
        return result(operation);
    }

    /**
     * 整单创建失败/确定未付关闭后回补原数量；不用于已支付订单或部分售后。
     * 调用方须先确认支付关闭结果，不能因 RPC 超时直接调用此方法。
     */
    @Transactional(propagation=Propagation.REQUIRES_NEW, rollbackFor=Exception.class,
            isolation=Isolation.READ_COMMITTED, timeout=15)
    public ProductOrderStockRespDTO release(Long orderId, Long userId, ProductStockReleaseReason reason) {
        Long tenant = tenant();
        identities(orderId, userId);
        if (reason == null) {
            throw exception0(400, "请选择确定的库存回补依据");
        }
        ProductOrderStockDO seed = new ProductOrderStockDO();
        seed.setTenantId(tenant);
        seed.setOrderId(orderId);
        seed.setUserId(userId);
        seed.setStatus(CLOSED);
        seed.setReleaseReason(reason.name());
        orders.ensure(seed);
        ProductOrderStockDO operation = required(tenant, orderId, userId, true);
        if (operation.getStatus() == CLOSED || operation.getStatus() == RELEASED) {
            return result(operation);
        }
        List<ProductOrderStockItemDO> records = records(operation);
        if (records.stream().anyMatch(record -> record.getRestoredCount()==null || record.getRestoredCount()!=0)) {
            throw exception0(409, "原订单已有售后回补，不能再按整单未付取消恢复库存");
        }
        lockParents(tenant, records.stream().map(ProductOrderStockItemDO::getSpuId).collect(Collectors.toSet()));
        records.sort(Comparator.comparing(ProductOrderStockItemDO::getSpuId).thenComparing(ProductOrderStockItemDO::getSkuId));
        String actor = "order:" + orderId;
        Set<Long> affected = new TreeSet<>();
        for (ProductOrderStockItemDO record : records) {
            ProductSkuDO sku = orders.lockSku(tenant, record.getSpuId(), record.getSkuId());
            if (sku == null) {
                throw exception0(409, "原订单SKU缺失，库存回补需核查");
            }
            long after = (long) sku.getStock() + record.getQuantity();
            if (after > Integer.MAX_VALUE || orders.restore(tenant, record.getSpuId(), record.getSkuId(),
                    record.getQuantity(), actor) != 1) {
                throw exception0(409, "回补后库存超出上限，需核查原订单");
            }
            if (orders.releaseItem(tenant, operation.getId(), record.getId(), sku.getStock(), (int) after) != 1) {
                throw new IllegalStateException("库存回补明细保存失败");
            }
            affected.add(record.getSpuId());
        }
        for (Long spuId : affected) {
            summarize(tenant, spuId, actor);
        }
        if (orders.release(tenant, operation.getId(), reason.name()) != 1) {
            throw new IllegalStateException("订单库存状态更新失败");
        }
        operation.setStatus(RELEASED);
        operation.setReleaseReason(reason.name());
        searchChanges.mark(tenant);
        return result(operation);
    }

    /** null 只表示暂未查到已提交记录，不证明没有在途扣减。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW, readOnly=true,
            isolation=Isolation.REPEATABLE_READ, timeout=5)
    public ProductOrderStockRespDTO get(Long orderId, Long userId) {
        Long tenant = tenant();
        identities(orderId, userId);
        ProductOrderStockDO operation = required(tenant, orderId, userId, false);
        return operation == null ? null : result(operation);
    }

    private Map<Long, ProductSpuDO> lockParents(Long tenant, Collection<Long> ids) {
        Map<Long, ProductSpuDO> parents = new TreeMap<>();
        for (Long id : new TreeSet<>(ids)) {
            ProductSpuDO row = orders.lockSpu(tenant, id);
            if (row == null) {
                throw exception0(409, "原商品记录缺失，库存操作需核查");
            }
            parents.put(id, row);
        }
        return parents;
    }

    private void summarize(Long tenant, Long spu, String actor) {
        ProductSpuSkuSummary summary = skus.summarize(tenant, spu);
        if (summary == null || summary.getStock() == null || summary.getStock() < 0
                || summary.getStock() > Integer.MAX_VALUE) {
            throw exception0(409, "商品可售总库存超出上限");
        }
        if (orders.summarize(tenant, spu, summary.getStock().intValue(), actor) != 1) {
            throw new IllegalStateException("商品库存汇总更新失败");
        }
    }

    private ProductOrderStockDO required(Long tenant, Long order, Long user, boolean lock) {
        ProductOrderStockDO row = lock ? orders.lock(tenant, order) : orders.get(tenant, order);
        if (row == null && lock) {
            throw new IllegalStateException("订单库存登记缺失");
        }
        if (row != null && !Objects.equals(user, row.getUserId())) {
            throw exception0(409, "原订单库存主体不一致");
        }
        return row;
    }

    private List<ProductOrderStockItemDO> records(ProductOrderStockDO operation) {
        List<ProductOrderStockItemDO> records = orders.items(operation.getTenantId(), operation.getId());
        ProductOrderStockReqDTO original = JsonUtils.parseObject(operation.getRequestSnapshot(), ProductOrderStockReqDTO.class);
        if (original == null || original.getItems() == null || records.isEmpty() || records.size() > 200
                || records.size() != original.getItems().size()) {
            throw new IllegalStateException("原订单库存明细不完整");
        }
        return records;
    }

    private ProductOrderStockRespDTO result(ProductOrderStockDO operation) {
        ProductOrderStockRespDTO response = new ProductOrderStockRespDTO();
        response.setOrderId(operation.getOrderId());
        response.setUserId(operation.getUserId());
        response.setStatus(operation.getStatus());
        response.setReleaseReason(operation.getReleaseReason());
        response.setItems(operation.getStatus() == CLOSED ? Collections.emptyList()
                : BeanUtils.toBean(records(operation), ProductOrderStockRespDTO.Item.class));
        return response;
    }

    private ProductOrderStockReqDTO normalize(ProductOrderStockReqDTO input) {
        if (input == null || input.getItems() == null || input.getItems().isEmpty() || input.getItems().size() > 200) {
            throw exception0(400, "订单库存商品数量不正确");
        }
        identities(input.getOrderId(), input.getUserId());
        ProductOrderStockReqDTO request = new ProductOrderStockReqDTO();
        request.setOrderId(input.getOrderId());
        request.setUserId(input.getUserId());
        List<ProductOrderStockReqDTO.Item> items = new ArrayList<>();
        Set<Long> lines = new HashSet<>(), skuIds = new HashSet<>();
        for (ProductOrderStockReqDTO.Item item : input.getItems()) {
            if (item == null || !id(item.getOrderItemId()) || !id(item.getSpuId()) || !id(item.getSkuId())
                    || !lines.add(item.getOrderItemId()) || !skuIds.add(item.getSkuId())
                    || item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > 9999
                    || item.getUnitPrice() == null || item.getUnitPrice() < 1 || item.getUnitPrice() > 2_000_000_000
                    || item.getSpuVersion() == null || item.getSpuVersion() < 0
                    || item.getSkuVersion() == null || item.getSkuVersion() < 0) {
                throw exception0(400, "订单库存商品身份、价格、数量或版本不正确");
            }
            items.add(BeanUtils.toBean(item, ProductOrderStockReqDTO.Item.class));
        }
        items.sort(Comparator.comparing(ProductOrderStockReqDTO.Item::getOrderItemId));
        request.setItems(items);
        return request;
    }

    private String digest(Long tenant, ProductOrderStockReqDTO request) {
        StringBuilder text = new StringBuilder("order-stock-v1|").append(tenant).append('|')
                .append(request.getOrderId()).append('|').append(request.getUserId());
        for (ProductOrderStockReqDTO.Item item : request.getItems()) {
            text.append('|').append(item.getOrderItemId()).append(':').append(item.getSpuId()).append(':')
                    .append(item.getSkuId()).append(':').append(item.getQuantity()).append(':').append(item.getUnitPrice())
                    .append(':').append(item.getSpuVersion()).append(':').append(item.getSkuVersion());
        }
        return DigestUtil.sha256Hex(text.toString());
    }

    private Long tenant() {
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant <= 0 || TenantContextHolder.isIgnore()) {
            throw exception0(403, "订单库存需要可信的明确租户上下文");
        }
        return tenant;
    }

    private void identities(Long order, Long user) {
        if (!id(order) || !id(user)) {
            throw exception0(400, "订单或会员编号不正确");
        }
    }

    private boolean id(Long value) {
        return value != null && value > 0 && value <= 9_007_199_254_740_991L;
    }
}
