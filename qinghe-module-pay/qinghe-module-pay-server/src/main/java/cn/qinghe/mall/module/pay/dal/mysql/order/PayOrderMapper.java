package cn.qinghe.mall.module.pay.dal.mysql.order;
import cn.qinghe.mall.module.pay.dal.dataobject.order.PayOrderDO;
import org.apache.ibatis.annotations.*;

/** 择取原selectByAppIdAndMerchantOrderId与insert；业务唯一键裁定并发重试。
 * @author 芋道源码
 */
@Mapper
public interface PayOrderMapper {
    @Select("SELECT * FROM pay_order WHERE tenant_id=#{tenant} AND id=#{id}")
    PayOrderDO get(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM pay_order WHERE tenant_id=#{tenant} AND id=#{id} FOR UPDATE")
    PayOrderDO lockId(@Param("tenant") Long tenant,@Param("id") Long id);
    @Update("UPDATE pay_order SET refund_reserved=refund_reserved+#{price},auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND status IN(10,20) AND closing=b'0' AND auth_version=#{version} "
        + "AND auth_version<9007199254740991 AND refund_reserved=0 AND refund_price+#{price}<=price")
    int reserveRefund(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,
        @Param("price") Long price,@Param("now") java.time.LocalDateTime now);
    @Update("UPDATE pay_order SET status=20,refund_price=refund_price+#{price},refund_reserved=refund_reserved-#{price},"
        + "auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} AND status IN(10,20) AND closing=b'0' "
        + "AND auth_version=#{version} AND auth_version<9007199254740991 AND refund_reserved>=#{price} AND refund_price+#{price}<=price")
    int refundSucceeded(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,
        @Param("price") Long price,@Param("now") java.time.LocalDateTime now);
    @Update("UPDATE pay_order SET closing=b'1',auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND status=0 AND closing=b'0' AND auth_version=#{version} AND auth_version<9007199254740991")
    int beginClose(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") java.time.LocalDateTime now);
    @Update("UPDATE pay_order SET status=30,auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND status=0 AND closing=b'1' AND auth_version=#{version} AND auth_version<9007199254740991")
    int finishClose(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") java.time.LocalDateTime now);
    @Update("UPDATE pay_order SET status=10,closing=b'0',success_time=#{paid},auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND status=0 AND auth_version=#{version} AND auth_version<9007199254740991")
    int paid(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,
        @Param("paid") java.time.LocalDateTime paid,@Param("now") java.time.LocalDateTime now);
    @Select("SELECT * FROM pay_order WHERE tenant_id=#{tenant} AND app_id=#{app} AND merchant_order_id=#{order}")
    PayOrderDO find(@Param("tenant") Long tenant,@Param("app") Long app,@Param("order") String order);
    @Select("SELECT * FROM pay_order WHERE tenant_id=#{tenant} AND app_id=#{app} AND merchant_order_id=#{order} FOR UPDATE")
    PayOrderDO lock(@Param("tenant") Long tenant,@Param("app") Long app,@Param("order") String order);
    // Duplicate registration obtains the original row write lock without replacing its immutable fields.
    @Insert("INSERT INTO pay_order(tenant_id,app_id,user_id,user_type,merchant_order_id,subject,price,status,expire_time,refund_price,auth_version,create_time,update_time) "
        + "SELECT #{row.tenantId},#{row.appId},#{row.userId},#{row.userType},#{row.merchantOrderId},#{row.subject},#{row.price},0,#{row.expireTime},0,0,#{row.createTime},#{row.updateTime} "
        + "FROM pay_app WHERE tenant_id=#{row.tenantId} AND id=#{row.appId} AND status=0 ON DUPLICATE KEY UPDATE merchant_order_id=pay_order.merchant_order_id")
    int ensure(@Param("row") PayOrderDO row);

    /** 关闭先到时使用同一业务唯一键留账，阻止晚到create重新建立付款入口；停用应用也可清理历史订单。 */
    // Duplicate registration obtains the original row write lock without replacing its immutable fields.
    @Insert("INSERT INTO pay_order(tenant_id,app_id,user_id,user_type,merchant_order_id,subject,price,status,closing,expire_time,refund_price,auth_version,create_time,update_time) "
        + "SELECT #{row.tenantId},#{row.appId},#{row.userId},#{row.userType},#{row.merchantOrderId},#{row.subject},#{row.price},30,b'1',#{row.expireTime},0,0,#{row.createTime},#{row.updateTime} "
        + "FROM pay_app WHERE tenant_id=#{row.tenantId} AND id=#{row.appId} ON DUPLICATE KEY UPDATE merchant_order_id=pay_order.merchant_order_id")
    int ensureClosed(@Param("row") PayOrderDO row);
}
