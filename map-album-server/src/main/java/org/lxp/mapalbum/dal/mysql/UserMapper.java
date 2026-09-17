package org.lxp.mapalbum.dal.mysql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.lxp.mapalbum.dal.dataobject.UserDO;
import org.lxp.mapalbum.framework.mybatis.mapper.BaseMapperX;

/**
 * 用户 Mapper（KSHG 规范 §7.1）。
 *
 * @author lxp
 */
@Mapper
public interface UserMapper extends BaseMapperX<UserDO> {

    /**
     * 按 SSO 标识查询用户（ssoUserId + ssoProvider 联合唯一）。
     *
     * @param ssoUserId   SSO 用户标识
     * @param ssoProvider SSO 来源
     * @return 用户 DO；不存在返回 null
     */
    default UserDO selectBySso(String ssoUserId, String ssoProvider) {
        return selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getSsoUserId, ssoUserId)
                .eq(UserDO::getSsoProvider, ssoProvider));
    }
}
