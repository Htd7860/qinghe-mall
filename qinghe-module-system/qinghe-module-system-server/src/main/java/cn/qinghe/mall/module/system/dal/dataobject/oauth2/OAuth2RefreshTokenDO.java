package cn.qinghe.mall.module.system.dal.dataobject.oauth2;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.dataobject.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2RefreshTokenDO
 * @Date：2026/10/23 19:48
 * @Filename：OAuth2RefreshTokenDO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
@TableName(value = "system_oauth2_refresh_token", autoResultMap = true)
public class OAuth2RefreshTokenDO extends TenantBaseDo {

    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 刷新令牌
     */
    private String refreshToken;
    /**
     * 用户编号
     */
    private Long userId;
    /**
     * 用户类型
     *

     */
    private Integer userType;

    /** 签发时的账号凭据版本；旧refresh不能跨越密码重置或账号恢复。 */
    private Long credentialVersion;

    private String clientId;
    /**
     * 授权范围
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scopes;
    /**
     * 过期时间
     */
    private LocalDateTime expiresTime;

    /**
     * 主动撤销时间，null 表示尚未主动撤销
     */
    private LocalDateTime revokedTime;
}
