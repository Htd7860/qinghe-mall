package cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate;
import lombok.Data;
/** 全局地区字典选项，无父子对象循环引用。 */
@Data
public class DeliveryAreaOptionRespVO {
    private Integer id;
    private Integer parentId;
    private Integer type;
    private String name;
}
