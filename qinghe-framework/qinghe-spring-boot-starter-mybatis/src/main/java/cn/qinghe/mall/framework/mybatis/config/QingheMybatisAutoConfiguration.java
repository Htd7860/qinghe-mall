package cn.qinghe.mall.framework.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.mybatis.config
 * @Project：qinghe-mall
 * @name：QingheMybatisAutoConfiguration
 * @Date：2026/10/21 14:28
 * @Filename：QingheMybatisAutoConfiguration
 */
@AutoConfiguration(before= MybatisPlusAutoConfiguration.class)
@MapperScan(value="${qinghe.info.base-package}",annotationClass = Mapper.class)
public class QingheMybatisAutoConfiguration {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
        MybatisPlusInterceptor interceptor=new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
