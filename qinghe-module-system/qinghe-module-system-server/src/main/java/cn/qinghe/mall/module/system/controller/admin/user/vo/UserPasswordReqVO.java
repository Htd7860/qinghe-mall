package cn.qinghe.mall.module.system.controller.admin.user.vo;
import cn.qinghe.mall.module.system.controller.admin.management.vo.VersionedIdReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;
@Data
@EqualsAndHashCode(callSuper=true)
@ToString(onlyExplicitlyIncluded=true)
public class UserPasswordReqVO extends VersionedIdReqVO {
    @NotBlank @Size(min=8,max=100) private String password;
}
