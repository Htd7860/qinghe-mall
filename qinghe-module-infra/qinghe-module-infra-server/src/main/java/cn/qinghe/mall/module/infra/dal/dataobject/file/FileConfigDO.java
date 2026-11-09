package cn.qinghe.mall.module.infra.dal.dataobject.file;

import cn.qinghe.mall.framework.mybatis.core.dataobject.BaseDo;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import cn.qinghe.mall.module.infra.framework.file.core.client.local.LocalFileClientConfig;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 取自 yudao FileConfigDO：保留全局存储配置、master 与 JSON 配置。
 * 当前仅支持 LOCAL，使用确定的配置类型，不导入无关存储或旧包名兼容分支。
 *
 * @author 芋道源码
 */
@TableName(value = "infra_file_config", autoResultMap = true)
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
public class FileConfigDO extends BaseDo {
    @TableId(type = IdType.AUTO)
    @ToString.Include
    private Long id;
    private String name;
    private Integer storage;
    private String remark;
    private Boolean master;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private LocalFileClientConfig config;
}
