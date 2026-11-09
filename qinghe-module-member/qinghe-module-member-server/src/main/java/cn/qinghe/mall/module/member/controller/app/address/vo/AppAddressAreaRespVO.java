package cn.qinghe.mall.module.member.controller.app.address.vo;
import lombok.Data;
/** 原Area树只投影编号/父级/名称/层级，避免循环序列化。 */
@Data
public class AppAddressAreaRespVO {
    private Integer id;
    private Integer parentId;
    private String name;
    private Integer type;
}
