package cn.qinghe.mall.module.product.dal.dataobject.property;

import com.baomidou.mybatisplus.annotation.IdType;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;


/**
 * 商品属性值 DO
 *
 * @author 芋道源码
 */
@TableName("product_property_value")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPropertyValueDO extends TenantBaseDo {

    /**
     * SPU 单规格时，默认属性值 id
     */
    public static final Long ID_DEFAULT = 0L;
    /**
     * SPU 单规格时，默认属性值名字
     */
    public static final String NAME_DEFAULT = "默认";

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 属性项的编号
     *
     * 关联 {@link ProductPropertyDO#getId()}
     */
    private Long propertyId;
    /**
     * 名称
     */
    private String name;
    /**
     * 备注
     *
     */
    private String remark;

    /** 当前资料版本，供后台更新时检查旧表单。 */
    private Long authVersion;

    /** 0启用，1停用，停用后不可用于新发布。 */
    private Integer status;

}
