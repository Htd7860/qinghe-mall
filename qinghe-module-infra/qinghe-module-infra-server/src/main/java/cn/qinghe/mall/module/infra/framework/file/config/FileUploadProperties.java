package cn.qinghe.mall.module.infra.framework.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/** 图片默认 10 MiB，与需求基线 B11 一致；HTTP multipart 上限由入口同步设置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "qinghe.file.upload")
public class FileUploadProperties {
    @Min(1)
    @Max(104857600)
    private long maxBytes = 10L * 1024 * 1024;
}
