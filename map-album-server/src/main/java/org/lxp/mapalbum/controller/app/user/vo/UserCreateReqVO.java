package org.lxp.mapalbum.controller.app.user.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 用户创建请求 VO（KSHG 规范 §5.1 ReqVO）。
 *
 * @author lxp
 */
@Data
public class UserCreateReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "SSO 用户标识不能为空")
    private String ssoUserId;

    @NotBlank(message = "SSO 来源不能为空")
    private String ssoProvider;

    private String nickname;
    private String avatarUrl;
    private String phone;
    private String email;
    private Integer gender;

    public UserCreateReqVO() {
    }
}
