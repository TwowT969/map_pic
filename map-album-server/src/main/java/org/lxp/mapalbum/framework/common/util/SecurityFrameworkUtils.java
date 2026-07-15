package org.lxp.mapalbum.framework.common.util;

/**
 * 登录上下文工具（KSHG 规范 §7.4.1、§14.1）。
 *
 * <p>TODO: 接入 Spring Security + JWT 后，从 SecurityContext 读取真实登录用户。
 * 当前为脚手架占位，审计字段填充默认 0L。
 *
 * @author lxp
 */
public final class SecurityFrameworkUtils {

    /** 系统默认用户（无登录上下文时使用，如脚手架阶段/定时任务） */
    public static final Long DEFAULT_USER_ID = 0L;

    private SecurityFrameworkUtils() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 登录用户 ID；无登录上下文时返回 {@link #DEFAULT_USER_ID}
     */
    public static Long getLoginUserId() {
        // TODO 接入 JWT 后从 SecurityContext 读取
        return DEFAULT_USER_ID;
    }
}
