package cn.qinghe.mall.module.system.controller.admin.permission.vo.menu;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import lombok.Data;
@Data
public class MenuNavigationRespVO {
    private Long id;private String name;private Long parentId;private Integer type;private Integer sort;
    private String path;private String icon;private String permission;
    public static MenuNavigationRespVO from(MenuDO row) {
        MenuNavigationRespVO out=new MenuNavigationRespVO();out.setId(row.getId());out.setName(row.getName());out.setParentId(row.getParentId());
        out.setType(row.getType());out.setSort(row.getSort());out.setPath(row.getPath());out.setIcon(row.getIcon());out.setPermission(row.getPermission());return out;
    }
}
