package cn.qinghe.mall.module.promotion.dal.dataobject.coupon;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import cn.qinghe.mall.module.promotion.enums.common.PromotionDiscountTypeEnum;
import cn.qinghe.mall.module.promotion.enums.common.PromotionProductScopeEnum;
import cn.qinghe.mall.module.promotion.enums.coupon.CouponStatusEnum;
import cn.qinghe.mall.module.promotion.enums.coupon.CouponTakeTypeEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠劵 DO
 *
 * @author 芋道源码
 */
@TableName(value = "promotion_coupon", autoResultMap = true)
@Data
@lombok.ToString(onlyExplicitlyIncluded=true)
@EqualsAndHashCode(callSuper = true)
public class CouponDO extends TenantBaseDo {

    // ========== 基本信息 BEGIN ==========
    /**
     * 优惠劵编号
     */
    @TableId(type=IdType.AUTO)
    private Long id;
    /**
     * 优惠劵模板编号
     *
     * 关联 {@link CouponTemplateDO#getId()}
     */
    private Long templateId;
    /**
     * 优惠劵名
     *
     * 冗余 {@link CouponTemplateDO#getName()}
     */
    private String name;
    /**
     * 优惠码状态
     *
     * 枚举 {@link CouponStatusEnum}
     */
    private Integer status;

    // TODO 芋艿：发放 adminid？

    // ========== 基本信息 END ==========

    // ========== 领取情况 BEGIN ==========
    /**
     * 用户编号
     *
     * 关联 MemberUserDO 的 id 字段
     */
    private Long userId;
    /**
     * 领取类型
     *
     * 枚举 {@link CouponTakeTypeEnum}
     */
    private Integer takeType;
    // ========== 领取情况 END ==========

    // ========== 使用规则 BEGIN ==========
    /**
     * 是否设置满多少金额可用，单位：分
     *
     * 冗余 {@link CouponTemplateDO#getUsePrice()}
     */
    private Integer usePrice;
    /**
     * 生效开始时间
     */
    private LocalDateTime validStartTime;
    /**
     * 生效结束时间
     */
    private LocalDateTime validEndTime;
    /**
     * 商品范围
     *
     * 枚举 {@link PromotionProductScopeEnum}
     */
    private Integer productScope;
    /**
     * 商品范围编号的数组
     *
     * 冗余 {@link CouponTemplateDO#getProductScopeValues()}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> productScopeValues;
    // ========== 使用规则 END ==========

    // ========== 使用效果 BEGIN ==========
    /**
     * 折扣类型
     *
     * 冗余 {@link CouponTemplateDO#getDiscountType()}
     */
    private Integer discountType;
    /**
     * 折扣百分比
     *
     * 冗余 {@link CouponTemplateDO#getDiscountPercent()}
     */
    private Integer discountPercent;
    /**
     * 优惠金额，单位：分
     *
     * 冗余 {@link CouponTemplateDO#getDiscountPrice()}
     */
    private Integer discountPrice;
    /**
     * 折扣上限，仅在 {@link #discountType} 等于 {@link PromotionDiscountTypeEnum#PERCENT} 时生效
     *
     * 冗余 {@link CouponTemplateDO#getDiscountLimitPrice()}
     */
    private Integer discountLimitPrice;
    // ========== 使用效果 END ==========

    // ========== 使用情况 BEGIN ==========
    /**
     * 使用订单号
     */
    private Long useOrderId;
    /**
     * 使用时间
     */
    private LocalDateTime useTime;

    // ========== 使用情况 END ==========

    /** 配置/会话元数据版本；发放计数不递增模板配置版本。 */
    private Long authVersion;
    private Long templateVersion;
    /** 可信发放调用者提供的稳定键，仅用于成功发放结果恢复。 */
    private String grantKey;
    private Long lockedOrderId;
    private LocalDateTime lockedTime;
    private LocalDateTime invalidatedTime;
}
