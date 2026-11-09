package cn.qinghe.mall.module.system.api.tenant.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 请求Host必须由受信member从实际HTTP请求提取，不能从浏览器JSON读取。 */
@Data
public class MemberStorefrontReqDTO {
    @NotBlank @Size(max=300) private String requestHost;
    @Pattern(regexp="[a-z0-9-]{3,32}") private String store;
}
