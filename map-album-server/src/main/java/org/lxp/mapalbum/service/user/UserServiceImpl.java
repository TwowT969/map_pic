package org.lxp.mapalbum.service.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRespVO;
import org.lxp.mapalbum.dal.dataobject.UserDO;
import org.lxp.mapalbum.dal.mysql.UserMapper;
import org.lxp.mapalbum.enums.CommonStatusEnum;
import org.lxp.mapalbum.framework.common.exception.ServiceException;
import org.lxp.mapalbum.framework.common.util.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.USER_DISABLED;

/**
 * 用户 Service 实现（KSHG 规范 §6）。
 *
 * <p>SSO 式登录：查 ssoUserId + ssoProvider → 有则返回，无则注册。
 * 首次登录时写入 nickname / avatarUrl；已存在用户不覆盖这些字段（由 SSO 侧同步）。
 *
 * @author lxp
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public UserRespVO login(UserLoginReqVO reqVO) {
        // 1. 查是否已有该 SSO 用户
        LambdaQueryWrapper<UserDO> query = new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getSsoUserId, reqVO.getSsoUserId())
                .eq(UserDO::getSsoProvider, reqVO.getSsoProvider());
        UserDO user = userMapper.selectOne(query);

        if (user != null) {
            // 已有用户——检查状态
            if (CommonStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
                throw new ServiceException(USER_DISABLED.getCode(), USER_DISABLED.getMessage());
            }
            log.info("[login][ssoUserId={}] 已有用户 id={}, nickname={}",
                    reqVO.getSsoUserId(), user.getId(), user.getNickname());
            return BeanUtils.toBean(user, UserRespVO.class);
        }

        // 2. 首次登录——注册新用户
        UserDO newUser = new UserDO();
        newUser.setSsoUserId(reqVO.getSsoUserId());
        newUser.setSsoProvider(reqVO.getSsoProvider());
        newUser.setNickname(reqVO.getNickname());
        newUser.setAvatarUrl(reqVO.getAvatarUrl());
        newUser.setGender(0);
        newUser.setStatus(CommonStatusEnum.ENABLED.getCode());
        userMapper.insert(newUser);

        log.info("[login][ssoUserId={}] 新用户注册 id={}", reqVO.getSsoUserId(), newUser.getId());
        return BeanUtils.toBean(newUser, UserRespVO.class);
    }
}
