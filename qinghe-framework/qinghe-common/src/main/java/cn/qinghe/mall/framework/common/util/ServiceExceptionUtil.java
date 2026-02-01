package cn.qinghe.mall.framework.common.util;

import cn.qinghe.mall.framework.common.exception.ErrorCode;
import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.exception.enums.GlobalErrorCodeConstants;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.common.util
 * @Project：qinghe-mall
 * @name：ServiceExceptionUtil
 * @Date：2026/10/20 12:03
 * @Filename：ServiceExceptionUtil
 */
@Slf4j
public class ServiceExceptionUtil {


    public static ServiceException exception(ErrorCode errorCode){
        return exception0(errorCode.getCode(),errorCode.getMsg());
    }

    public static ServiceException exception(ErrorCode errorCode,Object... params){
        return exception0(errorCode.getCode(),errorCode.getMsg(),params);
    }

    public static ServiceException invalidParamsException(String messagePattern,Object... params){
        return exception0(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), messagePattern,params);
    }

    public static ServiceException exception0(Integer code,String messagePattern,Object... params){
        String message=doFormat(code,messagePattern,params);
        return new ServiceException(code,message);
    }

    public static String doFormat(int code,String messagePattern,Object... params){
        StringBuilder sb=new StringBuilder(messagePattern.length()+50);
        int i=0;
        int temp=0;
        for(int j=0;j< params.length;j++){
            temp=messagePattern.indexOf("{}",i);
            if(temp==-1){
                log.error("[doFormat]参数过多！，code：{}参数：{} 错误内容：{}",code,params,messagePattern);
                return messagePattern;
            }
            sb.append(messagePattern,i,temp);
            sb.append(params[j]);
            i=temp+2;
        }

        if(messagePattern.indexOf("{}",i)!=-1){
            log.error("[doFormat]参数过多:错误码{} 错误内容{} 参数{}",code,messagePattern,params);
        }
        sb.append(messagePattern.substring(i));
        return sb.toString();
    }



}
