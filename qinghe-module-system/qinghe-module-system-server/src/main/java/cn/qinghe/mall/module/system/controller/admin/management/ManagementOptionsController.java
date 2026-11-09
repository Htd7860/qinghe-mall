package cn.qinghe.mall.module.system.controller.admin.management;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.permission.RoleDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.RoleMapper;
import cn.qinghe.mall.module.system.dal.mysql.dept.DeptMapper;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantPackageMapper;
import cn.qinghe.mall.module.system.service.tenant.TenantService;
import cn.qinghe.mall.module.system.service.management.ManagementTransactions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;
/** 编辑所需精简选项按操作权限授权，不要求额外开放完整管理查询权限。 */
@RestController @RequiredArgsConstructor
public class ManagementOptionsController {
    private final RoleMapper roleMapper;private final DeptMapper deptMapper;
    private final TenantPackageMapper packageMapper;private final TenantService tenantService;
    @GetMapping({"/admin-api/system/role/simple-list","/platform-api/system/role/simple-list"})
    @PreAuthorize("@ss.hasPermission('system:permission:assign-user-role')")
    public CommonResult<List<Map<String,Object>>> roles(HttpServletRequest request,HttpServletResponse response) {
        Long tenantId=prepare(request,response);
        return CommonResult.success(roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getTenantId,tenantId).eq(RoleDO::getType,2).orderByAsc(RoleDO::getSort,RoleDO::getId))
                .stream().map(row->option(row.getId(),row.getName(),row.getStatus(),row.getAuthVersion(),0L)).collect(Collectors.toList()));
    }
    @GetMapping({"/admin-api/system/dept/simple-list","/platform-api/system/dept/simple-list"})
    @PreAuthorize("@ss.hasAnyPermissions('system:user:create','system:user:update','system:permission:assign-role-data-scope','system:dept:query')")
    public CommonResult<List<Map<String,Object>>> depts(HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);
        return CommonResult.success(deptMapper.selectTenantTree().stream()
                .map(row->option(row.getId(),row.getName(),row.getStatus(),row.getAuthVersion(),row.getParentId())).collect(Collectors.toList()));
    }
    @GetMapping("/platform-api/system/tenant-package/simple-list")
    @PreAuthorize("@ss.hasAnyPermissions('system:tenant:create','system:tenant:update')")
    public CommonResult<List<Map<String,Object>>> packages(HttpServletRequest request,HttpServletResponse response) {
        prepare(request,response);ManagementTransactions.requirePlatformRead();
        return CommonResult.success(packageMapper.selectList(new LambdaQueryWrapper<TenantPackageDO>().orderByAsc(TenantPackageDO::getId))
                .stream().map(row->option(row.getId(),row.getName(),row.getStatus(),row.getAuthVersion(),0L)).collect(Collectors.toList()));
    }
    private Long prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        Long tenantId=TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()
                || !tenantId.equals(SecurityFrameworkUtils.getLoginUser().getTenantId())) {
            throw new org.springframework.security.access.AccessDeniedException("管理选项需要可信租户身份");
        }
        if(tenantId>0) { tenantService.validateForOperation(tenantId); }return tenantId;
    }
    private Map<String,Object> option(Long id,String name,Integer status,Long version,Long parentId) {
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("name",name);row.put("status",status);
        row.put("authVersion",version);row.put("deleted",false);row.put("parentId",parentId);return row;
    }
}
