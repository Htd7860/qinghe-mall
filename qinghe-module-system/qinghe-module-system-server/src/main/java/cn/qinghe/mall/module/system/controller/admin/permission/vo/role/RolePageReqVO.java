package cn.qinghe.mall.module.system.controller.admin.permission.vo.role;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/** 租户由认证上下文确定，不允许通过分页条件选择租户。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageReqVO extends PageParam {

    @javax.validation.constraints.NotNull
    private Boolean deleted = false;

    @Size(max = 30, message = "角色名称筛选最长30个字符")
    private String name;

    @Size(max = 100, message = "角色码筛选最长100个字符")
    private String code;

    @Min(value = 0, message = "角色状态只能为0或1")
    @Max(value = 1, message = "角色状态只能为0或1")
    private Integer status;

    @Size(max = 2, message = "创建时间最多包含开始和结束两个端点")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;

    @AssertTrue(message = "创建时间的开始不能晚于结束")
    public boolean isCreateTimeRangeValid() {
        return createTime == null || createTime.length < 2
                || createTime[0] == null || createTime[1] == null
                || !createTime[0].isAfter(createTime[1]);
    }
}
