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
 * 快递运费模板包邮配置 DO
 *
 * @author jason
 */
@TableName(value ="trade_delivery_express_template_free", autoResultMap = true)
@Data
public class DeliveryExpressTemplateFreeDO extends TenantBaseDo {

    /**
     * 编号
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
     * 包邮金额，单位：分
     *
     * 本模板商品优惠后实付款（不含运费） >= 包邮金额时，才免运费
     */
    private Integer freePrice;

    /**
     * 包邮件数；按重量计费也仍按件数判断
     *
     * 订单总件数 >= 包邮件数时，才免运费
     */
    private Integer freeCount;

    private Boolean defaultRule;

}
