package cn.qinghe.mall.module.pay.controller.notify;

import cn.qinghe.mall.module.pay.service.order.PayOrderAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** 择取原PayNotifyController.notifyOrder，拒绝参数重复，不记录原通知正文/签名。
 * 此入口通过渠道RSA2认证，不使用会员Bearer或浏览器tenant头决定支付归属。
 * @author 芋道源码
 */
@RestController
@RequiredArgsConstructor
public class PayNotifyController {
    private static final int MAX_BODY=65536;
    private final PayOrderAttemptService orders;

    @PermitAll
    @PostMapping(value="/public-api/pay/notify/alipay/{channelId}",produces=MediaType.TEXT_PLAIN_VALUE)
    public String notifyOrder(@PathVariable Long channelId,HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try {
            orders.notifyOrder(channelId,parameters(request));
            return "success"; // 本地事务已提交（包括已持久记录的REVIEW），失败不能提前应答。
        } catch(IllegalArgumentException malformed) {
            response.setStatus(400);return "failure";
        } catch(Exception unknown) {
            response.setStatus(503);return "failure";
        }
    }

    private Map<String,String> parameters(HttpServletRequest request) throws IOException {
        MediaType type=MediaType.parseMediaType(request.getContentType()==null?"":request.getContentType());
        String encoding=request.getHeader("Content-Encoding");
        if(!MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(type)
                || type.getCharset()!=null && !StandardCharsets.UTF_8.equals(type.getCharset())
                || request.getQueryString()!=null && !request.getQueryString().isEmpty()
                || encoding!=null && !"identity".equalsIgnoreCase(encoding)
                || request.getContentLengthLong()>MAX_BODY)throw new IllegalArgumentException("通知格式不正确");
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[2048];int count;
        InputStream input=request.getInputStream();
        while((count=input.read(chunk,0,Math.min(chunk.length,MAX_BODY+1-buffer.size())))!=-1) {
            buffer.write(chunk,0,count);
            if(buffer.size()>MAX_BODY)throw new IllegalArgumentException("通知过大");
        }
        String form=new String(buffer.toByteArray(),StandardCharsets.US_ASCII);
        if(form.isEmpty())throw new IllegalArgumentException("通知不能为空");
        Map<String,String> result=new LinkedHashMap<>();String[] parts=form.split("&",-1);
        if(parts.length>100)throw new IllegalArgumentException("通知字段过多");
        for(String part:parts) {
            int equals=part.indexOf('=');if(equals<=0)throw new IllegalArgumentException("通知字段不完整");
            String key=decode(part.substring(0,equals)),value=decode(part.substring(equals+1));
            if(key.length()>128 || value.length()>16384 || result.putIfAbsent(key,value)!=null)
                throw new IllegalArgumentException("通知参数重复或过大");
        }
        return result;
    }
    private String decode(String part) {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int i=0;i<part.length();i++) {
            char ch=part.charAt(i);
            if(ch=='+')bytes.write(' ');
            else if(ch=='%') {
                if(i+2>=part.length())throw new IllegalArgumentException("通知编码不正确");
                int a=Character.digit(part.charAt(++i),16),b=Character.digit(part.charAt(++i),16);
                if(a<0 || b<0)throw new IllegalArgumentException("通知编码不正确");bytes.write(a*16+b);
            }else {
                if(ch>127)throw new IllegalArgumentException("通知应使用百分号编码");bytes.write(ch);
            }
        }
        try {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();}
        catch(CharacterCodingException invalid){throw new IllegalArgumentException("通知UTF8编码不正确");}
    }
}
