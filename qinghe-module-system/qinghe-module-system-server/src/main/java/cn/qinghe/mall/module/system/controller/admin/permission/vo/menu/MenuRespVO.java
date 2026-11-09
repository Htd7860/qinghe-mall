package cn.qinghe.mall.module.system.controller.admin.permission.vo.menu;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import lombok.Data;
@Data
public class MenuRespVO {
    private Long id;private String realm;private String name;private String permission;
    private Integer type;private Integer sort;private Long parentId;private String path;private String icon;
    private String component;private String componentName;private Integer status;
    private Boolean visible;private Boolean keepAlive;private Boolean alwaysShow;private Long authVersion;private Boolean deleted;
    public static MenuRespVO from(MenuDO row) {
        MenuRespVO out=new MenuRespVO();out.setId(row.getId());out.setRealm(row.getRealm());out.setName(row.getName());
        out.setPermission(row.getPermission());out.setType(row.getType());out.setSort(row.getSort());out.setParentId(row.getParentId());
        out.setPath(row.getPath());out.setIcon(row.getIcon());out.setComponent(row.getComponent());out.setComponentName(row.getComponentName());
        out.setStatus(row.getStatus());out.setVisible(row.getVisible());out.setKeepAlive(row.getKeepAlive());out.setAlwaysShow(row.getAlwaysShow());
        out.setAuthVersion(row.getAuthVersion());out.setDeleted(row.getDeleted());return out;
    }
}
