package cn.qinghe.mall.module.member.dal.mysql.sms;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.member.dal.dataobject.sms.MemberSmsCodeDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
/** 原selectLastByMobile的最新场景查询；不再按明文code回查旧记录，所有状态修改均有前置状态条件。
 * @author 芋道源码
 */
@Mapper
public interface MemberSmsCodeMapper extends BaseMapperX<MemberSmsCodeDO> {
    @Select("SELECT * FROM member_sms_code WHERE tenant_id=#{tenantId} AND mobile=#{mobile} AND scene=#{scene} AND deleted=b'0' ORDER BY id DESC LIMIT 1 FOR UPDATE")
    MemberSmsCodeDO latest(@Param("tenantId") Long tenantId,@Param("mobile") String mobile,@Param("scene") String scene);
    @Update("UPDATE member_sms_code SET state=#{state},api_request_id=#{requestId},serial_no=#{serialNo},update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND state='PENDING' AND deleted=b'0'")
    int finish(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("state") String state,
            @Param("requestId") String requestId,@Param("serialNo") String serialNo,@Param("now") LocalDateTime now);
    @Update("UPDATE member_sms_code SET failed_attempts=failed_attempts+1,state=#{state},update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND state='ACTIVE' AND failed_attempts=#{before} AND deleted=b'0'")
    int failed(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("before") Integer before,@Param("state") String state,@Param("now") LocalDateTime now);
    @Update("UPDATE member_sms_code SET state='EXPIRED',update_time=#{now} WHERE tenant_id=#{tenantId} AND id=#{id} AND state='ACTIVE' AND deleted=b'0'")
    int expire(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("now") LocalDateTime now);
    @Update("UPDATE member_sms_code SET state='USED',used_time=#{now},update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND state='ACTIVE' AND failed_attempts<#{maximum} AND expire_time>#{now} AND deleted=b'0'")
    int use(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("maximum") Integer maximum,@Param("now") LocalDateTime now);
}
