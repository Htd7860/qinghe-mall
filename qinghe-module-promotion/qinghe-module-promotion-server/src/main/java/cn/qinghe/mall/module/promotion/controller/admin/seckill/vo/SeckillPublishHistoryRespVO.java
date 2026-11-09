package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishHistoryDTO;
import lombok.Data;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 管理端发布历史；数据库 UTC 时间显式带偏移，避免浏览器按本机时区解释。 */
@Data
public class SeckillPublishHistoryRespVO {
    private String requestId;
    private Long actorId;
    private Long fromVersion;
    private Long preparedVersion;
    private Integer generation;
    private OffsetDateTime preparedAt;
    private Boolean published;
    private Boolean withdrawn;
    private Boolean settled;

    public static SeckillPublishHistoryRespVO from(SeckillPublishHistoryDTO source) {
        SeckillPublishHistoryRespVO result=new SeckillPublishHistoryRespVO();
        result.setRequestId(source.getRequestId());result.setActorId(source.getActorId());
        result.setFromVersion(source.getFromVersion());result.setPreparedVersion(source.getPreparedVersion());
        result.setGeneration(source.getGeneration());result.setPreparedAt(source.getPreparedAt().atOffset(ZoneOffset.UTC));
        result.setPublished(source.getPublished());result.setWithdrawn(source.getWithdrawn());result.setSettled(source.getSettled());
        return result;
    }
}
