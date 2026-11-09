package cn.qinghe.mall.module.trade.service.seckill;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.trade.dal.dataobject.seckill.TradeSeckillQuoteDO;
import cn.qinghe.mall.module.trade.service.seckill.dto.TradeSeckillQuoteSnapshot;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 同一套原密文/摘要/归属校验供绑定与后台建单复用；不接HTTP、不解锁租户。 */
@Component
public class TradeSeckillSnapshotReader {
    private final TradeSeckillQuoteCipher cipher;
    public TradeSeckillSnapshotReader(TradeSeckillQuoteCipher cipher) { this.cipher=cipher; }
    public TradeSeckillQuoteSnapshot openVerified(TradeSeckillQuoteDO quote) {
        String json=cipher.open(quote);
        if (!Objects.equals(DigestUtil.sha256Hex(json),quote.getQuoteDigest())) throw exception0(503,"原秒杀报价快照不完整");
        TradeSeckillQuoteSnapshot snapshot=JsonUtils.parseObject(json,TradeSeckillQuoteSnapshot.class);
        if (snapshot==null || !Integer.valueOf(1).equals(snapshot.getSchemaVersion()) || snapshot.getActivity()==null
                || snapshot.getProduct()==null || snapshot.getAddress()==null || snapshot.getDelivery()==null
                || !Objects.equals(snapshot.getActivity().getTenantId(),quote.getTenantId())
                || !Objects.equals(snapshot.getActivity().getMemberId(),quote.getMemberId())
                || !Objects.equals(snapshot.getActivity().getActivityId(),quote.getActivityId())
                || !Objects.equals(snapshot.getActivity().getGeneration(),quote.getGeneration())
                || !Objects.equals(snapshot.getActivity().getActivityVersion(),quote.getActivityVersion())
                || !Objects.equals(snapshot.getActivity().getRedisEpoch(),quote.getRedisEpoch())
                || !Objects.equals(snapshot.getActivity().getSkuId(),quote.getSkuId())
                || !Objects.equals(snapshot.getActivity().getPriceCent(),quote.getPriceCent())
                || !Objects.equals(snapshot.getProduct().getTenantId(),quote.getTenantId())
                || !Objects.equals(snapshot.getProduct().getSkuId(),quote.getSkuId())
                || !Objects.equals(snapshot.getProduct().getSpuId(),snapshot.getActivity().getSpuId())
                || !Objects.equals(snapshot.getProduct().getSpuVersion(),snapshot.getActivity().getSpuVersion())
                || !Objects.equals(snapshot.getProduct().getSkuVersion(),snapshot.getActivity().getSkuVersion())
                || !id(snapshot.getAddress().getId()) || !Objects.equals(snapshot.getAddress().getTenantId(),quote.getTenantId())
                || !Objects.equals(snapshot.getAddress().getUserId(),quote.getMemberId())
                || !Objects.equals(snapshot.getQuantity(),quote.getQuantity()) || !Objects.equals(snapshot.getGoodsAmount(),quote.getGoodsAmount())
                || !Objects.equals(snapshot.getShippingAmount(),quote.getShippingAmount()) || !Objects.equals(snapshot.getPayAmount(),quote.getPayAmount())
                || !Objects.equals(snapshot.getDelivery().getDeliveryPrice(),quote.getShippingAmount())) throw exception0(503,"原秒杀报价快照不完整");
        return snapshot;
    }
    private boolean id(Long x) { return x!=null && x>0 && x<=9007199254740991L; }
}
