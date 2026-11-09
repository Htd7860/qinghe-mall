package cn.qinghe.mall.framework.common.util.json;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.common.util.json
 * @Project：qinghe-mall
 * @name：JsonUtils
 * @Date：2026/10/23 19:16
 * @Filename：JsonUtils
 */
@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonUtils() {
    }


    @SneakyThrows
    public static String toJsonString(Object object){
        return OBJECT_MAPPER.writeValueAsString(object);
    }


    public static <T> T parseObject(String text, Class<T> clazz){
        if(StrUtil.isEmpty(text)){
           return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, clazz);
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON反序列化失败");
        }
    }
}
