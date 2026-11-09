package cn.qinghe.mall.module.pay.dal.mysql.channel;
import cn.qinghe.mall.module.pay.dal.dataobject.channel.PayChannelDO;
import org.apache.ibatis.annotations.*;
/** 原渠道查询职责；新提交锁定当前配置，历史查询可读停用渠道。 */
@Mapper
public interface PayChannelMapper {
    @Select("SELECT * FROM pay_channel WHERE tenant_id=#{tenant} AND app_id=#{app} AND code='alipay_qr' AND status=0 ORDER BY id LIMIT 2")
    java.util.List<PayChannelDO> enabledQr(@Param("tenant") Long tenant,@Param("app") Long app);
    /** 仅通知定位；调用处窄范围executeIgnore，验签之前不写业务。 */
    @Select("SELECT * FROM pay_channel WHERE id=#{id}")
    PayChannelDO locateNotifyChannel(@Param("id") Long id);
    @Select("SELECT * FROM pay_channel WHERE tenant_id=#{tenant} AND id=#{id}")
    PayChannelDO get(@Param("tenant") Long tenant,@Param("id") Long id);
    @Select("SELECT * FROM pay_channel WHERE tenant_id=#{tenant} AND id=#{id} FOR UPDATE")
    PayChannelDO lock(@Param("tenant") Long tenant,@Param("id") Long id);
}
