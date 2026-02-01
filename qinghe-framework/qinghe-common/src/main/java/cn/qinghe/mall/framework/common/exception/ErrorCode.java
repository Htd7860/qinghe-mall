package cn.qinghe.mall.framework.common.exception;

import lombok.Data;

/**
 * @Author：qw
 * @Package：exception
 * @Project：qinghe-mall
 * @name：ErrorCode
 * @Date：2026/10/20 11:26
 * @Filename：ErrorCode
 */

@Data
public class ErrorCode {
    private final Integer code;

    private final String msg;

    public ErrorCode(Integer code,String msg){
        this.code=code;
        this.msg=msg;
    }
}
