package cn.qinghe.mall.framework.excel.core.util;

import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.converters.longconverter.LongStringConverter;
import cn.qinghe.mall.framework.excel.core.handler.ColumnWidthMatchStyleStrategy;
import java.io.OutputStream;
import java.util.List;

/** 直接择取yudao ExcelUtils.write：Excel写出、列宽与Long精度转换。
 * 调用者提供有界输出流，HTTP头及权限/配额由业务入口负责；未引入导入/字典/下拉框链。
 * @author 芋道源码
 */
public final class ExcelUtils {
    private ExcelUtils() { }
    public static <T> void write(OutputStream stream, String sheetName, Class<T> head, List<T> data) {
        FastExcelFactory.write(stream, head)
                .autoCloseStream(false)
                .registerWriteHandler(new ColumnWidthMatchStyleStrategy())
                .registerConverter(new LongStringConverter())
                .sheet(sheetName).doWrite(data);
    }
}
