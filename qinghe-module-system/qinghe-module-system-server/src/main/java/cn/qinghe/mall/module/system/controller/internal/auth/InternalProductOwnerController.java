package cn.qinghe.mall.module.system.controller.internal.auth;

import cn.qinghe.mall.framework.common.biz.system.permission.dto.ProductOwnerRespDTO;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import cn.qinghe.mall.module.system.dal.dataobject.dept.DeptDO;
import cn.qinghe.mall.module.system.dal.mysql.user.AdminUserMapper;
import cn.qinghe.mall.module.system.dal.mysql.dept.DeptMapper;
import cn.qinghe.mall.module.system.service.user.UserDataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Min;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取yudao AdminUserApiImpl.getUser的真实人员投影流程；新增当前租户/范围和专用转移权限。
 * 不向Gateway开放，也不把调用者传入的部门当真实归属。
 */
@RestController
@Validated
@RequiredArgsConstructor
public class InternalProductOwnerController {
    private final AdminUserMapper users;
    private final DeptMapper depts;
    private final UserDataScope scopes;

    @GetMapping("/internal-api/auth/product-owner")
    @PreAuthorize("@ss.hasPermission('product:spu:transfer')")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
    public CommonResult<ProductOwnerRespDTO> get(@RequestParam("id") @Min(1) Long id, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (actor==null || !UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getTenantId()==null
                || actor.getTenantId()<=0 || TenantContextHolder.isIgnore()
                || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())) {
            throw exception0(403,"商品归属只能选择当前商家员工");
        }
        AdminUserDO user=users.selectByIdAndTenant(id,actor.getTenantId());
        if (user==null || !Integer.valueOf(0).equals(user.getStatus())) { throw exception0(404,"目标员工不存在、已停用或不在可管理范围内"); }
        scopes.current().requireVisible(user);
        DeptDO department=null;
        if (user.getDeptId()!=null) {
            Map<Long,DeptDO> tree=depts.selectTenantTree().stream().collect(Collectors.toMap(DeptDO::getId,d -> d));
            department=tree.get(user.getDeptId());Long cursor=user.getDeptId();Set<Long> seen=new HashSet<>();
            while (cursor!=null && cursor>0) {
                DeptDO node=tree.get(cursor);
                if (node==null || !Integer.valueOf(0).equals(node.getStatus()) || !seen.add(cursor)) {
                    throw exception0(409,"目标员工所属部门不可用");
                }
                cursor=node.getParentId();
            }
        }
        ProductOwnerRespDTO result=new ProductOwnerRespDTO();
        result.setTenantId(actor.getTenantId());result.setOwnerUserId(user.getId());result.setOwnerName(user.getNickname());
        result.setOwnerDeptId(user.getDeptId());result.setUserVersion(user.getAuthVersion());
        if (department!=null) { result.setOwnerDeptName(department.getName());result.setDeptVersion(department.getAuthVersion()); }
        return CommonResult.success(result);
    }
}
