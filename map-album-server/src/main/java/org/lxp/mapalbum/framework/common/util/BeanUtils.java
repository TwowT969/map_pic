package org.lxp.mapalbum.framework.common.util;

import org.lxp.mapalbum.framework.common.pojo.PageResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象转换工具（KSHG 规范 §8.2）。
 *
 * <p>简单场景用框架级 BeanUtils；复杂映射后续可在 {@code convert} 包用 MapStruct。
 *
 * @author lxp
 */
public final class BeanUtils {

    private BeanUtils() {
    }

    public static <T> T toBean(Object source, Class<T> clazz) {
        if (source == null) {
            return null;
        }
        T target = instantiate(clazz);
        org.springframework.beans.BeanUtils.copyProperties(source, target);
        return target;
    }

    public static <S, T> List<T> toBean(List<S> source, Class<T> clazz) {
        if (source == null) {
            return null;
        }
        List<T> result = new ArrayList<>(source.size());
        for (S item : source) {
            result.add(toBean(item, clazz));
        }
        return result;
    }

    public static <T> PageResult<T> toBean(PageResult<?> source, Class<T> clazz) {
        if (source == null) {
            return null;
        }
        return new PageResult<>(toBean(source.getList(), clazz), source.getTotal());
    }

    private static <T> T instantiate(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("实例化对象失败: " + clazz.getName(), e);
        }
    }
}
