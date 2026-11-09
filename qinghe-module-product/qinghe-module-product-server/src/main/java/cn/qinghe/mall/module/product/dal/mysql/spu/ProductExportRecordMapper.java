package cn.qinghe.mall.module.product.dal.mysql.spu;
import org.apache.ibatis.annotations.*;
/** 记录已生成的导出；不把生成成功称为浏览器已下载。 */
@Mapper
public interface ProductExportRecordMapper {
    @Insert("INSERT INTO product_export_record(id,tenant_id,actor_user_id,filters,row_count,byte_count,sha256) "
            + "VALUES(#{id},#{tenantId},#{actorId},CAST(#{filters} AS JSON),#{rows},#{bytes},#{hash})")
    int insertGenerated(@Param("id") String id,@Param("tenantId") Long tenantId,@Param("actorId") Long actorId,
            @Param("filters") String filters,@Param("rows") int rows,@Param("bytes") int bytes,@Param("hash") String hash);
}
