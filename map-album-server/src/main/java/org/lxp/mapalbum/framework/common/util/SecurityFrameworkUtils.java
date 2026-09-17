package org.lxp.mapalbum.framework.common.util;

/**
 * 登录上下文工具（KSHG 规范 §7.4.1、§14.1）。
 *
 * <p>当前实现：{@code AuthInterceptor} 校验令牌后写入 ThreadLocal，
 * 审计字段填充（{@code DefaultDBFieldHandler}）与资源归属校验从本工具读取。
 * TODO: 后续接入 Spring Security + JWT 时，改从 SecurityContext 读取，接口签名不变。
 *
 * @author lxp
 */
public final class SecurityFrameworkUtils {

    /** 系统默认用户（无登录上下文时使用，如定时任务） */
    public static final Long DEFAULT_USER_ID = 0L;

    private static final ThreadLocal<Long> LOGIN_USER_ID = new ThreadLocal<>();

    private SecurityFrameworkUtils() {
    }

    /**
     * 获取当前登录用户 ID（由拦截器写入）。
     *
     * @return 登录用户 ID；无登录上下文时返回 {@link #DEFAULT_USER_ID}
     */
    public static Long getLoginUserId() {
        Long userId = LOGIN_USER_ID.get();
        return userId == null ? DEFAULT_USER_ID : userId;
    }

    /**
     * 写入当前登录用户 ID（仅供鉴权拦截器调用）。
     *
     * @param userId 登录用户 ID
     */
    public static void setLoginUserId(Long userId) {
        LOGIN_USER_ID.set(userId);
    }

    /**
     * 清理上下文（请求结束时必须调用，防止线程复用串号）。
     */
    public static void clear() {
        LOGIN_USER_ID.remove();
    }
}
