package cn.qinghe.mall.module.pay.dal.mysql.refund;

import cn.qinghe.mall.module.pay.dal.dataobject.refund.PayRefundDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

/** 原业务退款唯一查询/保存/结果条件更新；写入前持有原pay_order父锁。
 * @author 芋道源码
 */
@Mapper
public interface PayRefundMapper {
    @Select("SELECT * FROM pay_refund WHERE tenant_id=#{tenant} AND app_id=#{app} AND merchant_refund_id=#{business}")
    PayRefundDO find(@Param("tenant") Long tenant, @Param("app") Long app, @Param("business") String business);

    @Select("SELECT * FROM pay_refund WHERE tenant_id=#{tenant} AND no=#{no}")
    PayRefundDO findNo(@Param("tenant") Long tenant, @Param("no") String no);

    @Select("SELECT COUNT(*) FROM pay_refund WHERE tenant_id=#{tenant} AND active_order_id=#{order}")
    int pending(@Param("tenant") Long tenant, @Param("order") Long order);

    @Select("SELECT COALESCE(SUM(refund_price),0) FROM pay_refund WHERE tenant_id=#{tenant} AND order_id=#{order} AND status='SUCCESS'")
    long successPrice(@Param("tenant") Long tenant, @Param("order") Long order);

    @Select("SELECT COALESCE(SUM(refund_price),0) FROM pay_refund WHERE tenant_id=#{tenant} AND order_id=#{order} AND status<>'SUCCESS'")
    long reservedPrice(@Param("tenant") Long tenant, @Param("order") Long order);

    @Select("SELECT MAX(last_submit_time) FROM pay_refund WHERE tenant_id=#{tenant} AND order_id=#{order}")
    LocalDateTime lastSubmission(@Param("tenant") Long tenant, @Param("order") Long order);

    @Insert("INSERT INTO pay_refund(tenant_id,app_id,channel_id,channel_version,order_id,order_no,channel_order_no,user_id,user_type,"
            + "merchant_order_id,merchant_refund_id,no,status,pay_price,refund_price,reason,auth_version,create_time,update_time) "
            + "VALUES(#{row.tenantId},#{row.appId},#{row.channelId},#{row.channelVersion},#{row.orderId},#{row.orderNo},#{row.channelOrderNo},#{row.userId},#{row.userType},"
            + "#{row.merchantOrderId},#{row.merchantRefundId},#{row.no},'PREPARED',#{row.payPrice},#{row.refundPrice},#{row.reason},0,#{row.createTime},#{row.updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") PayRefundDO row);

    @Update("UPDATE pay_refund SET status='PROCESSING',submitted_time=COALESCE(submitted_time,#{now}),last_submit_time=#{now},"
            + "submit_count=submit_count+1,auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} "
            + "AND auth_version=#{version} AND auth_version<9007199254740991 AND submit_count<submission_limit "
            + "AND (status='PREPARED' OR (#{retry}=b'1' AND status IN('PROCESSING','UNKNOWN','FAILED') AND last_submit_time<=#{now}-INTERVAL 30 SECOND))")
    int submit(@Param("tenant") Long tenant, @Param("id") Long id, @Param("version") Long version,
            @Param("retry") boolean retry, @Param("now") LocalDateTime now);

    @Update("UPDATE pay_refund SET submission_limit=submission_limit+1,auth_version=auth_version+1,update_time=#{now} "
        +"WHERE tenant_id=#{tenant} AND id=#{id} AND auth_version=#{version} AND auth_version<9007199254740991 "
        +"AND status IN('PROCESSING','UNKNOWN','FAILED') AND submit_count=submission_limit AND submission_limit<2147483647")
    int authorizeContinuation(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE pay_refund SET status=#{status},channel_refund_no=COALESCE(#{channelNo},channel_refund_no),success_time=#{success},"
            + "auth_version=auth_version+1,update_time=#{now} WHERE tenant_id=#{tenant} AND id=#{id} AND status IN('PROCESSING','UNKNOWN','FAILED') "
            + "AND auth_version=#{version} AND auth_version<9007199254740991 AND submitted_time IS NOT NULL")
    int record(@Param("tenant") Long tenant, @Param("id") Long id, @Param("version") Long version, @Param("status") String status,
            @Param("channelNo") String channelNo, @Param("success") LocalDateTime success, @Param("now") LocalDateTime now);
}
