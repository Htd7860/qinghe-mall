package cn.qinghe.mall.framework.common.util;

import cn.qinghe.mall.framework.common.exception.ErrorCode;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract tests for exception messages and error-code propagation.
 * Expectations follow the agreed yudao behavior, not the current implementation.
 */
@DisplayName("业务异常创建与消息格式化")
class ServiceExceptionUtilTest {

    @Test
    @DisplayName("单个占位符：保留占位符前后的文字")
    void formatsSinglePlaceholderWithSurroundingText() {
        String actual = ServiceExceptionUtil.doFormat(400, "商品{}库存不足", "A");

        assertEquals("商品A库存不足", actual);
    }

    @Test
    @DisplayName("多个占位符：按顺序替换，保留中间文字与结尾")
    void formatsMultiplePlaceholdersInOrder() {
        String actual = ServiceExceptionUtil.doFormat(400, "{}购买{}件商品", "小林", 2);

        assertEquals("小林购买2件商品", actual);
    }

    @Test
    @DisplayName("没有占位符和参数：保持原消息")
    void keepsPlainMessageWhenNoArgumentsAreProvided() {
        String actual = ServiceExceptionUtil.doFormat(400, "库存不足");

        assertEquals("库存不足", actual);
    }


    @Test
    @DisplayName("参数过少：保留未替换的占位符，不丢失剩余文字")
    void keepsUnfilledPlaceholderWhenArgumentsAreMissing() {
        String actual = ServiceExceptionUtil.doFormat(400, "{}购买{}件", "小林");

        assertEquals("小林购买{}件", actual);
    }

    @Test
    @DisplayName("参数值为null：按字符串null填入占位符")
    void formatsNullArgumentAsLiteralNull() {
        // One null value is different from passing a null varargs array.
        String actual = ServiceExceptionUtil.doFormat(400, "商品{}", new Object[]{null});

        assertEquals("商品null", actual);
    }

    @Test
    @DisplayName("无格式化参数的异常工厂：保留错误码和标准异常消息")
    void createsExceptionFromErrorCodeWithoutChangingCodeOrMessage() {
        ErrorCode errorCode = new ErrorCode(400, "请求参数不正确");

        ServiceException actual = ServiceExceptionUtil.exception(errorCode);

        assertAll(
                () -> assertEquals(Integer.valueOf(400), actual.getCode()),
                () -> assertEquals("请求参数不正确", actual.getMessage())
        );
    }

    @Test
    @DisplayName("带参数的异常工厂：格式化消息并保留业务错误码")
    void createsExceptionWithFormattedBusinessMessage() {
        ErrorCode errorCode = new ErrorCode(1_000_001_001, "商品{}库存不足");

        ServiceException actual = ServiceExceptionUtil.exception(errorCode, "A");

        assertAll(
                () -> assertEquals(Integer.valueOf(1_000_001_001), actual.getCode()),
                () -> assertEquals("商品A库存不足", actual.getMessage())
        );
    }

    @Test
    @DisplayName("参数异常工厂：使用400错误码并格式化标准异常消息")
    void createsBadRequestExceptionWithFormattedMessage() {
        ServiceException actual = ServiceExceptionUtil.invalidParamsException("参数{}不正确", "name");

        assertAll(
                () -> assertEquals(Integer.valueOf(400), actual.getCode()),
                () -> assertEquals("参数name不正确", actual.getMessage())
        );
    }
}
