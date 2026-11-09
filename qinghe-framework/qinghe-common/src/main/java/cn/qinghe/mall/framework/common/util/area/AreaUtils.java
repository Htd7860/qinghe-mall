package cn.qinghe.mall.framework.common.util.area;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import java.util.*;
/** 择取yudao AreaUtils.init/getArea，地区CSV原样保留；仅按需增加祖先与中国省市区判断。
 * @author 芋道源码
 */
public final class AreaUtils {
    private static final Map<Integer,Area> areas;
    static {
        try {
            Map<Integer,Area> loaded=new HashMap<>();
            loaded.put(Area.ID_GLOBAL,new Area(Area.ID_GLOBAL,"全球",0,null,new ArrayList<>()));
            List<CsvRow> rows=CsvUtil.getReader().read(ResourceUtil.getUtf8Reader("qinghe-area.csv")).getRows();
            rows.remove(0);
            for (CsvRow row:rows) {
                Area area=new Area(Integer.valueOf(row.get(0)),row.get(1),Integer.valueOf(row.get(2)),null,new ArrayList<>());
                if (loaded.put(area.getId(),area)!=null) { throw new IllegalStateException("区域编号重复"); }
            }
            for (CsvRow row:rows) {
                Area area=loaded.get(Integer.valueOf(row.get(0))),parent=loaded.get(Integer.valueOf(row.get(3)));
                Assert.isTrue(parent!=null && area!=parent,"区域父链不正确");
                area.setParent(parent);parent.getChildren().add(area);
            }
            areas=Collections.unmodifiableMap(loaded);
        } catch (Exception error) { throw new IllegalStateException("区域字典加载失败",error); }
    }
    private AreaUtils() { }
    public static Area getArea(Integer id) { return areas.get(id); }
    /** 只为区域选项构造只读响应，不直接序列化带父/子循环引用的Area对象。 */
    public static List<Area> getChinaRegions() {
        return areas.values().stream().filter(area -> isChinaRegion(area.getId()))
                .sorted(Comparator.comparing(Area::getId)).collect(java.util.stream.Collectors.toList());
    }
    public static boolean contains(Integer ancestor,Integer child) {
        Area cursor=getArea(child);Set<Integer> visited=new HashSet<>();
        while (cursor!=null && visited.add(cursor.getId())) {
            if (Objects.equals(ancestor,cursor.getId())) { return true; }
            cursor=cursor.getParent();
        }
        return false;
    }
    public static boolean isChinaRegion(Integer id) {
        Area area=getArea(id);
        return area!=null && area.getType()>=2 && area.getType()<=4 && contains(Area.ID_CHINA,id);
    }
}
