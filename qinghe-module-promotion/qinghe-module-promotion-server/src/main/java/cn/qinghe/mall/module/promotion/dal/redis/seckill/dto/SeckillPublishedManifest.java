package cn.qinghe.mall.module.promotion.dal.redis.seckill.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

/** 原发布的不可变定义缓存；不含可用库存、会员占额、epoch就绪或内部划拨操作号。 */
@Data
@JsonPropertyOrder({"schema","tenantId","activityId","activityNo","generation","publishRequestId",
        "fromVersion","preparedVersion","productSpuId","productSpuVersion","startAt","endAt","singleLimit","memberLimit","skus"})
public class SeckillPublishedManifest {
    private final int schema=1;
    @NotNull @Min(1) @Max(9007199254740991L) private Long tenantId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long activityId;
    @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String activityNo;
    @NotNull @Min(1) private Integer generation;
    @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String publishRequestId;
    @NotNull @Min(0) @Max(9007199254740990L) private Long fromVersion;
    @NotNull @Min(1) @Max(9007199254740991L) private Long preparedVersion;
    @NotNull @Min(1) @Max(9007199254740991L) private Long productSpuId;
    @NotNull @Min(0) @Max(9007199254740991L) private Long productSpuVersion;
    @NotNull @Min(946684800000L) @Max(4102444799999L) private Long startAt;
    @NotNull @Min(946684800000L) @Max(4102444799999L) private Long endAt;
    @NotNull @Min(1) @Max(9999) private Integer singleLimit;
    @NotNull @Min(1) private Integer memberLimit;
    @NotEmpty @Size(max=200) @Valid private List<Sku> skus;

    @Data
    @JsonPropertyOrder({"skuId","skuVersion","priceCent","plannedQuantity"})
    public static class Sku {
        @NotNull @Min(1) @Max(9007199254740991L) private Long skuId;
        @NotNull @Min(0) @Max(9007199254740991L) private Long skuVersion;
        @NotNull @Min(1) @Max(2000000000) private Integer priceCent;
        @NotNull @Min(1) private Integer plannedQuantity;
    }
}
