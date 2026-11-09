package cn.qinghe.mall.module.product.dal.mysql.search;
import lombok.*;
import org.apache.ibatis.annotations.*;

/** 固定商品搜索轮次进度；所有写入先持有同租户control锁，不在锁内调用ES。 */
@Mapper
public interface ProductSearchRoundMapper {
    @Select("SELECT r.tenant_id,r.round_no,r.version,r.alias_name,r.physical_name,r.index_uuid,r.phase,r.capture_after,r.send_after,r.captured_count,r.sent_count "
            +"FROM product_search_round r JOIN product_search_control c ON c.tenant_id=r.tenant_id "
            +"WHERE r.tenant_id=#{tenant} AND c.initialized=b'1' AND c.change_version=c.synced_version AND c.change_version=r.version "
            +"AND r.version BETWEEN 1 AND 9007199254740991 AND r.phase='DONE' AND r.capture_after=r.send_after AND r.captured_count=r.sent_count "
            +"AND r.alias_name=#{alias} AND r.physical_name=#{physical} AND r.index_uuid=#{uuid}")
    Row ready(@Param("tenant") Long tenant,@Param("alias") String alias,@Param("physical") String physical,@Param("uuid") String uuid);
    @Select("SELECT tenant_id,round_no,version,alias_name,physical_name,index_uuid,phase,capture_after,send_after,captured_count,sent_count "
            +"FROM product_search_round WHERE tenant_id=#{tenant} FOR UPDATE")
    Row lock(@Param("tenant") Long tenant);
    @Insert("INSERT INTO product_search_round(tenant_id,round_no,version,alias_name,physical_name,index_uuid,phase,capture_after,send_after,captured_count,sent_count) "
            +"VALUES(#{r.tenantId},#{r.roundNo},#{r.version},#{r.aliasName},#{r.physicalName},#{r.indexUuid},#{r.phase},#{r.captureAfter},#{r.sendAfter},#{r.capturedCount},#{r.sentCount})")
    int insert(@Param("r") Row row);
    @Update("UPDATE product_search_round SET round_no=#{next.roundNo},version=#{next.version},alias_name=#{next.aliasName},physical_name=#{next.physicalName},"
            +"index_uuid=#{next.indexUuid},phase=#{next.phase},capture_after=#{next.captureAfter},send_after=#{next.sendAfter},"
            +"captured_count=#{next.capturedCount},sent_count=#{next.sentCount},update_time=UTC_TIMESTAMP(6) "
            +"WHERE tenant_id=#{old.tenantId} AND round_no=#{old.roundNo} AND version=#{old.version} AND phase=#{old.phase} "
            +"AND capture_after=#{old.captureAfter} AND send_after=#{old.sendAfter} AND captured_count=#{old.capturedCount} AND sent_count=#{old.sentCount}")
    int save(@Param("old") Row old,@Param("next") Row next);
    @Update("UPDATE product_search_control SET synced_version=#{version},initialized=b'1',update_time=UTC_TIMESTAMP(6) "
            +"WHERE tenant_id=#{tenant} AND change_version=#{version} AND synced_version<=#{version}")
    int confirm(@Param("tenant") Long tenant,@Param("version") Long version);
    @Update("UPDATE product_search_control SET change_version=change_version+1,initialized=b'0',update_time=UTC_TIMESTAMP(6) "
            +"WHERE tenant_id=#{tenant} AND change_version=#{expected} AND change_version<9007199254740991")
    int beginRebuild(@Param("tenant") Long tenant,@Param("expected") Long expected);

    @Data @ToString(onlyExplicitlyIncluded=true)
    class Row {
        private Long tenantId,version,captureAfter,sendAfter,capturedCount,sentCount;
        private String roundNo,aliasName,physicalName,indexUuid,phase;
    }
}
