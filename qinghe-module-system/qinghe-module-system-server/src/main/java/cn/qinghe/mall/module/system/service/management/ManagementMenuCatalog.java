package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.module.system.dal.dataobject.permission.MenuDO;
import cn.qinghe.mall.module.system.dal.mysql.permission.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
@Component
@RequiredArgsConstructor
public class ManagementMenuCatalog {
    private final MenuMapper menuMapper;
    public List<cn.qinghe.mall.module.system.controller.admin.permission.vo.menu.MenuSimpleRespVO> merchantOptions() {
        ManagementTransactions.requirePlatformRead();
        return menuMapper.selectListByRealm("merchant").stream().map(row -> {
            cn.qinghe.mall.module.system.controller.admin.permission.vo.menu.MenuSimpleRespVO out =
                    new cn.qinghe.mall.module.system.controller.admin.permission.vo.menu.MenuSimpleRespVO();
            out.setId(row.getId()); out.setName(row.getName()); out.setParentId(row.getParentId());
            out.setType(row.getType()); out.setSort(row.getSort()); out.setPermission(row.getPermission());
            out.setStatus(row.getStatus()); return out;
        }).collect(Collectors.toList());
    }
    /** 保留全局停用资源的原配置；新增资源必须连同父链启用，删除资源不能残留。 */
    public Set<Long> validateSelection(String realm,Set<Long> requested,Set<Long> existing) {
        if (requested==null || requested.size()>10000 || requested.stream().anyMatch(id->id==null || id<=0)) {
            throw exception0(400,"资源集合不合法");
        }
        Map<Long,MenuDO> catalog=menuMapper.selectListByRealm(realm).stream().collect(Collectors.toMap(MenuDO::getId,m->m));
        Set<Long> target=new TreeSet<>(requested);
        for(Long id:target) {
            MenuDO row=catalog.get(id);
            if(row==null) { throw exception0(400,"资源不存在、已删除或不属于指定端"); }
            if(!Long.valueOf(0).equals(row.getParentId()) && !target.contains(row.getParentId())) {
                throw exception0(400,"套餐资源必须包含完整父级目录");
            }
            Long cursor=id;Set<Long> visited=new HashSet<>();
            while(cursor!=null && cursor>0 && visited.add(cursor)) {
                MenuDO ancestor=catalog.get(cursor);
                if(ancestor==null || (!existing.contains(id) && !Integer.valueOf(0).equals(ancestor.getStatus()))) {
                    throw exception0(400,"新增资源及其父级必须存在且启用");
                }
                cursor=ancestor.getParentId();
            }
            if(!Long.valueOf(0).equals(cursor)) { throw exception0(400,"资源父链不完整或循环"); }
        }
        return target;
    }
    public List<Map<String,Object>> fingerprintState(String realm,Set<Long> requested) {
        List<Map<String,Object>> state=new ArrayList<>();
        menuMapper.selectListByRealm(realm).stream().filter(m->requested.contains(m.getId()))
                .sorted(Comparator.comparing(MenuDO::getId)).forEach(m -> {
                    Map<String,Object> row=new TreeMap<>();row.put("id",m.getId());row.put("parentId",m.getParentId());
                    row.put("permission",m.getPermission());row.put("status",m.getStatus());row.put("type",m.getType());state.add(row);
                });
        return state;
    }
}
