package cn.qinghe.mall.module.product.dal.mysql.search;

import cn.qinghe.mall.module.product.dal.mysql.spu.AppProductSpuMapper;
import cn.qinghe.mall.module.product.service.search.EsProduct;
import lombok.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** 搜索快照固定SQL；包含逻辑删除编号，才能为下架/删除生成持久不可见记录。 */
@Mapper
public interface ProductSearchSnapshotMapper {
    @Select("SELECT p.id FROM product_spu p WHERE p.tenant_id=#{tenant} AND p.id>#{after} ORDER BY p.id LIMIT 20")
    List<Long> ids(@Param("tenant") Long tenant,@Param("after") Long after);

    // 与原前台目录复用完全相同的销售可见性谓词，不在这里扩展经营规则。
    @Select(AppProductSpuMapper.FIELDS+",p.keyword AS keywords,p.sort,p.create_time,c.parent_id AS category_parent_id,"
            +"c2.id AS parent_id,c2.parent_id AS parent_parent_id,c2.name AS parent_name,"
            +"c3.id AS root_id,c3.parent_id AS root_parent_id,c3.name AS root_name"
            +AppProductSpuMapper.FROM+" AND p.id=#{id}")
    Source source(@Param("tenant") Long tenant,@Param("id") Long id);

    @Insert("INSERT INTO product_search_snapshot(tenant_id,version,product_id,payload,payload_hash) "
            +"VALUES(#{tenant},#{version},#{id},#{payload},#{hash}) ON DUPLICATE KEY UPDATE product_id=product_id")
    int insertImmutable(@Param("tenant") Long tenant,@Param("version") Long version,@Param("id") Long id,
                        @Param("payload") String payload,@Param("hash") String hash);
    @Select("SELECT tenant_id,version,product_id,payload,payload_hash FROM product_search_snapshot "
            +"WHERE tenant_id=#{tenant} AND version=#{version} AND product_id=#{id} FOR UPDATE")
    Row lock(@Param("tenant") Long tenant,@Param("version") Long version,@Param("id") Long id);
    @Select("SELECT tenant_id,version,product_id,payload,payload_hash FROM product_search_snapshot "
            +"WHERE tenant_id=#{tenant} AND version=#{version} AND product_id>#{after} ORDER BY product_id LIMIT 20")
    List<Row> page(@Param("tenant") Long tenant,@Param("version") Long version,@Param("after") Long after);

    @Data @EqualsAndHashCode(callSuper=true)
    class Source extends EsProduct {
        private LocalDateTime createTime;
        private Long categoryParentId,parentId,parentParentId,rootId,rootParentId;
        private String parentName,rootName;
    }
    @Data @ToString(onlyExplicitlyIncluded=true)
    class Row {
        private Long tenantId,version,productId;
        private String payload,payloadHash;
    }
}
