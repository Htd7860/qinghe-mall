package cn.qinghe.mall.module.infra.dal.dataobject.file;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件表，取自 yudao FileDO；本项目增加租户、上传者与用途。
 * 不沿用原版全局 @TenantIgnore，普通文件操作必须处于可信租户内。
 *
 * @author 芋道源码
 */
@TableName("infra_file")
@Data
@EqualsAndHashCode(callSuper = true)
public class FileDO extends TenantBaseDo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long configId;
    private String name;
    private String path;
    private String url;
    private String type;
    private Long size;
    private Long ownerUserId;
    private Integer ownerUserType;
    private String purpose;
}
