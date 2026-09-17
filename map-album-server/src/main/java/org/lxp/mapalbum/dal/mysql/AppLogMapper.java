package org.lxp.mapalbum.dal.mysql;

import org.apache.ibatis.annotations.Mapper;
import org.lxp.mapalbum.dal.dataobject.AppLogDO;
import org.lxp.mapalbum.framework.mybatis.mapper.BaseMapperX;

/**
 * App 端日志/埋点 Mapper（KSHG 规范 §7.4）。
 *
 * @author lxp
 */
@Mapper
public interface AppLogMapper extends BaseMapperX<AppLogDO> {
}
