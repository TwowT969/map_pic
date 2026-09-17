package org.lxp.mapalbum.service.user;

import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginRespVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRegisterReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRespVO;
import org.lxp.mapalbum.controller.app.user.vo.UserUpdateReqVO;

/**
 * 用户 Service（KSHG 规范 §6）。
 *
 * @author lxp
 */
public interface UserService {

    /**
     * SSO 式登录：查 ssoUserId + ssoProvider，存在则校验状态与密码，不存在则注册；
     * 成功后签发访问令牌（Redis 会话，30 天滑动过期）。
     *
     * @param reqVO 登录请求
     * @return 令牌 + 用户信息
     */
    UserLoginRespVO login(UserLoginReqVO reqVO);

    /**
     * 注册（与登录分离）：账号查重后创建用户并直接签发令牌（注册即登录）。
     *
     * @param reqVO 注册请求
     * @return 令牌 + 用户信息
     */
    UserLoginRespVO register(UserRegisterReqVO reqVO);

    /**
     * 更新当前登录用户资料（昵称等；userId 以登录态为准）。
     *
     * @param reqVO 更新请求
     * @return 更新后的用户信息
     */
    UserRespVO updateProfile(UserUpdateReqVO reqVO);
}
