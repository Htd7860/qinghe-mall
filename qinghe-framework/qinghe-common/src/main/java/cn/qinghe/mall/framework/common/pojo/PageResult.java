package cn.qinghe.mall.framework.common.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.common.pojo
 * @Project：qinghe-mall
 * @name：PageResult
 * @Date：2026/10/20 13:57
 * @Filename：PageResult
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    @Schema(description = "总量",requiredMode = Schema.RequiredMode.REQUIRED)
    private Long total;
    @Schema(description = "数据",requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> list;


    public PageResult(Long total){
        this.list=new ArrayList<>();
        this.total=total;
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L);
    }

    public static <T> PageResult<T> empty(Long total) {
        return new PageResult<>(total);
    }
}
