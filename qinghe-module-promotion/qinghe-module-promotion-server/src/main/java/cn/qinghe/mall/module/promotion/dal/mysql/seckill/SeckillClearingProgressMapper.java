package cn.qinghe.mall.module.promotion.dal.mysql.seckill;

import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.SeckillClearingProgressRespVO.Pending;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillPublishRecordDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 只读原代次；阻塞项直接复用实际清算判断的七组谓词，避免显示与裁定分叉。 */
@Mapper
public interface SeckillClearingProgressMapper {
    @Select("SELECT * FROM promotion_seckill_publish_record WHERE tenant_id=#{tenant} AND activity_id=#{activity} AND generation=#{generation} LIMIT 2")
    List<SeckillPublishRecordDO> publication(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);

    @Select("SELECT "+SeckillClearingMapper.PENDING_ADMISSION+" AS admission,"+SeckillClearingMapper.PENDING_REQUESTS+" AS requests,"
            +SeckillClearingMapper.PENDING_OUTBOX+" AS outbox,"+SeckillClearingMapper.PENDING_RESULTS+" AS result_cache,"
            +SeckillClearingMapper.PENDING_REFUNDS+" AS refunds,"+SeckillClearingMapper.PENDING_RETURNS+" AS pool_returns,"
            +SeckillClearingMapper.PENDING_QUOTA+" AS member_held")
    Pending pending(@Param("tenant") Long tenant,@Param("activity") Long activity,@Param("generation") Integer generation);
}
