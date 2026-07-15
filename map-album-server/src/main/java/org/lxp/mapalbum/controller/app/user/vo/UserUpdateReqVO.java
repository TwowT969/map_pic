package org.lxp.mapalbum.controller.app.user.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 用户更新请求 VO（KSHG 规范 §5.1 ReqVO）。
 *
 * <p>只传需要更新的字段；null 字段不更新。
 *
 * @author lxp
 */
@Data
public class UserUpdateReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    private String nickname;
    private String avatarUrl;
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;

    public UserUpdateReqVO() {
    }
}
