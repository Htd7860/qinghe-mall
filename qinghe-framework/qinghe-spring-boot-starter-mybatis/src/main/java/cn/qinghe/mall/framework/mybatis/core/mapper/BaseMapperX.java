package cn.qinghe.mall.framework.mybatis.core.mapper;

import cn.hutool.core.util.PageUtil;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.mybatis.core.mapper
 * @Project：qinghe-mall
 * @name：BaseMapperX
 * @Date：2026/10/21 15:20
 * @Filename：BaseMapperX
 */
public interface BaseMapperX<T> extends BaseMapper<T> {
    // 从yudao BaseMapperX择取常用重载，保留当前已有分页构造与行为。
    default T selectOne(SFunction<T, ?> field, Object value) {
        return selectOne(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default Long selectCount(SFunction<T, ?> field, Object value) {
        return selectCount(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default List<T> selectList() {
        return selectList(new QueryWrapper<>());
    }

    default List<T> selectList(SFunction<T, ?> field, Object value) {
        return selectList(new LambdaQueryWrapper<T>().eq(field, value));
    }

    default PageResult<T> selectPage(PageParam param, Wrapper<T> wrapper){
        if(PageParam.PAGE_SIZE_NONE.equals(param.getPageSize())){
            List<T> list=selectList(wrapper);
            return new PageResult<T>((long) list.size(),list );
        }

        Page<T> objectPage = MybatisUtils.buildPage(param);
        selectPage(objectPage,wrapper);
        return new PageResult<>(objectPage.getTotal(),objectPage.getRecords());
    }
}
