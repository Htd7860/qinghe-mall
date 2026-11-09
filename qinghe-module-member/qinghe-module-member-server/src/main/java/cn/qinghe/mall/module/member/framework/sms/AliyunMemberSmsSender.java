package cn.qinghe.mall.module.member.framework.sms;

import cn.hutool.core.date.format.FastDateFormat;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 直接提取yudao AliyunSmsClient.sendSms/request/percentCode的阿里ACS3签名与发送链。
 * 只允许固定两个验证码模板，剔除记录渠道密钥的init/refresh日志；不把API受理称已送达。
 * @author zzf
 */
public final class AliyunMemberSmsSender implements MemberSmsSender {
    private static final String URL="https://dysmsapi.aliyuncs.com";
    private static final String HOST="dysmsapi.aliyuncs.com";
    private static final String VERSION="2017-05-25";
    private final String apiKey;
    private final String apiSecret;
    private final String signature;
    private final String registerTemplate;
    private final String resetTemplate;
    public AliyunMemberSmsSender(MemberSmsProperties properties) {
        apiKey=properties.getApiKey();apiSecret=properties.getApiSecret();signature=properties.getSignature();
        registerTemplate=properties.getRegisterTemplate();resetTemplate=properties.getResetPasswordTemplate();
        if (apiKey==null || !apiKey.matches("[A-Za-z0-9]{1,128}") || apiSecret==null || apiSecret.length()<8 || apiSecret.length()>256
                || signature==null || signature.trim().isEmpty() || signature.length()>50 || registerTemplate==null || !registerTemplate.matches("[A-Za-z0-9_]{1,128}")
                || resetTemplate==null || !resetTemplate.matches("[A-Za-z0-9_]{1,128}")) { throw new IllegalArgumentException("阿里短信配置不完整"); }
    }
    @Override
    public MemberSmsSendResult send(Long logId,Long tenantId,String mobile,MemberSmsScene scene,String code) {
        validate(logId,tenantId,mobile,scene,code);
        TreeMap<String,Object> parameters=new TreeMap<>();parameters.put("PhoneNumbers",mobile);parameters.put("SignName",signature);
        parameters.put("TemplateCode",scene==MemberSmsScene.REGISTER?registerTemplate:resetTemplate);
        parameters.put("TemplateParam",JsonUtils.toJsonString(Collections.singletonMap("code",code)));parameters.put("OutId",logId);
        try {
            JSONObject response=request("SendSms",parameters);
            if (!"OK".equals(response.getStr("Code")) || response.getStr("RequestId")==null || response.getStr("BizId")==null) {
                throw exception0(503,"短信渠道未确认受理，请稍后重试");
            }
            if (response.getStr("RequestId").trim().isEmpty() || response.getStr("RequestId").length()>128
                    || response.getStr("BizId").trim().isEmpty() || response.getStr("BizId").length()>128) {
                throw exception0(503,"短信渠道受理信息不完整");
            }
            return new MemberSmsSendResult("ALIYUN",response.getStr("RequestId"),response.getStr("BizId"));
        } catch (RuntimeException error) {
            // 不携带原始异常/URL/响应，避免provider错误把手机号、验证码或Authorization写进技术日志。
            throw exception0(503,"短信渠道调用未确认，请稍后重试");
        }
    }
    public static void validate(Long logId,Long tenantId,String mobile,MemberSmsScene scene,String code) {
        if (logId==null || logId<=0 || tenantId==null || tenantId<=0 || mobile==null || !mobile.matches("^1[3-9][0-9]{9}$")
                || scene==null || code==null || !code.matches("[0-9]{6}")) { throw new IllegalArgumentException("短信验证码发送参数不正确"); }
    }
    private JSONObject request(String apiName, TreeMap<String, Object> queryParams) {
        // 1. 请求参数
        String queryString = queryParams.entrySet().stream()
                .map(entry -> percentCode(entry.getKey()) + "=" + percentCode(String.valueOf(entry.getValue())))
                .collect(Collectors.joining("&"));

        // 2. 请求 Body
        String requestBody = ""; // 短信 API 为 RPC 接口，query parameters 在 uri 中拼接，因此 request body 如果没有特殊要求，设置为空
        String hashedRequestPayload = DigestUtil.sha256Hex(requestBody);

        // 3.1 请求 Header
        TreeMap<String, String> headers = new TreeMap<>();
        headers.put("host", HOST);
        headers.put("x-acs-version", VERSION);
        headers.put("x-acs-action", apiName);
        headers.put("x-acs-date", FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("GMT")).format(new Date()));
        headers.put("x-acs-signature-nonce", IdUtil.randomUUID());
        headers.put("x-acs-content-sha256", hashedRequestPayload);

