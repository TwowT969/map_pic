package org.lxp.mapalbum.framework.mybatis.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Lambda 查询包装器增强（KSHG 规范 §7.1、附录 A.4）。
 *
 * <p>提供 {@code *IfPresent} 系列方法：参数为空时跳过条件，便于动态拼接；
 * 返回值保持 {@link LambdaQueryWrapperX} 类型以支持链式调用。
 *
 * @author lxp
 */
public class LambdaQueryWrapperX<T> extends LambdaQueryWrapper<T> {

    private static final long serialVersionUID = 1L;

    public LambdaQueryWrapperX<T> likeIfPresent(SFunction<T, ?> column, String val) {
        if (val != null && !val.isEmpty()) {
            return (LambdaQueryWrapperX<T>) super.like(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> eqIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.eq(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> neIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.ne(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> gtIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.gt(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> geIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.ge(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> ltIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.lt(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> leIfPresent(SFunction<T, ?> column, Object val) {
        if (val != null) {
            return (LambdaQueryWrapperX<T>) super.le(column, val);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> betweenIfPresent(SFunction<T, ?> column, LocalDateTime[] val) {
        if (val != null && val.length == 2 && val[0] != null && val[1] != null) {
            return (LambdaQueryWrapperX<T>) super.between(column, val[0], val[1]);
        }
        return this;
    }

    public LambdaQueryWrapperX<T> inIfPresent(SFunction<T, ?> column, Collection<?> coll) {
        if (coll != null && !coll.isEmpty()) {
            return (LambdaQueryWrapperX<T>) super.in(column, coll);
        }
        return this;
    }

    @Override
    public LambdaQueryWrapperX<T> orderByDesc(SFunction<T, ?> column) {
        return (LambdaQueryWrapperX<T>) super.orderByDesc(column);
    }

    @Override
    public LambdaQueryWrapperX<T> orderByAsc(SFunction<T, ?> column) {
        return (LambdaQueryWrapperX<T>) super.orderByAsc(column);
    }
}
