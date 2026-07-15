package org.lxp.mapalbum.dal.mysql;

import org.apache.ibatis.annotations.Mapper;
import org.lxp.mapalbum.dal.dataobject.PhotoDO;
import org.lxp.mapalbum.framework.mybatis.mapper.BaseMapperX;

/**
 * 照片 Mapper（KSHG 规范 §7.1）。
 *
 * @author lxp
 */
@Mapper
public interface PhotoMapper extends BaseMapperX<PhotoDO> {
}
