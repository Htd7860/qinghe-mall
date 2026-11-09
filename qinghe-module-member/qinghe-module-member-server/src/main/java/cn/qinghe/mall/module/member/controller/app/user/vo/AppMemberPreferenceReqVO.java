package cn.qinghe.mall.module.member.controller.app.user.vo;
import lombok.Data;
import javax.validation.constraints.*;
/** 本项目已确认的营销退出字段；不接受整个会员DO回写。 */
@Data
public class AppMemberPreferenceReqVO {
    @NotNull private Boolean marketingOptOut;
    @NotNull @Min(0) private Long expectedVersion;
}
