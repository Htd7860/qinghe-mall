package cn.qinghe.mall.module.system.dal.mysql.oauth2;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.mysql.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2RefreshTokenMapper
 * @Date：2026/10/23 20:30
 * @Filename：OAuth2RefreshTokenMapper
 */
@Mapper
public interface OAuth2RefreshTokenMapper extends BaseMapperX<OAuth2RefreshTokenDO> {
    default OAuth2RefreshTokenDO selectByRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || refreshToken.length() > 128
                || refreshToken.chars().anyMatch(ch -> Character.isWhitespace(ch)
                || Character.isSpaceChar(ch)
                || Character.isISOControl(ch)
                || ch == ',')) {
            throw new IllegalArgumentException("刷新令牌格式不合法");
        }

        return TenantUtils.executeIgnore(() ->
                selectOne(new LambdaQueryWrapper<OAuth2RefreshTokenDO>()
                        .eq(OAuth2RefreshTokenDO::getRefreshToken, refreshToken)));
    }

    /**
     * 在可写事务中锁定父刷新记录，协调同一会话的刷新与撤销。
     * 仅本次精确凭据查询忽略租户，返回后仍由Service核对身份归属。
     */
    default OAuth2RefreshTokenDO selectByRefreshTokenForUpdate(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || refreshToken.length() > 128
                || refreshToken.chars().anyMatch(ch -> Character.isWhitespace(ch)
                || Character.isSpaceChar(ch) || Character.isISOControl(ch) || ch == ',')) {
            throw new IllegalArgumentException("刷新令牌格式不合法");
        }
        return TenantUtils.executeIgnore(() -> selectOne(
                new LambdaQueryWrapper<OAuth2RefreshTokenDO>()
                        .eq(OAuth2RefreshTokenDO::getRefreshToken, refreshToken)
                        .last("FOR UPDATE")));
    }
}
