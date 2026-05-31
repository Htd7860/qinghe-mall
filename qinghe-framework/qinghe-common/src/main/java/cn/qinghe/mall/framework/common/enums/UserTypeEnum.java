package cn.qinghe.mall.framework.common.enums;

import cn.hutool.core.util.ArrayUtil;
import cn.qinghe.mall.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.common.enums
 * @Project：qinghe-mall
 * @name：UserTypeEnum
 * @Date：2026/10/22 17:44
 * @Filename：UserTypeEnum
 */
@AllArgsConstructor
@Getter
public enum UserTypeEnum implements ArrayValuable<Integer> {
    MEMBER(1,"会员"),
    ADMIN(2,"管理员");

    private final Integer value;
    private final String name;

    public static final Integer[] ARRAYS= Arrays.stream(values()).map(UserTypeEnum::getValue).toArray(Integer[]::new);

    public static UserTypeEnum valueOf(Integer value){
        return ArrayUtil.firstMatch(userType->userType.getValue().equals(value),values());
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
