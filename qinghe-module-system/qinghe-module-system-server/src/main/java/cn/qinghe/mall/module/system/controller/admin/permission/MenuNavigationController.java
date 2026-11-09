package cn.qinghe.mall.module.system.controller.admin.permission;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.menu.MenuNavigationRespVO;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import cn.qinghe.mall.module.system.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;
/** 无独立菜单维护权限要求，仅返回登录者已有授权与可见父链的导航白名单。 */
@RestController @RequiredArgsConstructor
public class MenuNavigationController {
    private final PermissionService permissionService;
    @GetMapping({"/admin-api/system/menu/navigation","/platform-api/system/menu/navigation"})
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<MenuNavigationRespVO>> navigation(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        List<MenuDO> effective=permissionService.getUserPermission(SecurityFrameworkUtils.getLoginUser().getId()).getMenus();
        Map<Long,MenuDO> map=effective.stream().collect(Collectors.toMap(MenuDO::getId,m->m));
        List<MenuNavigationRespVO> result=new ArrayList<>();
        for(MenuDO row:effective) {
            if(Integer.valueOf(3).equals(row.getType())) { continue; }
            Long cursor=row.getId();Set<Long> visited=new HashSet<>();boolean visible=true;
            while(cursor!=null && cursor>0 && visited.add(cursor)) {
                MenuDO current=map.get(cursor);
                if(current==null || !Boolean.TRUE.equals(current.getVisible())) { visible=false;break; }
                cursor=current.getParentId();
            }
            if(visible && Long.valueOf(0).equals(cursor)) { result.add(MenuNavigationRespVO.from(row)); }
        }
        return CommonResult.success(result);
    }
}
