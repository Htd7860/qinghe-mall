package cn.qinghe.mall.module.system.controller.admin.user.vo;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageReqVO extends PageParam {
    @Size(max=30) private String username;
    @Size(max=30) private String nickname;
    @Min(0) @Max(1) private Integer status;
    @Min(1) private Long deptId;
    @NotNull private Boolean deleted = false;
}
