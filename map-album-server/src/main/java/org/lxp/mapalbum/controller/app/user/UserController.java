package org.lxp.mapalbum.controller.app.user;

import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginRespVO;
import org.lxp.mapalbum.framework.common.pojo.CommonResult;
import org.lxp.mapalbum.service.user.UserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static org.lxp.mapalbum.framework.common.pojo.CommonResult.success;

/**
 * App - 用户 Controller（KSHG 规范 §5）。
 *
 * @author lxp
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * SSO 式登录：首次登录自动注册，已存在则校验状态；
     * 成功返回访问令牌 + 用户信息，后续请求携带 Authorization: Bearer <token>。
     *
     * @param reqVO 登录请求
     * @return 令牌 + 用户信息
     */
    @PostMapping("/login")
    public CommonResult<UserLoginRespVO> login(@Validated @RequestBody UserLoginReqVO reqVO) {
        return success(userService.login(reqVO));
    }
}
