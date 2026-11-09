package cn.qinghe.mall.module.trade.dal.dataobject.delivery;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

/**
 * 快递运费模板计费配置 DO
 *
 * @author jason
 */
@TableName(value ="trade_delivery_express_template_charge", autoResultMap = true)
@Data
public class DeliveryExpressTemplateChargeDO extends TenantBaseDo {

    /**
     * 编号，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配送模板编号
     *
     * 关联 {@link DeliveryExpressTemplateDO#getId()}
     */
    private Long templateId;

    /**
     * 配送区域编号列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> areaIds;

    /**
     * 配送计费方式
     *
     * 冗余 {@link DeliveryExpressTemplateDO#getChargeMode()}
     */
    private Integer chargeMode;

    /**
     * 首件数量(件数,重量，或体积)
     */
    private java.math.BigDecimal startCount;
    /**
     * 起步价，单位：分
     */
    private Integer startPrice;

    /**
     * 续件数量(件, 重量，或体积)
     */
    private java.math.BigDecimal extraCount;
    /**
     * 额外价，单位：分
     */
    private Integer extraPrice;

    /** 明确默认规则；默认区域列表为空，区域规则不允许互相重叠。 */
    private Boolean defaultRule;

}
