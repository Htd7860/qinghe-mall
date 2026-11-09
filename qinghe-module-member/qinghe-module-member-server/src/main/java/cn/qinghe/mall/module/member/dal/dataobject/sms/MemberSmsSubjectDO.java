package cn.qinghe.mall.module.member.dal.dataobject.sms;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDate;
import java.time.LocalDateTime;
/** 注册前没有member_user父行，用tenant/mobile唯一行串行控制跨场景发送额度。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class MemberSmsSubjectDO {
    private Long tenantId;
    private String mobile;
    private LocalDate quotaDate;
    private Integer dailyCount;
    private LocalDateTime lastSentAt;
}
