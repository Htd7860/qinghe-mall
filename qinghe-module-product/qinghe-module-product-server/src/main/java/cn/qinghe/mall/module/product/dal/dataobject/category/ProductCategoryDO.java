package cn.qinghe.mall.module.product.dal.dataobject.category;

import com.baomidou.mybatisplus.annotation.IdType;

import cn.qinghe.mall.framework.common.enums.CommonStatusEnum;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 商品分类 DO
 *
 * @author 芋道源码
 */
@TableName("product_category")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryDO extends TenantBaseDo {

    /**
     * 父分类编号 - 根分类
     */
    public static final Long PARENT_ID_NULL = 0L;
    /**
     * 最大分类层级；可挂商品的是启用的叶子节点，并非强制第三级。
     */
    public static final int CATEGORY_LEVEL = 3;

    /**
     * 分类编号
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 父分类编号
     */
    private Long parentId;
    /**
     * 分类名称
     */
    private String name;
    /**
     * 移动端分类图
     *
     * 建议 180*180 分辨率
     */
    private String picUrl;
    /**
     * 分类排序
     */
    private Integer sort;
    /**
     * 开启状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /** 当前资料版本，供后台更新时检查旧表单。 */
    private Long authVersion;

    /** 对应原版保存VO已有的分类描述。 */
    private String description;

}
