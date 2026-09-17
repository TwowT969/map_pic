package org.lxp.mapalbum.controller.app.user.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 用户登录请求 VO（KSHG 规范 §5.1 ReqVO）。
 *
 * <p>SSO 式登录：传 ssoUserId + ssoProvider 标识唯一用户，首次登录自动注册。
 * nickname / avatarUrl 仅首次注册时写入，已存在用户不覆盖（由 SSO 同步更新）。
 * 测试阶段 SSO 未接入：网页 dev 通道（ssoUserId 前缀 dev-）免密；
 * App 通道（前缀 app-）必须携带 password：新账号即初始密码（≥6 位），老账号校验匹配。
 *
 * @author lxp
 */
@Data
public class UserLoginReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "SSO 用户标识不能为空")
    private String ssoUserId;

    @NotBlank(message = "SSO 来源不能为空")
    private String ssoProvider;

    /** 昵称（首次注册时写入） */
    private String nickname;

    /** 头像 URL（首次注册时写入） */
    private String avatarUrl;

    /** 登录密码（app 通道必填；首次登录作为初始密码） */
    private String password;

    public UserLoginReqVO() {
    }
}
