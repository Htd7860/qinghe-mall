package cn.qinghe.mall.module.member.dal.mysql.user;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
/** 择取yudao按mobile查询；显式固定可信租户，默认地址修改用会员父锁串行化。
 * @author 芋道源码
 */
@Mapper
public interface MemberUserMapper extends BaseMapperX<MemberUserDO> {
    @Update("UPDATE member_user SET marketing_opt_out=#{optOut},auth_version=auth_version+1,updater=#{userId},update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{userId} AND status=0 AND deleted=b'0' "
            + "AND auth_version=#{version} AND credential_version=#{credentialVersion} AND auth_version<9223372036854775807")
    int updatePreference(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("version") Long version,
            @Param("credentialVersion") Long credentialVersion,@Param("optOut") Boolean optOut);
    @Insert("INSERT INTO member_preference_audit(tenant_id,user_id,from_opt_out,to_opt_out,from_version,to_version) "
            + "VALUES(#{tenantId},#{userId},#{fromOptOut},#{toOptOut},#{fromVersion},#{toVersion})")
    int auditPreference(@Param("tenantId") Long tenantId,@Param("userId") Long userId,
            @Param("fromOptOut") Boolean fromOptOut,@Param("toOptOut") Boolean toOptOut,
            @Param("fromVersion") Long fromVersion,@Param("toVersion") Long toVersion);
    default Long tenant() {
        Long id=TenantContextHolder.getRequiredTenantId();
        if (id<=0 || TenantContextHolder.isIgnore()) { throw new IllegalStateException("会员查询需要可信商家租户"); }
        return id;
    }
    default MemberUserDO selectByMobile(String mobile) {
        if (mobile==null || !mobile.matches("^1[3-9][0-9]{9}$")) { throw new IllegalArgumentException("手机号格式不正确"); }
        return selectOne(new LambdaQueryWrapperX<MemberUserDO>().eq(MemberUserDO::getTenantId,tenant()).eq(MemberUserDO::getMobile,mobile));
    }
    default MemberUserDO selectCurrent(Long id,boolean lock) {
        if (id==null || id<=0) { throw new IllegalArgumentException("会员编号不正确"); }
        LambdaQueryWrapperX<MemberUserDO> query=new LambdaQueryWrapperX<MemberUserDO>().eq(MemberUserDO::getTenantId,tenant()).eq(MemberUserDO::getId,id);
        if (lock) { MybatisUtils.requireWriteTransaction();query.last("FOR UPDATE"); }
        return selectOne(query);
    }

    /** 注册/找回在验证码号码锁之后调用；含已删除身份，禁止删除后复用手机号身份。 */
    default MemberUserDO lockIdentityByMobile(String mobile) {
        MybatisUtils.requireWriteTransaction();
        if (mobile==null || !mobile.matches("^1[3-9][0-9]{9}$")) { throw new IllegalArgumentException("手机号格式不正确"); }
        return selectIdentityForUpdate(tenant(),mobile);
    }
    @Select("SELECT * FROM member_user WHERE tenant_id=#{tenantId} AND mobile=#{mobile} FOR UPDATE")
    MemberUserDO selectIdentityForUpdate(@Param("tenantId") Long tenantId,@Param("mobile") String mobile);

    /** 不接受请求DO，只有密码及两项服务端代次可以变更。 */
    @Update("UPDATE member_user SET password=#{encoded},auth_version=auth_version+1,credential_version=credential_version+1, "
            + "updater='sms-password-reset',update_time=CURRENT_TIMESTAMP(6) "
            + "WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=b'0' AND status=0 "
            + "AND auth_version=#{authVersion} AND credential_version=#{credentialVersion} "
            + "AND auth_version<9223372036854775807 AND credential_version<9223372036854775807")
    int resetPassword(@Param("tenantId") Long tenantId,@Param("id") Long id,@Param("encoded") String encoded,
            @Param("authVersion") Long authVersion,@Param("credentialVersion") Long credentialVersion);

    @Insert("INSERT INTO member_account_audit(tenant_id,user_id,action,from_auth_version,to_auth_version, "
            + "from_credential_version,to_credential_version) "
            + "VALUES(#{tenantId},#{userId},#{action},#{fromAuth},#{toAuth},#{fromCredential},#{toCredential})")
    int auditAccount(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("action") String action,
            @Param("fromAuth") Long fromAuth,@Param("toAuth") Long toAuth,
            @Param("fromCredential") Long fromCredential,@Param("toCredential") Long toCredential);

    /** 最近登录仅为元数据，不改变权限/凭据代次；真实会员父锁后调用。 */
    @Update("UPDATE member_user SET login_ip=#{peer},login_date=#{now},updater='member-login',update_time=#{now} "
            + "WHERE tenant_id=#{tenantId} AND id=#{userId} AND status=0 AND deleted=b'0' AND credential_version=#{credentialVersion}")
    int updateLogin(@Param("tenantId") Long tenantId,@Param("userId") Long userId,
            @Param("credentialVersion") Long credentialVersion,@Param("peer") String peer,@Param("now") LocalDateTime now);
}
