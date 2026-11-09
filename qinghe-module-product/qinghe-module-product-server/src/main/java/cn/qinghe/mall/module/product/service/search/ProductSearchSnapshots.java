package cn.qinghe.mall.module.product.service.search;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.dal.mysql.search.ProductSearchControlMapper;
import cn.qinghe.mall.module.product.dal.mysql.search.ProductSearchSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.ZoneId;
import java.util.*;

/** 有界捕获原目录版本；只落本地不可变快照，不发送ES、不推进synced/initialized。 */
@Service @RequiredArgsConstructor
public class ProductSearchSnapshots {
    private final ProductSearchChanges changes;
    private final ProductSearchSnapshotMapper mapper;
    private final ObjectMapper json;

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=10,rollbackFor=Exception.class)
    public Page capture(Long version,Long afterId) {
        Long tenant=identity(version,afterId);
        // 必须是本事务第一次一致性读；后续商品、SKU存在性、品牌/类目共享同一个视图。
        ProductSearchControlMapper.State state=changes.state(tenant);
        if(state==null || !version.equals(state.getChangeVersion()))throw new IllegalStateException("商品目录已变化，需要从最新版本重新捕获");
        List<Long> ids=mapper.ids(tenant,afterId);
        if(ids==null || ids.size()>20)throw incomplete();
        List<ProductSearchDocument> documents=new ArrayList<>();long last=afterId;
        for(Long id:ids) {
            if(id==null || id<=last || id>9007199254740991L)throw incomplete();
            ProductSearchSnapshotMapper.Source source=mapper.source(tenant,id);
            EsProduct product;
            if(source==null) {
                product=new EsProduct();product.setTenantId(tenant);product.setId(id);product.setVisible(false);
            } else {
                if(!tenant.equals(source.getTenantId()) || !id.equals(source.getId()) || source.getCreateTime()==null)throw incomplete();
                path(source);source.setCreatedAt(source.getCreateTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
                source.setVisible(true);product=source;
            }
            product.setVersion(version);
            ProductSearchDocument document=ProductSearchDocument.of(json,product);
            mapper.insertImmutable(tenant,version,id,document.getPayload(),document.getHash());
            // 当前读可看见并发捕获已插入的同键；只接受完全相同正文，不覆盖旧快照。
            ProductSearchDocument stored=restore(mapper.lock(tenant,version,id),tenant,version,id);
            if(!document.getPayload().equals(stored.getPayload()))throw new IllegalStateException("同版本商品搜索快照发生冲突");
            documents.add(stored);last=id;
        }
        return new Page(version,afterId,last,ids.size()<20,Collections.unmodifiableList(documents));
    }

    /** 原版本发送重试从已保存正文恢复，不能重新查询当前商品拼装旧版本。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5,readOnly=true)
    public Page saved(Long version,Long afterId) {
        Long tenant=identity(version,afterId);List<ProductSearchSnapshotMapper.Row> rows=mapper.page(tenant,version,afterId);
        if(rows==null || rows.size()>20)throw incomplete();
        List<ProductSearchDocument> documents=new ArrayList<>();long last=afterId;
        for(ProductSearchSnapshotMapper.Row row:rows) {
            if(row==null || row.getProductId()==null || row.getProductId()<=last)throw incomplete();
            ProductSearchDocument document=restore(row,tenant,version,row.getProductId());documents.add(document);last=document.getId();
        }
        return new Page(version,afterId,last,rows.size()<20,Collections.unmodifiableList(documents));
    }

    private ProductSearchDocument restore(ProductSearchSnapshotMapper.Row row,Long tenant,Long version,Long id) {
        if(row==null || !tenant.equals(row.getTenantId()) || !version.equals(row.getVersion()) || !id.equals(row.getProductId()))throw incomplete();
        ProductSearchDocument document=ProductSearchDocument.restore(json,row.getPayload(),row.getPayloadHash());
        if(!tenant.equals(document.getTenantId()) || !version.equals(document.getVersion()) || !id.equals(document.getId()))throw incomplete();
        return document;
    }
    private void path(ProductSearchSnapshotMapper.Source p) {
        List<Long> ids=new ArrayList<>();List<String> names=new ArrayList<>();
        if(p.getRootId()!=null) {
            if(!Long.valueOf(0).equals(p.getRootParentId()) || !Objects.equals(p.getParentParentId(),p.getRootId())
                    || !text(p.getRootName()))throw incomplete();
            ids.add(p.getRootId());names.add(p.getRootName());
        }
        if(p.getParentId()!=null) {
            if(!Objects.equals(p.getCategoryParentId(),p.getParentId()) || !text(p.getParentName())
                    || p.getRootId()==null && !Long.valueOf(0).equals(p.getParentParentId()))throw incomplete();
            ids.add(p.getParentId());names.add(p.getParentName());
        } else if(p.getRootId()!=null || !Long.valueOf(0).equals(p.getCategoryParentId()))throw incomplete();
        if(!text(p.getCategoryName()))throw incomplete();
        ids.add(p.getCategoryId());names.add(p.getCategoryName());p.setCategoryIds(ids);p.setCategoryName(String.join("/",names));
    }
    private Long identity(Long version,Long after) {
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || version==null || version<=0 || version>9007199254740991L || after==null || after<0 || after>9007199254740991L)
            throw new IllegalArgumentException("搜索快照需要可信租户、正版本和有界游标");
        return tenant;
    }
    private boolean text(String s){return s!=null && !s.trim().isEmpty() && s.length()<=100;}
    private IllegalStateException incomplete(){return new IllegalStateException("商品搜索快照来源或记录不完整");}
    @Getter @RequiredArgsConstructor
    public static final class Page {
        private final Long version,afterId,nextId;
        /** 本次SQL页尾，不是整轮捕获/发送完成证明；调用方仍需持久游标和版本复核。 */
        private final boolean exhausted;
        private final List<ProductSearchDocument> documents;
    }
}
