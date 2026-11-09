package cn.qinghe.mall.module.product.service.search;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 固定销售字段编码与不可变正文；保存和重试发送共用，不序列化DO/子类的额外字段。 */
public final class ProductSearchDocument {
    private final Long tenantId, id, version;
    private final String payload, hash;
    private ProductSearchDocument(Long tenantId,Long id,Long version,String payload,String hash) {
        this.tenantId=tenantId;this.id=id;this.version=version;this.payload=payload;this.hash=hash;
    }
    public Long getTenantId(){return tenantId;}
    public Long getId(){return id;}
    public Long getVersion(){return version;}
    public String getPayload(){return payload;}
    public String getHash(){return hash;}

    public static ProductSearchDocument of(ObjectMapper json,EsProduct p) {
        validate(p);
        ObjectNode body=json.createObjectNode();
        body.put("tenantId",p.getTenantId());body.put("id",p.getId());body.put("version",p.getVersion());body.put("visible",p.getVisible());
        if(p.getVisible()) {
            body.put("name",p.getName());body.put("introduction",p.getIntroduction());body.put("keywords",p.getKeywords());
            body.put("brandId",p.getBrandId());body.put("brandName",p.getBrandName());body.put("categoryId",p.getCategoryId());
            body.set("categoryIds",json.valueToTree(p.getCategoryIds()));body.put("categoryName",p.getCategoryName());
            body.put("picUrl",p.getPicUrl());body.put("price",p.getPrice());body.put("marketPrice",p.getMarketPrice());
            body.put("stock",p.getStock());body.put("salesCount",p.getSalesCount());body.put("sort",p.getSort());body.put("createdAt",p.getCreatedAt());
        }
        // ObjectNode固定插入顺序；不使用可能随全局配置变化的Bean属性顺序。
        String hash=DigestUtil.sha256Hex(body.toString());body.put("payloadHash",hash);
        String payload=body.toString();
        if(payload.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("商品搜索快照过大");
        return new ProductSearchDocument(p.getTenantId(),p.getId(),p.getVersion(),payload,hash);
    }

    /** 恢复必须核对原正文，不把JSON重新编码后的不同内容当作原版本。 */
    public static ProductSearchDocument restore(ObjectMapper json,String payload,String hash) {
        if(payload==null || payload.getBytes(StandardCharsets.UTF_8).length>65536 || hash==null || !hash.matches("[0-9a-f]{64}"))
            throw new IllegalStateException("商品搜索快照不完整");
        try {
            JsonNode parsed=json.readTree(payload);
            if(parsed==null || !parsed.isObject() || !hash.equals(parsed.path("payloadHash").asText()))throw new IOException();
            ObjectNode body=(ObjectNode)parsed;body.remove("payloadHash");
            ProductSearchDocument canonical=of(json,json.treeToValue(body,EsProduct.class));
            if(!hash.equals(canonical.hash) || !payload.equals(canonical.payload))throw new IOException();
            return canonical;
        } catch(IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("商品搜索快照内容或摘要不一致");
        }
    }

    private static void validate(EsProduct p) {
        if(p==null || !id(p.getTenantId()) || !id(p.getId()) || !id(p.getVersion()) || p.getVisible()==null)throw new IllegalArgumentException("索引身份或版本不正确");
        if(!p.getVisible())return;
        if(!text(p.getName(),128) || p.getIntroduction()==null || p.getIntroduction().length()>512 || p.getKeywords()==null || p.getKeywords().length()>255
                || !id(p.getBrandId()) || !text(p.getBrandName(),100) || !id(p.getCategoryId()) || !text(p.getCategoryName(),302)
                || p.getCategoryIds()==null || p.getCategoryIds().isEmpty() || p.getCategoryIds().size()>3 || !p.getCategoryIds().contains(p.getCategoryId())
                || p.getCategoryIds().stream().anyMatch(x->!id(x)) || new HashSet<>(p.getCategoryIds()).size()!=p.getCategoryIds().size()
                || !text(p.getPicUrl(),1024) || p.getPrice()==null || p.getPrice()<1 || p.getPrice()>2000000000 || p.getMarketPrice()==null || p.getMarketPrice()<0 || p.getMarketPrice()>2000000000
                || p.getStock()==null || p.getStock()<0 || p.getSalesCount()==null || p.getSalesCount()<0 || p.getSort()==null || p.getSort()<0 || p.getSort()>10000
                || p.getCreatedAt()==null || p.getCreatedAt()<=0 || p.getCreatedAt()>8640000000000000L)throw new IllegalArgumentException("商品索引字段不完整");
    }
    private static boolean id(Long n){return n!=null && n>0 && n<=9007199254740991L;}
    private static boolean text(String s,int max){return s!=null && !s.trim().isEmpty() && s.length()<=max;}
}
