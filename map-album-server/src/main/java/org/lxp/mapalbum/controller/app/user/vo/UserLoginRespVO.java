package org.lxp.mapalbum.controller.app.user.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录响应 VO：用户信息 + 访问令牌（KSHG 规范 §8.1 RespVO）。
 *
 * <p>前端持 token，后续请求以 {@code Authorization: Bearer <token>} 携带。
 *
 * @author lxp
 */
@Data
public class UserLoginRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 访问令牌 */
    private String token;

    /** 用户信息 */
    private UserRespVO user;

    public UserLoginRespVO() {
    }

    public UserLoginRespVO(String token, UserRespVO user) {
        this.token = token;
        this.user = user;
    }
}
