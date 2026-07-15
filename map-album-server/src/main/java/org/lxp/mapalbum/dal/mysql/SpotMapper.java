package org.lxp.mapalbum.dal.mysql;

import org.apache.ibatis.annotations.Mapper;
import org.lxp.mapalbum.dal.dataobject.SpotDO;
import org.lxp.mapalbum.framework.mybatis.mapper.BaseMapperX;

/**
 * 点位 Mapper（KSHG 规范 §7.1）。
 *
 * @author lxp
 */
@Mapper
public interface SpotMapper extends BaseMapperX<SpotDO> {
}
