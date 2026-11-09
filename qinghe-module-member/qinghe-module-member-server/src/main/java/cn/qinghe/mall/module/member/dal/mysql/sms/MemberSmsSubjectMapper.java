package cn.qinghe.mall.module.member.dal.mysql.sms;
import cn.qinghe.mall.module.member.dal.dataobject.sms.MemberSmsSubjectDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Mapper
public interface MemberSmsSubjectMapper {
    @Insert("INSERT IGNORE INTO member_sms_subject(tenant_id,mobile,quota_date,daily_count) VALUES(#{tenantId},#{mobile},#{date},0)")
    int ensure(@Param("tenantId") Long tenantId,@Param("mobile") String mobile,@Param("date") LocalDate date);
    @Select("SELECT tenant_id,mobile,quota_date,daily_count,last_sent_at FROM member_sms_subject WHERE tenant_id=#{tenantId} AND mobile=#{mobile} FOR UPDATE")
    MemberSmsSubjectDO lock(@Param("tenantId") Long tenantId,@Param("mobile") String mobile);
    @Update("UPDATE member_sms_subject SET quota_date=#{date},daily_count=#{count},last_sent_at=#{now},update_time=#{now} WHERE tenant_id=#{tenantId} AND mobile=#{mobile}")
    int reserve(@Param("tenantId") Long tenantId,@Param("mobile") String mobile,@Param("date") LocalDate date,@Param("count") Integer count,@Param("now") LocalDateTime now);
}
