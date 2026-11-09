package cn.qinghe.mall.module.infra.api.file.dto;

import lombok.Data;

/** 返回经核验的文件id与规范URL，不返回存储配置。 */
@Data
public class FileBusinessBindRespDTO {
    private Long id;
    private String url;
}
