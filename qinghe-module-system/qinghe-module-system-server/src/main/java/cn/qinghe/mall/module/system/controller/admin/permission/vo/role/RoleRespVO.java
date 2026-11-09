package cn.qinghe.mall.module.system.controller.admin.permission.vo.role;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/** 管理读取投影，不直接返回DO、审计操作者或逻辑删除字段。 */
@Data
public class RoleRespVO {

    private Long id;
    private String name;
    private String code;
    private Integer sort;
    private Integer status;
    private Integer type;
    private String remark;
    /** 当前仅返回配置，不表示部门/本人SQL数据范围已经执行。 */
    private Integer dataScope;
    private Set<Long> dataScopeDeptIds;
    private Long authVersion;
    private Boolean deleted;
    private LocalDateTime createTime;
}
