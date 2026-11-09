package cn.qinghe.mall.framework.common.enums;

import cn.qinghe.mall.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.common.enums
 * @Project：qinghe-mall
 * @name：CommonStatusEnum
 * @Date：2026/10/24 12:58
 * @Filename：CommonStatusEnum
 */
@Getter
@AllArgsConstructor
public enum CommonStatusEnum implements ArrayValuable<Integer> {
    ENABLE(0,"开启"),
    DISABLE(1,"关闭"),
    ;
    private final Integer status;
    private final String name;
    public static final Integer[] ARRAYS= Arrays.stream(values()).map(CommonStatusEnum::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }


    public static boolean isEnable(Integer status){
        return ENABLE.status.equals(status);
    }

    public static boolean isDisable(Integer status) {
        return DISABLE.status.equals(status);
    }
}
