package cn.qinghe.mall.module.system.api.oauth2.dto;

import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 内部消费者令牌对；不输出toString，消费者身份查询由单独接口负责。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class MemberTokenIssueRespDTO {
    private Long tenantId;
    private Long userId;
    private Long credentialVersion;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresTime;
    private LocalDateTime refreshExpiresTime;
}
