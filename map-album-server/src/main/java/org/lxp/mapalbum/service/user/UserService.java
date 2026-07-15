package org.lxp.mapalbum.service.user;

import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRespVO;

/**
 * 用户 Service（KSHG 规范 §6）。
 *
 * @author lxp
 */
public interface UserService {

    /**
     * SSO 式登录：查 ssoUserId + ssoProvider，存在则更新昵称/头像，不存在则注册。
     *
     * @param reqVO 登录请求
     * @return 用户信息
     */
    UserRespVO login(UserLoginReqVO reqVO);
}
