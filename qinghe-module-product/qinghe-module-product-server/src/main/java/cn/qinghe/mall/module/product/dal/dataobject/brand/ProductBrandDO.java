package cn.qinghe.mall.module.product.dal.dataobject.brand;

import com.baomidou.mybatisplus.annotation.IdType;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 商品品牌 DO
 *
 * @author 芋道源码
 */
@TableName("product_brand")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBrandDO extends TenantBaseDo {

    /**
     * 品牌编号
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 品牌名称
     */
    private String name;
    /**
     * 品牌图片
     */
    private String picUrl;
    /**
     * 品牌排序
     */
    private Integer sort;
    /**
     * 品牌描述
     */
    private String description;
    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /** 当前资料版本，供后台更新时检查旧表单。 */
    private Long authVersion;

}
