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
import java.util.Map;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.dataobject.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2AccessTokenDO
 * @Date：2026/10/23 19:48
 * @Filename：OAuth2AccessTokenDO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
@TableName(value = "system_oauth2_access_token", autoResultMap = true)
public class OAuth2AccessTokenDO extends TenantBaseDo {

    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 访问令牌
     */
    private String accessToken;
    /**
     * 刷新令牌
     */
    private String refreshToken;
    /**
     * 用户编号
     */
    private Long userId;

    private Integer userType;
    /**
     * 用户信息
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> userInfo;

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
