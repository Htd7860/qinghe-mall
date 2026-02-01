package cn.qinghe.mall.framework.mybatis.core.util;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.mybatis.core.util
 * @Project：qinghe-mall
 * @name：MybatisUtils
 * @Date：2026/10/21 15:13
 * @Filename：MybatisUtils
 */

public class MybatisUtils {
    public static <T> Page<T> buildPage(PageParam param){
        Page<T> page=new Page<>(param.getPageNo(), param.getPageSize());
        page.setOptimizeJoinOfCountSql(false);
        return page;
    }

    public static void addInterceptor(MybatisPlusInterceptor interceptor, InnerInterceptor inner,
    int index){
        List<InnerInterceptor> inners = new ArrayList<>(interceptor.getInterceptors());
        inners.add(index,inner);
        interceptor.setInterceptors(inners);
    }
}
