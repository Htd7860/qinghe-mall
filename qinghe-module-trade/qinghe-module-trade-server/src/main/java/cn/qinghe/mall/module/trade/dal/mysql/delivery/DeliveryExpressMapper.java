package cn.qinghe.mall.module.trade.dal.mysql.delivery;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 复用原全局承运商目录的启用读取；发货事务锁定配置，历史显示读取订单快照。 */
@Mapper
public interface DeliveryExpressMapper extends BaseMapperX<DeliveryExpressDO> {
    @Select("SELECT * FROM trade_delivery_express WHERE status=0 AND deleted=b'0' ORDER BY sort,id LIMIT 201")
    List<DeliveryExpressDO> enabled();
    @Select("SELECT * FROM trade_delivery_express WHERE id=#{id} AND status=0 AND deleted=b'0' FOR UPDATE")
    DeliveryExpressDO lockEnabled(@Param("id") Long id);
}
