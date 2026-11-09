package cn.qinghe.mall.module.trade.dal.mysql.cart;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.trade.dal.dataobject.cart.CartDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
/** 原本人购物车查询/更新职责；固定tenant+user白名单，不接浏览器归属字段。
 * @author 芋道源码
 */
@Mapper
public interface CartMapper extends BaseMapperX<CartDO> {
    default List<CartDO> list(Long tenant,Long user) {
        return selectList(new LambdaQueryWrapperX<CartDO>().eq(CartDO::getTenantId,tenant).eq(CartDO::getUserId,user).orderByDesc(CartDO::getId).last("LIMIT 201"));
    }
    default CartDO find(Long tenant,Long user,Long id){return selectOne(new LambdaQueryWrapperX<CartDO>().eq(CartDO::getTenantId,tenant).eq(CartDO::getUserId,user).eq(CartDO::getId,id));}
    default CartDO findSku(Long tenant,Long user,Long sku){return selectOne(new LambdaQueryWrapperX<CartDO>().eq(CartDO::getTenantId,tenant).eq(CartDO::getUserId,user).eq(CartDO::getSkuId,sku));}
    @Update("UPDATE trade_cart SET count=#{count},selected=#{selected},auth_version=auth_version+1,updater=#{user},update_time=CURRENT_TIMESTAMP(6) "
        + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND deleted=b'0' AND auth_version<9007199254740991")
    int change(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,@Param("count") Integer count,@Param("selected") Boolean selected);
    @Update("UPDATE trade_cart SET deleted=b'1',auth_version=auth_version+1,updater=#{user},update_time=CURRENT_TIMESTAMP(6) "
        + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND deleted=b'0' AND auth_version<9007199254740991")
    int retire(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id);
    /** 建单后只清理仍等于原报价的行，保留用户在处理中改过的数量/勾选/规格。 */
    @Update("UPDATE trade_cart SET deleted=b'1',auth_version=auth_version+1,updater=#{user},update_time=CURRENT_TIMESTAMP(6) "
        + "WHERE tenant_id=#{tenant} AND user_id=#{user} AND id=#{id} AND sku_id=#{sku} AND count=#{count} "
        + "AND deleted=b'0' AND auth_version=#{version} AND auth_version<9007199254740991")
    int retirePurchased(@Param("tenant") Long tenant,@Param("user") Long user,@Param("id") Long id,
            @Param("sku") Long sku,@Param("count") Integer count,@Param("version") Long version);
    @Insert("INSERT INTO trade_cart_user(tenant_id,user_id,auth_version) VALUES(#{tenant},#{user},0) ON DUPLICATE KEY UPDATE user_id=user_id")
    int ensureUser(@Param("tenant") Long tenant,@Param("user") Long user);
    @Select("SELECT auth_version FROM trade_cart_user WHERE tenant_id=#{tenant} AND user_id=#{user} FOR UPDATE")
    Long lockVersion(@Param("tenant") Long tenant,@Param("user") Long user);
    @Select("SELECT auth_version FROM trade_cart_user WHERE tenant_id=#{tenant} AND user_id=#{user}")
    Long readVersion(@Param("tenant") Long tenant,@Param("user") Long user);
    @Update("UPDATE trade_cart_user SET auth_version=auth_version+1,update_time=CURRENT_TIMESTAMP(6) WHERE tenant_id=#{tenant} AND user_id=#{user} AND auth_version=#{expected} AND auth_version<9007199254740991")
    int bump(@Param("tenant") Long tenant,@Param("user") Long user,@Param("expected") Long expected);
}
