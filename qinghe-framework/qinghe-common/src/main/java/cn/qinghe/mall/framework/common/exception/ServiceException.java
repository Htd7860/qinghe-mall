package cn.qinghe.mall.framework.common.exception;

import lombok.Data;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.common.exception
 * @Project：qinghe-mall
 * @name：ServiceException
 * @Date：2026/10/20 11:39
 * @Filename：ServiceException
 */
public class ServiceException extends RuntimeException{

    private Integer code;

    private String msg;

    public ServiceException(){}


    public ServiceException(ErrorCode errorCode){
        this.code=errorCode.getCode();
        this.msg= errorCode.getMsg();
    }

    public ServiceException(Integer code,String msg){
        this.code=code;
        this.msg=msg;
    }


    public Integer getCode(){
        return code;
    }

    public ServiceException setCode(Integer code){
        this.code=code;
        return this;
    }

    public String getMsg(){
        return msg;
    }

    public ServiceException setMsg(String msg){
        this.msg=msg;
        return this;
    }

    @Override
    public String getMessage() {
        return msg;
    }
}
