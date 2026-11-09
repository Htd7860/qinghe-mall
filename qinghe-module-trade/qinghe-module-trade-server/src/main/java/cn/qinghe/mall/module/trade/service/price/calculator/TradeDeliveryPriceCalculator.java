package cn.qinghe.mall.module.trade.service.price.calculator;

import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.*;
import cn.qinghe.mall.module.trade.framework.delivery.DeliveryQuoteProperties;
import cn.qinghe.mall.module.trade.service.delivery.bo.*;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取yudao TradeDeliveryPriceCalculator的按模板分组、首/续费、包邮和行分摊流程。
 * B05：可信服务端商品事实/折后金额输入，先配送后包邮，Decimal精确计量，缺数据拒绝而非按0处理。
 * 现由TradePriceServiceImpl从真实会员地址/商品构造输入；不能接浏览器自由提交的价格/重量/模板快照。
 * @author jason
 */
@Component
public class TradeDeliveryPriceCalculator {
    private final DeliveryQuoteProperties settings;
    public TradeDeliveryPriceCalculator(DeliveryQuoteProperties settings) { this.settings=settings; }

    public TradeDeliveryQuoteBO calculate(Long tenantId,Integer areaId,List<TradeDeliveryLineBO> lines,
            Map<Long,DeliveryTemplateSnapshotBO> snapshots) {
        try { return calculateChecked(tenantId,areaId,lines,snapshots); }
        catch (ArithmeticException error) { throw exception0(400,"运费或商品金额超出支持范围"); }
    }
    private TradeDeliveryQuoteBO calculateChecked(Long tenantId,Integer areaId,List<TradeDeliveryLineBO> lines,
            Map<Long,DeliveryTemplateSnapshotBO> snapshots) {
        if (tenantId==null || tenantId<=0 || !AreaUtils.isChinaRegion(areaId) || AreaUtils.getArea(areaId).getType()<3 || !AreaUtils.getArea(areaId).getChildren().isEmpty()) {
            throw exception0(400,"请提供完整有效的收货地区");
        }
        if (lines==null || lines.isEmpty() || lines.size()>settings.getMaxQuoteLines() || snapshots==null) {
            throw exception0(400,"请选择合法数量的报价商品");
        }
        Set<Long> seen=new HashSet<>();Map<Long,List<TradeDeliveryLineBO>> groups=new TreeMap<>();
        for (TradeDeliveryLineBO line:lines) {
            if (line==null || line.getSkuId()==null || line.getSkuId()<=0 || !seen.add(line.getSkuId())
                    || line.getTemplateId()==null || line.getTemplateId()<=0 || line.getQuantity()==null || line.getQuantity()<=0
                    || line.getQuantity()>settings.getMaxItemQuantity() || line.getPaidPrice()==null || line.getPaidPrice()<0) {
                throw exception0(400,"报价商品数量、身份或优惠后金额不正确");
            }
            groups.computeIfAbsent(line.getTemplateId(),key -> new ArrayList<>()).add(line);
        }
        List<TradeDeliveryQuoteBO.Group> result=new ArrayList<>();long total=0;
        for (Map.Entry<Long,List<TradeDeliveryLineBO>> entry:groups.entrySet()) {
            DeliveryTemplateSnapshotBO snapshot=snapshots.get(entry.getKey());
            if (snapshot==null || snapshot.getTemplate()==null || !Objects.equals(snapshot.getTemplate().getId(),entry.getKey())
                    || !Objects.equals(snapshot.getTemplate().getTenantId(),tenantId) || !Integer.valueOf(0).equals(snapshot.getTemplate().getStatus())
                    || snapshot.getTemplate().getAuthVersion()==null || snapshot.getTemplate().getAuthVersion()<0
                    || snapshot.getCharges()==null || snapshot.getFrees()==null) { throw exception0(400,"配送模板缺失或不属于当前有效租户配置"); }
            int mode=snapshot.getTemplate().getChargeMode()==null?0:snapshot.getTemplate().getChargeMode();
            if (mode!=1 && mode!=2) { throw exception0(400,"配送计费方式不正确"); }
            List<TradeDeliveryLineBO> grouped=new ArrayList<>(entry.getValue());grouped.sort(Comparator.comparing(TradeDeliveryLineBO::getSkuId));
            long quantity=0,paid=0;BigDecimal chargeValue=BigDecimal.ZERO;
            for (TradeDeliveryLineBO line:grouped) {
                quantity=Math.addExact(quantity,line.getQuantity().longValue());paid=Math.addExact(paid,line.getPaidPrice());
                chargeValue=chargeValue.add(measure(line,mode));
            }
            // 没有收费地区配置就拒绝，即使包邮门槛已满足，也不能绕过地区限制或缺失重量。
            DeliveryExpressTemplateChargeDO charge=charge(snapshot.getCharges(),areaId);
            if (charge==null) { throw exception0(400,"当前收货地区不支持配送"); }
            validRule(tenantId,entry.getKey(),charge.getTenantId(),charge.getTemplateId(),charge.getId());
            if (!Integer.valueOf(mode).equals(charge.getChargeMode()) || !positive(charge.getStartCount()) || !positive(charge.getExtraCount())
                    || charge.getStartPrice()==null || charge.getStartPrice()<0 || charge.getExtraPrice()==null || charge.getExtraPrice()<0
                    || (mode==1 && (charge.getStartCount().stripTrailingZeros().scale()>0 || charge.getExtraCount().stripTrailingZeros().scale()>0))) {
                throw exception0(400,"配送收费配置不完整");
            }
            DeliveryExpressTemplateFreeDO free=free(snapshot.getFrees(),areaId);
            boolean freeApplied=false;
            if (free!=null) {
                validRule(tenantId,entry.getKey(),free.getTenantId(),free.getTemplateId(),free.getId());
                if (free.getFreePrice()==null || free.getFreePrice()<0 || free.getFreeCount()==null || free.getFreeCount()<0
                        || (free.getFreePrice()==0 && free.getFreeCount()==0)) { throw exception0(400,"包邮门槛配置不正确"); }
                // freeCount始终是件数，重量模板也不能用kg满足件数门槛；两个已配置门槛须同时满足。
                freeApplied=paid>=free.getFreePrice() && quantity>=free.getFreeCount();
            }
            long fee=freeApplied?0:calculateFee(chargeValue,charge);
            TradeDeliveryQuoteBO.Group group=new TradeDeliveryQuoteBO.Group();
            group.setTemplateId(entry.getKey());group.setTemplateVersion(snapshot.getTemplate().getAuthVersion());group.setChargeMode(mode);group.setAreaId(areaId);
            group.setChargeRuleId(charge.getId());group.setChargeDefault(charge.getDefaultRule());group.setChargeAreas(new ArrayList<>(charge.getAreaIds()));
            group.setStartCount(charge.getStartCount());group.setStartPrice(charge.getStartPrice());group.setExtraCount(charge.getExtraCount());group.setExtraPrice(charge.getExtraPrice());
            if (free!=null) {
                group.setFreeRuleId(free.getId());group.setFreeDefault(free.getDefaultRule());group.setFreeAreas(new ArrayList<>(free.getAreaIds()));
                group.setFreePrice(free.getFreePrice());group.setFreeCount(free.getFreeCount());
            }
            group.setFreeApplied(freeApplied);group.setQuantity(quantity);group.setPaidPrice(paid);group.setChargeValue(chargeValue);group.setDeliveryPrice(fee);
            group.setLineFees(allocate(grouped,mode,chargeValue,fee));result.add(group);total=Math.addExact(total,fee);
        }
        TradeDeliveryQuoteBO quote=new TradeDeliveryQuoteBO();quote.setDeliveryPrice(total);quote.setGroups(result);return quote;
    }

