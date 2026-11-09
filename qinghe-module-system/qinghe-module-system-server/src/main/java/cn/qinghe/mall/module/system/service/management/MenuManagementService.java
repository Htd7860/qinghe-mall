package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.module.system.controller.admin.management.vo.*;
import cn.qinghe.mall.module.system.controller.admin.permission.vo.menu.*;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.MenuMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
@Service
@RequiredArgsConstructor
public class MenuManagementService {
    private final ManagementTransactions transactions;
    private final MenuMapper menuMapper;
    private final ManagementAudit audit;
    public List<MenuRespVO> list(String realm,boolean deleted) {
        ManagementTransactions.requirePlatformRead();requireRealm(realm);
        return menuMapper.selectLifecycleList(realm,deleted).stream().map(MenuRespVO::from).collect(Collectors.toList());
    }
    public Long create(MenuSaveReqVO body) {
        return transactions.execute("system:menu:create",actor -> {
            actor.requirePlatform();
            if(body.getId()!=null || body.getExpectedVersion()!=null) { throw exception0(400,"创建资源不能指定编号或版本"); }
            validateTreeAndType(null,body.getRealm(),body.getType(),body.getParentId(),body.getPermission(),body.getStatus());
            MenuDO row=new MenuDO();row.setRealm(body.getRealm());row.setName(body.getName());row.setPermission(body.getPermission());
            row.setType(body.getType());row.setSort(body.getSort());row.setParentId(body.getParentId());row.setPath(body.getPath());row.setIcon(body.getIcon());
            row.setComponent(body.getComponent());row.setComponentName(body.getComponentName());row.setStatus(body.getStatus());
            row.setVisible(body.getVisible());row.setKeepAlive(body.getKeepAlive());row.setAlwaysShow(body.getAlwaysShow());row.setAuthVersion(0L);row.setDeleted(false);
            row.setCreator(actor.getId().toString());row.setUpdater(actor.getId().toString());row.setCreateTime(actor.getNow());row.setUpdateTime(actor.getNow());
            if(menuMapper.insert(row)!=1 || row.getId()==null || row.getId()<=0) { throw new IllegalStateException("资源创建结果异常"); }
            audit.record(actor,0L,"MENU.CREATE",row.getId(),-1L,0L,Collections.singletonMap("realm",row.getRealm()));return row.getId();
        });
    }
    public Long update(MenuSaveReqVO body) {
        return transactions.execute("system:menu:update",actor -> {
            actor.requirePlatform();MenuDO row=editable(body.getId(),body.getExpectedVersion(),false);
            if(!Objects.equals(row.getRealm(),body.getRealm())) { throw exception0(400,"资源所属端创建后不可更换"); }
            boolean identityChanged=!Objects.equals(row.getPermission(),body.getPermission()) || !Objects.equals(row.getType(),body.getType())
                    || !Objects.equals(row.getParentId(),body.getParentId());
            if(identityChanged && referenced(row.getId())) { throw exception0(409,"资源仍被角色或套餐引用，请先解除引用再调整权限标识、类型或父级"); }
            if(!Objects.equals(row.getType(),body.getType()) && menuMapper.selectListByRealm(row.getRealm()).stream()
                    .anyMatch(m->row.getId().equals(m.getParentId()))) { throw exception0(409,"资源仍有下级，不能直接改变类型"); }
            validateTreeAndType(row.getId(),body.getRealm(),body.getType(),body.getParentId(),body.getPermission(),body.getStatus());
            requireChanged(menuMapper.update(null,change(row,actor).set(MenuDO::getName,body.getName()).set(MenuDO::getPermission,body.getPermission())
                    .set(MenuDO::getType,body.getType()).set(MenuDO::getSort,body.getSort()).set(MenuDO::getParentId,body.getParentId())
                    .set(MenuDO::getPath,body.getPath()).set(MenuDO::getIcon,body.getIcon()).set(MenuDO::getComponent,body.getComponent())
                    .set(MenuDO::getComponentName,body.getComponentName()).set(MenuDO::getStatus,body.getStatus())
                    .set(MenuDO::getVisible,body.getVisible()).set(MenuDO::getKeepAlive,body.getKeepAlive()).set(MenuDO::getAlwaysShow,body.getAlwaysShow())));
            Map<String,Object> details=new LinkedHashMap<>();details.put("status",body.getStatus());details.put("visible",body.getVisible());details.put("identityChanged",identityChanged);
            return recorded(actor,row,"MENU.UPDATE",details);
        });
    }
    public Long status(VersionedStatusReqVO body) {
        return transactions.execute("system:menu:update",actor -> {
            actor.requirePlatform();MenuDO row=editable(body.getId(),body.getExpectedVersion(),false);
            if(Objects.equals(row.getStatus(),body.getStatus())) { return row.getAuthVersion(); }
            validateTreeAndType(row.getId(),row.getRealm(),row.getType(),row.getParentId(),row.getPermission(),body.getStatus());
            requireChanged(menuMapper.update(null,change(row,actor).set(MenuDO::getStatus,body.getStatus())));
            return recorded(actor,row,"MENU.STATUS",Collections.singletonMap("status",body.getStatus()));
        });
    }
    public Long delete(VersionedIdReqVO body) {
        return transactions.execute("system:menu:delete",actor -> {
            actor.requirePlatform();MenuDO row=editable(body.getId(),body.getExpectedVersion(),false);
            if(menuMapper.selectListByRealm(row.getRealm()).stream().anyMatch(m->row.getId().equals(m.getParentId())) || referenced(row.getId())) {
                throw exception0(409,"资源仍有下级或被角色、套餐引用，请先调整");
            }
            requireChanged(menuMapper.update(null,change(row,actor).set(MenuDO::getDeleted,true).set(MenuDO::getStatus,1)));
            return recorded(actor,row,"MENU.DELETE",Collections.singletonMap("referencesChecked",true));
        });
    }
    public Long restore(VersionedIdReqVO body) {
        return transactions.execute("system:menu:restore",actor -> {
            actor.requirePlatform();MenuDO row=editable(body.getId(),body.getExpectedVersion(),true);
            validateTreeAndType(row.getId(),row.getRealm(),row.getType(),row.getParentId(),row.getPermission(),1);
            requireChanged(menuMapper.restoreLifecycle(row.getId(),row.getAuthVersion(),actor.getId().toString(),actor.getNow()));
            return recorded(actor,row,"MENU.RESTORE",Collections.singletonMap("disabled",true));
        });
    }
    private void validateTreeAndType(Long id,String realm,Integer type,Long parentId,String permission,Integer status) {
        requireRealm(realm);
        if(Integer.valueOf(1).equals(type) && !permission.isEmpty()) { throw exception0(400,"目录不能设置接口权限标识"); }
        if(Integer.valueOf(3).equals(type) && permission.trim().isEmpty()) { throw exception0(400,"按钮或接口资源必须设置权限标识"); }
        Map<Long,MenuDO> map=menuMapper.selectListByRealm(realm).stream().collect(Collectors.toMap(MenuDO::getId,m->m));
        if(Integer.valueOf(3).equals(type)) {
            MenuDO parent=map.get(parentId);
            if(parent==null || !Integer.valueOf(2).equals(parent.getType())) { throw exception0(400,"按钮必须归属本端页面菜单"); }
        } else if(parentId>0 && (map.get(parentId)==null || !Integer.valueOf(1).equals(map.get(parentId).getType()))) {
            throw exception0(400,"目录或页面的父级必须是本端目录");
        }
        Set<Long> visited=new HashSet<>();Long cursor=parentId;
        while(cursor!=null && cursor>0 && visited.add(cursor)) {
            if(cursor.equals(id)) { throw exception0(400,"不能将资源移动到自身或其下级"); }
            MenuDO parent=map.get(cursor);
            if(parent==null || (Integer.valueOf(0).equals(status) && !Integer.valueOf(0).equals(parent.getStatus()))) {
                throw exception0(400,"父级资源不存在或未启用");
            }
            cursor=parent.getParentId();
        }
        if(!Long.valueOf(0).equals(cursor)) { throw exception0(400,"资源父链不完整或循环"); }
    }
    private boolean referenced(Long id) { return menuMapper.countAllActiveRoleReferences(id)>0 || menuMapper.countAllPackageReferences(id)>0; }
    private MenuDO editable(Long id,Long version,boolean deleted) {
        if(id==null || id<=0 || version==null || version<0 || version==Long.MAX_VALUE) { throw exception0(400,"资源编号或版本不合法"); }
        MenuDO row=menuMapper.selectLifecycleForUpdate(id);
        if(row==null || Boolean.TRUE.equals(row.getDeleted())!=deleted) { throw exception0(404,"资源不存在"); }
        if(!version.equals(row.getAuthVersion())) { throw exception0(409,"资源已变化，请重新读取"); }return row;
    }
    private void requireRealm(String realm) { if(!"merchant".equals(realm) && !"platform".equals(realm)) { throw exception0(400,"资源所属端不合法"); } }
    private LambdaUpdateWrapper<MenuDO> change(MenuDO row,ManagementTransactions.Actor actor) {
        return new LambdaUpdateWrapper<MenuDO>().eq(MenuDO::getId,row.getId()).eq(MenuDO::getAuthVersion,row.getAuthVersion())
                .set(MenuDO::getAuthVersion,row.getAuthVersion()+1).set(MenuDO::getUpdater,actor.getId().toString()).set(MenuDO::getUpdateTime,actor.getNow());
    }
    private Long recorded(ManagementTransactions.Actor actor,MenuDO row,String action,Map<String,Object> details) {
        audit.record(actor,0L,action,row.getId(),row.getAuthVersion(),row.getAuthVersion()+1,details);return row.getAuthVersion()+1;
    }
    private void requireChanged(int count) { if(count!=1) { throw exception0(409,"资源已变化，请重新读取"); } }
}
