package org.lxp.mapalbum.service.user;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginRespVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRespVO;
import org.lxp.mapalbum.dal.dataobject.UserDO;
import org.lxp.mapalbum.dal.mysql.UserMapper;
import org.lxp.mapalbum.dal.redis.UserTokenRedisDAO;
import org.lxp.mapalbum.enums.CommonStatusEnum;
import org.lxp.mapalbum.framework.common.util.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.USER_DISABLED;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 用户 Service 实现（KSHG 规范 §6）。
 *
 * <p>SSO 式登录：查 ssoUserId + ssoProvider → 有则校验状态，无则注册；
 * 登录成功后签发令牌（Redis 30 天滑动过期）。禁用用户拒绝登录。
 *
 * @author lxp
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserTokenRedisDAO userTokenRedisDAO;

    @Override
    public UserLoginRespVO login(UserLoginReqVO reqVO) {
        // 1. 查是否已有该 SSO 用户；无则首次注册
        UserDO user = userMapper.selectBySso(reqVO.getSsoUserId(), reqVO.getSsoProvider());
        if (user == null) {
            user = doRegister(reqVO);
            log.info("[login][ssoProvider={}, ssoUserId={}] 新用户注册 id={}",
                    reqVO.getSsoProvider(), reqVO.getSsoUserId(), user.getId());
        } else {
            validateStatus(user);
            log.info("[login][ssoProvider={}, ssoUserId={}] 已有用户 id={}",
                    reqVO.getSsoProvider(), reqVO.getSsoUserId(), user.getId());
        }

        // 2. 签发令牌（UUID 随机值，无状态；有效性由 Redis 会话管理）
        String token = UUID.randomUUID().toString().replace("-", "");
        userTokenRedisDAO.create(token, user.getId());
        return new UserLoginRespVO(token, BeanUtils.toBean(user, UserRespVO.class));
    }

    /**
     * 校验用户状态：禁用抛业务异常（不吞、不用 null 表达失败）。
     */
    private void validateStatus(UserDO user) {
        if (CommonStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw exception(USER_DISABLED);
        }
    }

    /**
     * 首次登录注册：仅写 SSO 标识 + 初始资料，昵称头像由 SSO 侧同步。
     */
    private UserDO doRegister(UserLoginReqVO reqVO) {
        UserDO newUser = new UserDO();
        newUser.setSsoUserId(reqVO.getSsoUserId());
        newUser.setSsoProvider(reqVO.getSsoProvider());
        newUser.setNickname(reqVO.getNickname());
        newUser.setAvatarUrl(reqVO.getAvatarUrl());
        newUser.setGender(0);
        newUser.setStatus(CommonStatusEnum.ENABLED.getCode());
        userMapper.insert(newUser);
        return newUser;
    }
}
