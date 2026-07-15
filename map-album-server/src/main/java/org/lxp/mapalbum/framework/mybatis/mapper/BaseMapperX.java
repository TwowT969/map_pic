package org.lxp.mapalbum.framework.mybatis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lxp.mapalbum.framework.common.pojo.PageParam;
import org.lxp.mapalbum.framework.common.pojo.PageResult;
import org.lxp.mapalbum.framework.mybatis.query.LambdaQueryWrapperX;

import java.util.Collection;

/**
 * Mapper 基类（KSHG 规范 §7.1、附录 A.4）。
 *
 * <p>继承 MyBatis-Plus BaseMapper，补充分页/批量等通用方法。业务 Mapper 继承本接口。
 *
 * @author lxp
 */
public interface BaseMapperX<T> extends BaseMapper<T> {

    /**
     * 分页查询（KSHG 规范 §7.2.3：分页需稳定排序，由调用方在 query 中指定 orderBy）。
     *
     * @param pageParam 分页参数
     * @param query     查询条件（含排序）
     * @return 分页结果
     */
    default PageResult<T> selectPage(PageParam pageParam, LambdaQueryWrapperX<T> query) {
        IPage<T> page = new Page<>(pageParam.getPageNo(), pageParam.getPageSize());
        selectPage(page, query);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 批量新增。
     *
     * <p>TODO 升级为 {@code SqlHelper.executeBatch} 实现真正批量 SQL（KSHG 规范 §7.2.6 / §15.4）。
     *
     * @param entities 实体集合
     * @return 受影响行数
     */
    default int insertBatch(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (T entity : entities) {
            count += insert(entity);
        }
        return count;
    }
}
