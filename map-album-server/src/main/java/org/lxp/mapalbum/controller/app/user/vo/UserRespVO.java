package org.lxp.mapalbum.controller.app.user.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户响应 VO（KSHG 规范 §8.1 RespVO）。
 *
 * @author lxp
 */
@Data
public class UserRespVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String ssoUserId;
    private String ssoProvider;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;
    private LocalDateTime createTime;

    public UserRespVO() {
    }

    public UserRespVO(Long id, String ssoUserId, String ssoProvider, String nickname,
                      String avatarUrl, String phone, String email,
                      Integer gender, Integer status, LocalDateTime createTime) {
        this.id = id;
        this.ssoUserId = ssoUserId;
        this.ssoProvider = ssoProvider;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.phone = phone;
        this.email = email;
        this.gender = gender;
        this.status = status;
        this.createTime = createTime;
    }
}