    /** 原版首费+ceil(超出计量/续费计量)*续费，改为精确Decimal及溢出检查。 */
    private long calculateFee(BigDecimal value,DeliveryExpressTemplateChargeDO charge) {
        if (value.compareTo(charge.getStartCount())<=0) { return charge.getStartPrice().longValue(); }
        BigDecimal extra=value.subtract(charge.getStartCount()).divide(charge.getExtraCount(),0,RoundingMode.CEILING);
        return BigDecimal.valueOf(charge.getStartPrice()).add(extra.multiply(BigDecimal.valueOf(charge.getExtraPrice()))).longValueExact();
    }
    private BigDecimal measure(TradeDeliveryLineBO line,int mode) {
        if (mode==1) { return BigDecimal.valueOf(line.getQuantity()); }
        BigDecimal weight=line.getUnitWeight();
        if (!positive(weight) || weight.scale()>6 || weight.precision()-weight.scale()>12) { throw exception0(400,"按重量配送的商品缺少有效重量"); }
        return weight.multiply(BigDecimal.valueOf(line.getQuantity()));
    }
    private boolean positive(BigDecimal value) { return value!=null && value.signum()>0; }
    private void validRule(Long tenant,Long template,Long ruleTenant,Long ruleTemplate,Long ruleId) {
        if (ruleId==null || ruleId<=0 || !Objects.equals(tenant,ruleTenant) || !Objects.equals(template,ruleTemplate)) {
            throw exception0(400,"配送规则身份不正确");
        }
    }
    private boolean matches(List<Integer> ids,Integer area) {
        if (ids==null) { throw exception0(400,"配送区域配置缺失"); }
        return ids.stream().anyMatch(id -> AreaUtils.contains(id,area));
    }
    private DeliveryExpressTemplateChargeDO charge(List<DeliveryExpressTemplateChargeDO> rows,Integer area) {
        DeliveryExpressTemplateChargeDO specific=null,fallback=null;
        for (DeliveryExpressTemplateChargeDO row:rows) {
            if (row==null || row.getDefaultRule()==null || row.getAreaIds()==null) { throw exception0(400,"配送区域配置缺失"); }
            if (row.getDefaultRule()) {
                if (fallback!=null || !row.getAreaIds().isEmpty()) { throw exception0(400,"默认配送规则冲突"); }fallback=row;
            } else if (matches(row.getAreaIds(),area)) {
                if (specific!=null) { throw exception0(400,"配送地区规则重叠"); }specific=row;
            }
        }
        return specific!=null?specific:fallback;
    }
    private DeliveryExpressTemplateFreeDO free(List<DeliveryExpressTemplateFreeDO> rows,Integer area) {
        DeliveryExpressTemplateFreeDO specific=null,fallback=null;
        for (DeliveryExpressTemplateFreeDO row:rows) {
            if (row==null || row.getDefaultRule()==null || row.getAreaIds()==null) { throw exception0(400,"包邮区域配置缺失"); }
            if (row.getDefaultRule()) {
                if (fallback!=null || !row.getAreaIds().isEmpty()) { throw exception0(400,"默认包邮规则冲突"); }fallback=row;
            } else if (matches(row.getAreaIds(),area)) {
                if (specific!=null) { throw exception0(400,"包邮地区规则重叠"); }specific=row;
            }
        }
        return specific!=null?specific:fallback;
    }
    private List<TradeDeliveryQuoteBO.LineFee> allocate(List<TradeDeliveryLineBO> lines,int mode,BigDecimal totalValue,long totalFee) {
        List<Share> shares=new ArrayList<>();long allocated=0;
        for (TradeDeliveryLineBO line:lines) {
            Share share=new Share();share.skuId=line.getSkuId();
            BigDecimal numerator=measure(line,mode).multiply(BigDecimal.valueOf(totalFee));
            share.fee=numerator.divide(totalValue,0,RoundingMode.DOWN).longValueExact();
            share.remainder=numerator.subtract(totalValue.multiply(BigDecimal.valueOf(share.fee)));
            allocated=Math.addExact(allocated,share.fee);shares.add(share);
        }
        int left=Math.toIntExact(Math.subtractExact(totalFee,allocated));
        if (left<0 || left>shares.size()) { throw new IllegalStateException("运费分摊余数异常"); }
        shares.sort(Comparator.comparing((Share share) -> share.remainder).reversed().thenComparing(share -> share.skuId));
        for (int i=0;i<left;i++) { shares.get(i).fee=Math.addExact(shares.get(i).fee,1L); }
        shares.sort(Comparator.comparing(share -> share.skuId));
        return shares.stream().map(share -> { TradeDeliveryQuoteBO.LineFee line=new TradeDeliveryQuoteBO.LineFee();
            line.setSkuId(share.skuId);line.setDeliveryPrice(share.fee);return line; }).collect(Collectors.toList());
    }
    private static class Share { private Long skuId;private long fee;private BigDecimal remainder; }
}
