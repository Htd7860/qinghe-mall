package cn.qinghe.mall.module.trade.dal.dataobject.delivery;

import cn.qinghe.mall.framework.mybatis.core.dataobject.BaseDo;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 快递公司；择取原全局承运商目录，不包含月结账号或物流渠道凭据。
 * @author jason
 */
@TableName("trade_delivery_express") @TenantIgnore @Data
public class DeliveryExpressDO extends BaseDo {
    @TableId(type=IdType.AUTO) private Long id;
    private String code;
    private String name;
    private String logo;
    private Integer sort;
    private Integer status;
}
