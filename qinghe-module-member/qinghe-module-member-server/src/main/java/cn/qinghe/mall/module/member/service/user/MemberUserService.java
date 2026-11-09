package cn.qinghe.mall.module.member.service.user;

/** 择取yudao会员创建/找回密码职责；当前只供已权威确定店铺的后端调用。
 * 调用者须按服务端Host解析并核对租户的注册/找回资格，不得直接使用浏览器自报tenantId。
 * 不得带外层事务；不返回密码/验证码，不签发Token。
 * @author 芋道源码
 */
public interface MemberUserService {
    /** 手机号在租户内注册；registerIp必须由可信传输层获取，当前终端固定H5。 */
    Long register(Long tenantId,String mobile,String password,String code,String registerIp);

    /** RESET_PASSWORD验证码与密码、凭据代次、成功记录在同一member库事务内提交。 */
    void resetUserPassword(Long tenantId,String mobile,String password,String code);
}
