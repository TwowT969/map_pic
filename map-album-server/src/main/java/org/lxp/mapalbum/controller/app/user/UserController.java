package org.lxp.mapalbum.controller.app.user;

import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRespVO;
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
     * SSO 式登录：首次登录自动注册，已存在则返回用户信息。
     *
     * @param reqVO 登录请求
     * @return 用户信息
     */
    @PostMapping("/login")
    public CommonResult<UserRespVO> login(@Validated @RequestBody UserLoginReqVO reqVO) {
        return success(userService.login(reqVO));
    }
}