        // 3.2 构建签名 Header
        StringBuilder canonicalHeaders = new StringBuilder(); // 构造请求头，多个规范化消息头，按照消息头名称（小写）的字符代码顺序以升序排列后拼接在一起
        StringBuilder signedHeadersBuilder = new StringBuilder(); // 已签名消息头列表，多个请求头名称（小写）按首字母升序排列并以英文分号（;）分隔
        headers.entrySet().stream().filter(entry -> entry.getKey().toLowerCase().startsWith("x-acs-")
                        || "host".equalsIgnoreCase(entry.getKey())
                        || "content-type".equalsIgnoreCase(entry.getKey()))
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    String lowerKey = entry.getKey().toLowerCase();
                    canonicalHeaders.append(lowerKey).append(":").append(String.valueOf(entry.getValue()).trim()).append("\n");
                    signedHeadersBuilder.append(lowerKey).append(";");
                });
        String signedHeaders = signedHeadersBuilder.substring(0, signedHeadersBuilder.length() - 1);

        // 4. 构建 Authorization 签名
        String canonicalRequest = "POST" + "\n" +
                "/" + "\n" +
                queryString + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                hashedRequestPayload;
        String hashedCanonicalRequest = DigestUtil.sha256Hex(canonicalRequest);
        String stringToSign = "ACS3-HMAC-SHA256" + "\n" + hashedCanonicalRequest;
        String signature = SecureUtil.hmacSha256(apiSecret).digestHex(stringToSign); // 计算签名
        headers.put("Authorization", "ACS3-HMAC-SHA256" + " " + "Credential=" + apiKey
                + ", " + "SignedHeaders=" + signedHeaders + ", " + "Signature=" + signature);

        // 5. 发起请求
        String responseBody = post(URL + "?" + queryString, headers, requestBody);
        return JSONUtil.parseObj(responseBody);
    }


    /** 固定HTTPS地址、有限超时/响应体，无重试或重定向；返回原响应只在方法内部解析。 */
    private String post(String url,Map<String,String> headers,String body) {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(1000);connection.setReadTimeout(3000);connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.getBytes(StandardCharsets.UTF_8).length);
            for (Map.Entry<String,String> header:headers.entrySet()) { connection.setRequestProperty(header.getKey(),header.getValue()); }
            try (OutputStream output=connection.getOutputStream()) { output.write(body.getBytes(StandardCharsets.UTF_8)); }
            if (connection.getResponseCode()<200 || connection.getResponseCode()>=300) { throw new IOException("短信渠道响应失败"); }
            try (InputStream input=connection.getInputStream();ByteArrayOutputStream result=new ByteArrayOutputStream()) {
                byte[] block=new byte[4096];int count;
                while ((count=input.read(block))!=-1) {
                    if (result.size()>65536-count) { throw new IOException("短信渠道响应过大"); }result.write(block,0,count);
                }
                return new String(result.toByteArray(),StandardCharsets.UTF_8);
            }
        } catch (IOException error) { throw exception0(503,"短信渠道连接未确认"); }
        finally { if (connection!=null) { connection.disconnect(); } }
    }
    private static String percentCode(String value) {
        try { return URLEncoder.encode(value,"UTF-8").replace("+","%20").replace("*","%2A").replace("%7E","~"); }
        catch (UnsupportedEncodingException impossible) { throw new IllegalStateException("UTF-8不可用"); }
    }
}
