package cn.qinghe.mall.module.promotion.service.seckill.dto;

import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillActivityDO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillActivitySkuDO;
import lombok.Data;
import java.util.List;

/** 本服务不可变发布快照：保留当代名称/时间/价格/限额/规格，非浏览器请求模型。 */
@Data
public class SeckillConfigurationSnapshotDTO {
    private SeckillActivityDO activity;
    private List<SeckillActivitySkuDO> skus;
}
