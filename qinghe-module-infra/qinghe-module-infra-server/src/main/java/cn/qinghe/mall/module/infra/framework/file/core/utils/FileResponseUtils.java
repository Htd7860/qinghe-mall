package cn.qinghe.mall.module.infra.framework.file.core.utils;

import cn.qinghe.mall.module.infra.service.file.bo.FileContentRespBO;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;

/** 沿用yudao附件输出职责，改用Spring标准ContentDisposition处理文件名与响应头。 */
public final class FileResponseUtils {
    private FileResponseUtils() { }

    public static ResponseEntity<byte[]> attachment(FileContentRespBO file) {
        return response(file, ContentDisposition.attachment().filename(file.getName(), StandardCharsets.UTF_8).build());
    }

    public static ResponseEntity<byte[]> image(FileContentRespBO file) {
        return response(file, ContentDisposition.inline().filename(file.getName(), StandardCharsets.UTF_8).build());
    }

    private static ResponseEntity<byte[]> response(FileContentRespBO file, ContentDisposition disposition) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.getType()))
                .contentLength(file.getContent().length).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(file.getContent());
    }
}
