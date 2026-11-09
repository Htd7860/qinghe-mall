package cn.qinghe.mall.module.product.controller.admin.spu.vo;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import javax.validation.constraints.*;

@Schema(description = "管理后台 - 商品 SPU 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProductSpuPageReqVO extends PageParam {

    /**
     * 出售中商品
     */
    public static final Integer FOR_SALE = 0;

    /**
     * 仓库中商品
     */
    public static final Integer IN_WAREHOUSE = 1;

    /**
     * 已售空商品
     */
    public static final Integer SOLD_OUT = 2;

    /**
     * 警戒库存
     */
    public static final Integer ALERT_STOCK = 3;

    /**
     * 商品回收站
     */
    public static final Integer RECYCLE_BIN = 4;

    @Schema(description = "商品名称", example = "清凉小短袖")
    @Size(max=128)
    private String name;

    @Schema(description = "前端请求的tab类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Min(0) @Max(4)
    private Integer tabType;

    @Schema(description = "商品分类编号", example = "1")
    @Min(1)
    private Long categoryId;

    @Schema(description = "创建时间", example = "[2022-07-01 00:00:00, 2022-07-01 23:59:59]")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Size(max=2)
    private LocalDateTime[] createTime;

    @Min(1) private Long brandId;

}
