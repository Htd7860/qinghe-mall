package cn.qinghe.mall.module.pay.dal.mysql.order;
import cn.qinghe.mall.module.pay.dal.dataobject.order.PayOrderExtensionDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
/** 原渠道尝试保存职责，所有写由主支付单父行锁串行化。 */
@Mapper
public interface PayOrderExtensionMapper {
    @Select("SELECT * FROM pay_order_extension WHERE tenant_id=#{tenant} AND no=#{no}")
    PayOrderExtensionDO findNo(@Param("tenant") Long tenant,@Param("no") String no);
    @Select("SELECT * FROM pay_order_extension WHERE tenant_id=#{tenant} AND order_id=#{order}")
    PayOrderExtensionDO find(@Param("tenant") Long tenant,@Param("order") Long order);
    @Insert("INSERT INTO pay_order_extension(tenant_id,order_id,channel_id,channel_version,no,state,auth_version,create_time,update_time) "
        + "VALUES(#{row.tenantId},#{row.orderId},#{row.channelId},#{row.channelVersion},#{row.no},'SUBMITTING',0,#{row.createTime},#{row.updateTime})")
    @Options(useGeneratedKeys=true,keyProperty="row.id")
    int insert(@Param("row") PayOrderExtensionDO row);
    @Update("UPDATE pay_order_extension SET qr_recovery_count=qr_recovery_count+1,qr_recovery_after=#{after},auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND auth_version=#{version} AND auth_version<9007199254740991 "
        + "AND state IN('SUBMITTING','WAITING') AND (display_content IS NULL OR display_content='') AND qr_recovery_count<3 "
        + "AND create_time<=#{now}-INTERVAL 30 SECOND AND (qr_recovery_after IS NULL OR qr_recovery_after<=#{now})")
    int claimQrRecovery(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,
        @Param("now") LocalDateTime now,@Param("after") LocalDateTime after);
    @Update("UPDATE pay_order_extension SET state=#{state},display_content=COALESCE(#{display},display_content),"
        + "channel_order_no=COALESCE(#{channelNo},channel_order_no),success_time=COALESCE(#{paid},success_time),auth_version=auth_version+1,update_time=#{now} "
        + "WHERE tenant_id=#{tenant} AND id=#{id} AND auth_version=#{version} AND auth_version<9007199254740991")
    int record(@Param("tenant") Long tenant,@Param("id") Long id,@Param("version") Long version,@Param("state") String state,
        @Param("display") String display,@Param("channelNo") String channelNo,@Param("paid") LocalDateTime paid,@Param("now") LocalDateTime now);
}
