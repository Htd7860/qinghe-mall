package cn.qinghe.mall.module.infra.controller.admin.file.vo.file;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/** 取自 yudao FilePageReqVO；时间格式使用已有 Spring 注解，不引入整个 DateUtils。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FilePageReqVO extends PageParam {
    @Size(max = 255)
    private String path;
    @Size(max = 127)
    private String type;
    @Size(max = 2)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime[] createTime;
}
