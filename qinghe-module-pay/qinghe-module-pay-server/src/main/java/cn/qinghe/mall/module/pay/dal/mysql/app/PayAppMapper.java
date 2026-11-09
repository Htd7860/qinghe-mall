package cn.qinghe.mall.module.pay.dal.mysql.app;
import cn.qinghe.mall.module.pay.dal.dataobject.app.PayAppDO;
import org.apache.ibatis.annotations.*;
/** 原支付应用查询职责；appKey在租户内唯一，不让商家共用另一商家的支付配置。 */
@Mapper
public interface PayAppMapper {
    @Select("SELECT id,tenant_id,app_key,name,status,auth_version,create_time,update_time FROM pay_app WHERE tenant_id=#{tenant} AND app_key=#{key}")
    PayAppDO find(@Param("tenant") Long tenant,@Param("key") String key);
}
