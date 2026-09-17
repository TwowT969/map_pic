package org.lxp.mapalbum.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.lxp.mapalbum.framework.mybatis.dataobject.BaseDO;

/**
 * 用户 DO（KSHG 规范 §7.3）。
 *
 * <p>继承 {@link BaseDO} 统一审计字段；主键自增供内部 FK 关联。
 * SSO 标识（ssoUserId + ssoProvider）是外部唯一凭证，由 Service 层负责 UPSERT。
 *
 * @author lxp
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class UserDO extends BaseDO {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** SSO 用户唯一标识 */
    private String ssoUserId;

    /** SSO 来源（cas / oauth2 / wechat / miniapp） */
    private String ssoProvider;

    /** 登录密码（SHA-256 摘要；app 通道必填，dev 测试通道为空免密） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 手机号（可选） */
    private String phone;

    /** 邮箱（可选） */
    private String email;

    /** 性别：0-未知 1-男 2-女 */
    private Integer gender;

    /** 状态：0-禁用 1-正常 */
    private Integer status;
}
