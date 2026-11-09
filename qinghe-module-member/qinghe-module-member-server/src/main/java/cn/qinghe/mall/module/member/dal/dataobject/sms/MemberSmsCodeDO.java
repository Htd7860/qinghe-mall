package cn.qinghe.mall.module.member.dal.dataobject.sms;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import java.time.LocalDateTime;
/** 从yudao SmsCodeDO提取手机号/场景/当天次数/使用信息；编码替代明文code，并增加发送状态。
 * @author 芋道源码
 */
@Data
@EqualsAndHashCode(callSuper=true)
@ToString(onlyExplicitlyIncluded=true)
@TableName("member_sms_code")
public class MemberSmsCodeDO extends TenantBaseDo {
    @TableId(type=IdType.AUTO) private Long id;
    private String mobile;
    private String scene;
    private String providerMode;
    private String state;
    @JsonIgnore private String codeHash;
    private Integer failedAttempts;
    private Integer todayIndex;
    private LocalDateTime expireTime;
    private LocalDateTime usedTime;
    private String apiRequestId;
    private String serialNo;
}
