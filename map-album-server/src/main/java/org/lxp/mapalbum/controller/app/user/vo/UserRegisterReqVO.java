package org.lxp.mapalbum.controller.app.user.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 用户注册请求 VO（注册与登录分离）。
 *
 * <p>注册成功后直接签发令牌（注册即登录）。账号规则与登录一致：
 * 2-32 位字母 / 数字 / 下划线（服务端统一映射为 app- 前缀 SSO 标识）。
 *
 * @author lxp
 */
@Data
public class UserRegisterReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[\\w@.-]{2,32}$", message = "账号需为 2-32 位字母 / 数字 / 下划线")
    private String account;

    /** 昵称（选填，默认同账号） */
    @Size(max = 20, message = "昵称最长 20 位")
    private String nickname;

    /** 初始密码（≥6 位） */
    @NotBlank(message = "请输入密码")
    private String password;

    public UserRegisterReqVO() {
    }
}
