package cn.qinghe.mall.module.trade.dal.dataobject.delivery;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import cn.qinghe.mall.module.trade.enums.delivery.DeliveryExpressChargeModeEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 快递运费模板 DO
 *
 * @author jason
 */
@TableName("trade_delivery_express_template")
@Data
public class DeliveryExpressTemplateDO extends TenantBaseDo {

    /**
     * 编号，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 配送计费方式
     *
     * 枚举 {@link DeliveryExpressChargeModeEnum}
     */
    private Integer chargeMode;

    /**
     * 排序
     */
    private Integer sort;

    /** 0启用/1停用，配置版本由未来管理Service推进。 */
    private Integer status;
    private Long authVersion;

}
