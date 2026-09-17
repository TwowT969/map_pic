package org.lxp.mapalbum.service.user;

import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginRespVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRespVO;

/**
 * 用户 Service（KSHG 规范 §6）。
 *
 * @author lxp
 */
public interface UserService {

    /**
     * SSO 式登录：查 ssoUserId + ssoProvider，存在则校验状态，不存在则注册；
     * 成功后签发访问令牌（Redis 会话，30 天滑动过期）。
     *
     * @param reqVO 登录请求
     * @return 令牌 + 用户信息
     */
    UserLoginRespVO login(UserLoginReqVO reqVO);
}
