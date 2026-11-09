package cn.qinghe.mall.module.system.controller.admin.dept.vo;
import cn.qinghe.mall.module.system.dal.dataobject.dept.DeptDO;
import lombok.Data;
@Data
public class DeptRespVO {
    private Long id; private String name; private Long parentId;
    private Integer sort; private Integer status; private Long authVersion; private Boolean deleted;
    public static DeptRespVO from(DeptDO row) {
        DeptRespVO out = new DeptRespVO(); out.setId(row.getId()); out.setName(row.getName());
        out.setParentId(row.getParentId()); out.setSort(row.getSort()); out.setStatus(row.getStatus());
        out.setAuthVersion(row.getAuthVersion()); out.setDeleted(row.getDeleted()); return out;
    }
}
