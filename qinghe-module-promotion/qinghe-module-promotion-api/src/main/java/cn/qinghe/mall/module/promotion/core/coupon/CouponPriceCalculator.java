package cn.qinghe.mall.module.promotion.core.coupon;
import cn.qinghe.mall.module.promotion.api.coupon.dto.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 直接择取TradeCouponPriceCalculator的范围→门槛→优惠→分摊流程。
 * B05差异：结束不含、整数长整型/溢出检查、优惠封顶可抵完商品、稳定最大余数与单位快照。
 * @author 芋道源码
 */
public final class CouponPriceCalculator {
    private CouponPriceCalculator() {}
    public static CouponQuoteDTO calculate(CouponRespDTO coupon,List<CouponOrderLineDTO> authoritativeLines,
            boolean normalOrder,LocalDateTime now) {
        List<CouponOrderLineDTO> lines=orderedLines(authoritativeLines);
        if(!normalOrder)throw exception0(400,"该订单类型不能使用优惠券");
        validateSnapshot(coupon);
        if(!Integer.valueOf(1).equals(coupon.getStatus()))throw exception0(409,"优惠券已占用、使用、过期或作废");
        if(now==null || now.isBefore(coupon.getValidStartTime()) || !now.isBefore(coupon.getValidEndTime()))throw exception0(409,"优惠券不在有效期内");
        Set<Long> scope=new HashSet<>(coupon.getProductScopeValues());
        List<Share> shares=new ArrayList<>();long eligible=0;
        for(CouponOrderLineDTO line:lines) {
            Share share=new Share();share.input=line;share.goods=Math.multiplyExact(line.getUnitPrice(),line.getQuantity().longValue());
            share.matched=coupon.getProductScope()==1 || coupon.getProductScope()==2 && scope.contains(line.getSpuId())
                    || coupon.getProductScope()==3 && scope.contains(line.getCategoryId());
            if(share.matched)eligible=Math.addExact(eligible,share.goods);
            shares.add(share);
        }
        if(eligible==0 || eligible<coupon.getUsePrice())throw exception0(400,"适用商品金额未达到优惠券门槛");
        // 与原版一致：百分比表示实付比例，例如80为八折；保留整数截断实付的语义。
        long reduction=coupon.getDiscountType()==1?coupon.getDiscountPrice().longValue():
                Math.min(coupon.getDiscountLimitPrice().longValue(),Math.subtractExact(eligible,Math.multiplyExact(eligible,coupon.getDiscountPercent().longValue())/100));
        reduction=Math.min(reduction,eligible);
        if(reduction<=0)throw exception0(400,"当前商品没有可优惠金额");
        long allocated=0;BigInteger denominator=BigInteger.valueOf(eligible);
        List<Share> matched=new ArrayList<>();
        for(Share share:shares)if(share.matched && share.goods>0) {
            BigInteger[] divided=BigInteger.valueOf(share.goods).multiply(BigInteger.valueOf(reduction)).divideAndRemainder(denominator);
            share.discount=divided[0].longValueExact();share.remainder=divided[1];allocated=Math.addExact(allocated,share.discount);matched.add(share);
        }
        int left=Math.toIntExact(Math.subtractExact(reduction,allocated));
        if(left<0 || left>=matched.size())throw new IllegalStateException("优惠分摊余数不正确");
        matched.sort(Comparator.comparing((Share x)->x.remainder).reversed().thenComparing(x->x.input.getLineId()).thenComparing(x->x.input.getSkuId()));
        for(int i=0;i<left;i++)matched.get(i).discount=Math.addExact(matched.get(i).discount,1L);
        List<CouponQuoteDTO.Line> output=new ArrayList<>();long check=0;
        for(Share share:shares) {
            if(share.discount<0 || share.discount>share.goods)throw new IllegalStateException("行优惠超出商品金额");
            CouponOrderLineDTO input=share.input;CouponQuoteDTO.Line line=new CouponQuoteDTO.Line();
            line.setLineId(input.getLineId());line.setSpuId(input.getSpuId());line.setSkuId(input.getSkuId());line.setCategoryId(input.getCategoryId());
            line.setQuantity(input.getQuantity());line.setUnitPrice(input.getUnitPrice());line.setGoodsPrice(share.goods);line.setDiscountPrice(share.discount);
            line.setPaidPrice(Math.subtractExact(share.goods,share.discount));line.setUnitDiscountBase(share.discount/input.getQuantity());
            line.setUnitDiscountRemainder(Math.toIntExact(share.discount%input.getQuantity()));output.add(line);check=Math.addExact(check,share.discount);
        }
        if(check!=reduction)throw new IllegalStateException("优惠分摊总额不一致");
        CouponQuoteDTO result=new CouponQuoteDTO();result.setCouponId(coupon.getId());result.setCouponVersion(coupon.getAuthVersion());
        result.setEligiblePrice(eligible);result.setDiscountPrice(reduction);result.setLines(output);return result;
    }
    /** 快照字段必须完整，不能把未知范围当全店或未知折扣当零优惠。 */
    public static void validateSnapshot(CouponRespDTO row) {
        if(row==null || !positive(row.getId()) || !positive(row.getTenantId()) || !positive(row.getUserId())
                || row.getAuthVersion()==null || row.getAuthVersion()<0 || row.getUsePrice()==null || row.getUsePrice()<0 || row.getUsePrice()>2_000_000_000
                || row.getProductScope()==null || !Arrays.asList(1,2,3).contains(row.getProductScope()) || row.getProductScopeValues()==null
                || row.getProductScopeValues().size()>200 || row.getProductScopeValues().stream().anyMatch(x->!positive(x))
                || new HashSet<>(row.getProductScopeValues()).size()!=row.getProductScopeValues().size()
                || (row.getProductScope()==1?!row.getProductScopeValues().isEmpty():row.getProductScopeValues().isEmpty())
                || row.getValidStartTime()==null || row.getValidEndTime()==null || !row.getValidStartTime().isBefore(row.getValidEndTime())
                || row.getStatus()==null || !Arrays.asList(1,2,3,4,5).contains(row.getStatus()))throw exception0(503,"优惠券快照不完整");
        if(Integer.valueOf(1).equals(row.getDiscountType())) {
            if(!amount(row.getDiscountPrice()) || row.getDiscountPercent()!=null || row.getDiscountLimitPrice()!=null)throw exception0(503,"优惠券优惠规则异常");
        } else if(Integer.valueOf(2).equals(row.getDiscountType())) {
            if(row.getDiscountPrice()!=null || row.getDiscountPercent()==null || row.getDiscountPercent()<1 || row.getDiscountPercent()>99
                    || !amount(row.getDiscountLimitPrice()))throw exception0(503,"优惠券优惠规则异常");
        } else throw exception0(503,"优惠券优惠类型异常");
    }
    public static List<CouponOrderLineDTO> orderedLines(List<CouponOrderLineDTO> source) {
        if(source==null || source.isEmpty() || source.size()>200)throw exception0(400,"请选择受支持数量的商品");
        Set<Long> ids=new HashSet<>(),skus=new HashSet<>();List<CouponOrderLineDTO> lines=new ArrayList<>();
        for(CouponOrderLineDTO row:source) {
            if(row==null || !positive(row.getLineId()) || !ids.add(row.getLineId()) || !positive(row.getSpuId()) || !positive(row.getSkuId()) || !skus.add(row.getSkuId())
                    || !positive(row.getCategoryId()) || row.getQuantity()==null || row.getQuantity()<1 || row.getQuantity()>9999
                    || row.getUnitPrice()==null || row.getUnitPrice()<0 || row.getUnitPrice()>2_000_000_000L)throw exception0(400,"权威商品行不完整或存在重复SKU");
            CouponOrderLineDTO copy=new CouponOrderLineDTO();copy.setLineId(row.getLineId());copy.setSpuId(row.getSpuId());copy.setSkuId(row.getSkuId());
            copy.setCategoryId(row.getCategoryId());copy.setQuantity(row.getQuantity());copy.setUnitPrice(row.getUnitPrice());lines.add(copy);
        }
        lines.sort(Comparator.comparing(CouponOrderLineDTO::getLineId).thenComparing(CouponOrderLineDTO::getSkuId));return lines;
    }
    private static boolean positive(Long x){return x!=null && x>0;}
    private static boolean amount(Integer x){return x!=null && x>=1 && x<=2_000_000_000;}
    private static class Share { private CouponOrderLineDTO input;private boolean matched;private long goods,discount;private BigInteger remainder=BigInteger.ZERO; }
}
