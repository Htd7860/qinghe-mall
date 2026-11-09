package cn.qinghe.mall.module.system.controller.admin.permission.vo.menu;

import lombok.Data;

/** 扁平树选项，parentId=0为根；不包含组件路径或内部审计字段。 */
@Data
public class MenuSimpleRespVO {

    private Long id;
    private String name;
    private Long parentId;
    private Integer type;
    private Integer sort;
    private String permission;
    private Integer status;
}
