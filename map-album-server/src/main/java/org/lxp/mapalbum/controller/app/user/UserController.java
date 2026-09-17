package org.lxp.mapalbum.controller.app.user;

import org.lxp.mapalbum.controller.app.user.vo.UserLoginReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserLoginRespVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRegisterReqVO;
import org.lxp.mapalbum.controller.app.user.vo.UserRespVO;
import org.lxp.mapalbum.controller.app.user.vo.UserUpdateReqVO;
import org.lxp.mapalbum.framework.common.pojo.CommonResult;
import org.lxp.mapalbum.service.user.UserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
     * SSO 式登录：首次登录自动注册，已存在则校验状态与密码；
     * 成功返回访问令牌 + 用户信息，后续请求携带 Authorization: Bearer <token>。
     *
     * @param reqVO 登录请求
     * @return 令牌 + 用户信息
     */
    @PostMapping("/login")
    public CommonResult<UserLoginRespVO> login(@Validated @RequestBody UserLoginReqVO reqVO) {
        return success(userService.login(reqVO));
    }

    /**
     * 注册（与登录分离）：账号查重后创建用户并直接签发令牌（注册即登录）。
     * 公开接口（无需登录态），在 WebConfig 中排除拦截。
     *
     * @param reqVO 注册请求
     * @return 令牌 + 用户信息
     */
    @PostMapping("/register")
    public CommonResult<UserLoginRespVO> register(@Validated @RequestBody UserRegisterReqVO reqVO) {
        return success(userService.register(reqVO));
    }

    /**
     * 更新当前登录用户资料（昵称等；userId 以登录态为准，不信任前端传参）。
     *
     * @param reqVO 更新请求
     * @return 更新后的用户信息
     */
    @PutMapping("/profile")
    public CommonResult<UserRespVO> updateProfile(@Validated @RequestBody UserUpdateReqVO reqVO) {
        return success(userService.updateProfile(reqVO));
    }
}
