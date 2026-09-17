package org.lxp.mapalbum.service.user;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginRespVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRespVO;
import org.lxp.mapalbum.controller.app.user.vo.UserUpdateReqVO;
import org.lxp.mapalbum.dal.dataobject.UserDO;
import org.lxp.mapalbum.dal.mysql.UserMapper;
import org.lxp.mapalbum.dal.redis.UserTokenRedisDAO;
import org.lxp.mapalbum.enums.CommonStatusEnum;
import org.lxp.mapalbum.framework.common.util.BeanUtils;
import org.lxp.mapalbum.framework.common.util.SecurityFrameworkUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.lxp.mapalbum.enums.ErrorCodeConstants.USER_DISABLED;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.USER_NOT_EXISTS;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.USER_PASSWORD_REQUIRED;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.USER_PASSWORD_TOO_SHORT;
import static org.lxp.mapalbum.enums.ErrorCodeConstants.USER_PASSWORD_WRONG;
import static org.lxp.mapalbum.framework.common.exception.ServiceExceptionUtil.exception;

/**
 * 用户 Service 实现（KSHG 规范 §6）。
 *
 * <p>SSO 式登录：查 ssoUserId + ssoProvider → 有则校验状态与密码，无则注册；
 * 登录成功后签发令牌（Redis 30 天滑动过期）。禁用用户拒绝登录。
 *
 * <p>密码策略：app 通道（ssoUserId 前缀 app-）必须密码，SHA-256 摘要存储（盐 = ssoUserId）；
 * dev 测试通道（前缀 dev-）免密；老 app 账号密码为空时，首次登录所填密码即初始密码。
 *
 * @author lxp
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private static final String APP_CHANNEL_PREFIX = "app-";

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
            validatePassword(user, reqVO);
            log.info("[login][ssoProvider={}, ssoUserId={}] 已有用户 id={}",
                    reqVO.getSsoProvider(), reqVO.getSsoUserId(), user.getId());
        }

        // 2. 签发令牌（UUID 随机值，无状态；有效性由 Redis 会话管理）
        String token = UUID.randomUUID().toString().replace("-", "");
        userTokenRedisDAO.create(token, user.getId());
        return new UserLoginRespVO(token, BeanUtils.toBean(user, UserRespVO.class));
    }

    @Override
    public UserRespVO updateProfile(UserUpdateReqVO reqVO) {
        // userId 以登录上下文为准，不信任前端传参（KSHG 规范 §14.1）
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (reqVO.getNickname() != null) {
            String nick = reqVO.getNickname().trim();
            if (!nick.isEmpty()) {
                user.setNickname(nick);
            }
        }
        if (reqVO.getAvatarUrl() != null) {
            user.setAvatarUrl(reqVO.getAvatarUrl());
        }
        userMapper.updateById(user);
        return BeanUtils.toBean(user, UserRespVO.class);
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
     * app 通道密码校验：密码为空的老账号首次登录即设置密码；已设置则必须匹配。
     */
    private void validatePassword(UserDO user, UserLoginReqVO reqVO) {
        if (!isAppChannel(user.getSsoUserId())) {
            return; // dev 测试通道免密
        }
        String raw = reqVO.getPassword() == null ? "" : reqVO.getPassword().trim();
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            // 老账号补设密码
            if (raw.isEmpty()) {
                throw exception(USER_PASSWORD_REQUIRED);
            }
            checkPasswordLength(raw);
            UserDO update = new UserDO();
            update.setId(user.getId());
            update.setPassword(hash(raw, user.getSsoUserId()));
            userMapper.updateById(update);
            user.setPassword(update.getPassword());
            return;
        }
        if (raw.isEmpty() || !hash(raw, user.getSsoUserId()).equals(user.getPassword())) {
            throw exception(USER_PASSWORD_WRONG);
        }
    }

    /**
     * 首次登录注册：仅写 SSO 标识 + 初始资料，昵称头像由 SSO 侧同步。
     */
    private UserDO doRegister(UserLoginReqVO reqVO) {
        if (isAppChannel(reqVO.getSsoUserId())) {
            String raw = reqVO.getPassword() == null ? "" : reqVO.getPassword().trim();
            if (raw.isEmpty()) {
                throw exception(USER_PASSWORD_REQUIRED);
            }
            checkPasswordLength(raw);
        }
        UserDO newUser = new UserDO();
        newUser.setSsoUserId(reqVO.getSsoUserId());
        newUser.setSsoProvider(reqVO.getSsoProvider());
        newUser.setNickname(reqVO.getNickname());
        newUser.setAvatarUrl(reqVO.getAvatarUrl());
        if (isAppChannel(reqVO.getSsoUserId())) {
            newUser.setPassword(hash(reqVO.getPassword().trim(), reqVO.getSsoUserId()));
        }
        newUser.setGender(0);
        newUser.setStatus(CommonStatusEnum.ENABLED.getCode());
        userMapper.insert(newUser);
        return newUser;
    }

    private boolean isAppChannel(String ssoUserId) {
        return ssoUserId != null && ssoUserId.startsWith(APP_CHANNEL_PREFIX);
    }

    private void checkPasswordLength(String raw) {
        if (raw.length() < 6) {
            throw exception(USER_PASSWORD_TOO_SHORT);
        }
    }

    private String hash(String raw, String salt) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((raw + ":" + salt).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
