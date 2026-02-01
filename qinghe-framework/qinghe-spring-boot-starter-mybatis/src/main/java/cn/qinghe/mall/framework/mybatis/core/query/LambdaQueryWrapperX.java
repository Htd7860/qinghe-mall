package cn.qinghe.mall.framework.mybatis.core.query;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.mybatis.core.query
 * @Project：qinghe-mall
 * @name：LambdaQueryWrapperX
 * @Date：2026/10/21 16:01
 * @Filename：LambdaQueryWrapperX
 *
 * <p>用户创建类及 likeIfPresent；其余方法由助手按明确授权对照 yudao 补齐。
 * 可选筛选值不存在时不添加条件；租户和权限等必需条件不能依赖这种跳过行为。</p>
 */
public class LambdaQueryWrapperX<T> extends LambdaQueryWrapper<T> {
    private static final long serialVersionUID = 1L;

    public LambdaQueryWrapperX<T> likeIfPresent(SFunction<T, ?> column, String val) {
        if (StringUtils.hasText(val)) {
            super.like(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> likeRightIfPresent(SFunction<T, ?> column, String val) {
        if (StringUtils.hasText(val)) {
            super.likeRight(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> inIfPresent(SFunction<T, ?> column, Collection<?> values) {
        if (ObjectUtil.isAllNotEmpty(values) && !ArrayUtil.isEmpty(values)) {
            super.in(column, values);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> inIfPresent(SFunction<T, ?> column, Object... values) {
        if (ObjectUtil.isAllNotEmpty(values) && !ArrayUtil.isEmpty(values)) {
            super.in(column, values);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> eqIfPresent(SFunction<T, ?> column, Object val) {
        if (ObjectUtil.isNotEmpty(val)) {
            super.eq(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> neIfPresent(SFunction<T, ?> column, Object val) {
        if (ObjectUtil.isNotEmpty(val)) {
            super.ne(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> gtIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            super.gt(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> geIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            super.ge(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> ltIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            super.lt(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> leIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            super.le(column, val);
        }
        return this;
    }

    /** 两端都有值时查闭区间，只有一端时退化为大于等于或小于等于。 */
    public LambdaQueryWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object begin, Object end) {
        if (begin != null && end != null) {
            super.between(column, begin, end);
        } else if (begin != null) {
            super.ge(column, begin);
        } else if (end != null) {
            super.le(column, end);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object[] values) {
        return betweenIfPresent(column, valueAt(values, 0), valueAt(values, 1));
    }

    /**
     * 查询两个闭区间是否重叠：记录开始值 <= 查询结束值，且记录结束值 >= 查询开始值。
     * 不能用单个字段的 BETWEEN 替代，否则会漏掉完整覆盖查询区间的记录。
     */
    public LambdaQueryWrapperX<T> betweenIfPresent(
            SFunction<T, ?> beginColumn, SFunction<T, ?> endColumn, LocalDateTime[] times) {
        return leIfPresent(beginColumn, valueAt(times, 1))
                .geIfPresent(endColumn, valueAt(times, 0));
    }

    // 与参考一致，为常用原生方法保留扩展类型，便于继续调用 xxxIfPresent。

    @Override
    public LambdaQueryWrapperX<T> eq(boolean condition, SFunction<T, ?> column, Object val) {
        super.eq(condition, column, val);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> eq(SFunction<T, ?> column, Object val) {
        super.eq(column, val);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> orderByDesc(SFunction<T, ?> column) {
        super.orderByDesc(true, column);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> last(String lastSql) {
        super.last(lastSql);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> in(SFunction<T, ?> column, Collection<?> values) {
        super.in(column, values);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> exists(boolean condition, String existsSql, Object... values) {
        super.exists(condition, existsSql, values);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> notExists(boolean condition, String existsSql, Object... values) {
        super.notExists(condition, existsSql, values);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> and(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
        super.and(condition, consumer);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> or(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
        super.or(condition, consumer);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> nested(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
        super.nested(condition, consumer);
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> not(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
        super.not(condition, consumer);
        return this;
    }

    /** 本类只需安全读取区间端点，避免依赖尚未建立的项目 ArrayUtils。 */
    private static <V> V valueAt(V[] values, int index) {
        return values == null || index < 0 || index >= values.length ? null : values[index];
    }
}
