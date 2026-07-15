package org.lxp.mapalbum.framework.common.util;

import javax.servlet.http.HttpServletRequest;

/**
 * IP 工具（KSHG 规范 §14 上下文辅助）。
 *
 * @author lxp
 */
public final class IpUtils {

    private static final String UNKNOWN = "unknown";

    private IpUtils() {
    }

    /**
     * 获取客户端真实 IP。
     *
     * <p>依次取 X-Forwarded-For(首个) -> X-Real-IP -> Proxy-Client-IP -> WL-Proxy-Client-IP -> RemoteAddr。
     * 用于低风险的"默认定位中心"场景；生产应仅信任可信代理设置的 XFF，并对接口加限流。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能含多个：client, proxy1, proxy2，取首个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "" : ip.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
