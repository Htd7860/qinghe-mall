package cn.qinghe.mall.module.infra.enums;

import java.util.Arrays;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 本项目文件用途边界；公开性由用途确定，不接受客户端任意设置公开标志。 */
public enum FilePurposeEnum {
    PRODUCT_IMAGE("product-image", true),
    STORE_LOGO("store-logo", true),
    AFTERSALE_EVIDENCE("aftersale-evidence", false);

    private final String value;
    private final boolean publicRead;

    FilePurposeEnum(String value, boolean publicRead) {
        this.value = value;
        this.publicRead = publicRead;
    }

    public String getValue() { return value; }
    public boolean isPublicRead() { return publicRead; }

    public static FilePurposeEnum fromValue(String value) {
        return Arrays.stream(values()).filter(item -> item.value.equals(value))
                .findFirst().orElseThrow(() -> exception0(400, "文件用途不正确"));
    }
}
