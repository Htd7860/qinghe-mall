package cn.qinghe.mall.module.member.dal.dataobject.logger;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 择取yudao LoginLogDO的尝试标识/用户/结果/时刻；账号与来源只存摘要，不存凭据。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
@TableName("member_login_log")
public class MemberLoginLogDO {
    @TableId(type=IdType.AUTO) private Long id;
    private Long tenantId;
    private Long userId;
    private String traceId;
    private String subjectHash;
    private String peerHash;
    private String result;
    private Long credentialVersion;
    private LocalDateTime createTime;
}
