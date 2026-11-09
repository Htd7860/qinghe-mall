package cn.qinghe.mall.framework.excel.core.handler;

import cn.hutool.core.collection.CollUtil;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.Head;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.util.MapUtils;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.style.column.AbstractColumnWidthStyleStrategy;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.Cell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 自适应列宽处理器
 *
 * 相比 {@link LongestMatchColumnWidthStyleStrategy} 来说，额外处理了 DATE 类型！
 *
 * @see <a href="https://github.com/YunaiV/yudao-cloud/pull/196/">添加自适应列宽处理器，并替换默认列宽策略</a>
 * @author hmb
 */
public class ColumnWidthMatchStyleStrategy extends AbstractColumnWidthStyleStrategy {

    private static final int MAX_COLUMN_WIDTH = 255;

    private final Map<Integer, Map<Integer, Integer>> cache = MapUtils.newHashMapWithExpectedSize(8);

    @Override
    protected void setColumnWidth(WriteSheetHolder writeSheetHolder, List<WriteCellData<?>> cellDataList, Cell cell,
                                  Head head, Integer relativeRowIndex, Boolean isHead) {
        boolean needSetWidth = isHead || CollUtil.isNotEmpty(cellDataList);
        if (!needSetWidth) {
            return;
        }
        Map<Integer, Integer> maxColumnWidthMap = cache.computeIfAbsent(writeSheetHolder.getSheetNo(),
                key -> new HashMap<>(16));
        Integer columnWidth = dataLength(cellDataList, cell, isHead);
        if (columnWidth < 0) {
            return;
        }
        if (columnWidth > MAX_COLUMN_WIDTH) {
            columnWidth = MAX_COLUMN_WIDTH;
        }
        Integer maxColumnWidth = maxColumnWidthMap.get(cell.getColumnIndex());
        if (maxColumnWidth == null || columnWidth > maxColumnWidth) {
            maxColumnWidthMap.put(cell.getColumnIndex(), columnWidth);
            writeSheetHolder.getSheet().setColumnWidth(cell.getColumnIndex(), columnWidth * 256);
        }
    }

    @SuppressWarnings("EnhancedSwitchMigration")
    private Integer dataLength(List<WriteCellData<?>> cellDataList, Cell cell, Boolean isHead) {
        if (isHead) {
            return cell.getStringCellValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        WriteCellData<?> cellData = cellDataList.get(0);
        CellDataTypeEnum type = cellData.getType();
        if (type == null) {
            return -1;
        }
        switch (type) {
            case STRING:
                return cellData.getStringValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            case BOOLEAN:
                return cellData.getBooleanValue().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            case NUMBER:
                return cellData.getNumberValue().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            case DATE:
                return cellData.getDateValue().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            default:
                return -1;
        }
    }

}