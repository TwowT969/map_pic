package org.lxp.mapalbum.framework.web.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.dal.redis.UserTokenRedisDAO;
import org.lxp.mapalbum.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.lxp.mapalbum.framework.common.util.SecurityFrameworkUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * 登录鉴权拦截器（KSHG 规范 §14.1：非公开接口必须校验登录态）。
 *
 * <p>策略（测试阶段）：
 * <ul>
 *   <li>带合法令牌的请求：一律写入登录上下文（审计字段填充真实 userId）</li>
 *   <li>写操作（POST/PUT/DELETE）与"我的"资源查询：无合法令牌返回业务 401</li>
 *   <li>公开读接口：令牌无效也放行（地图浏览类），仅忽略上下文</li>
 * </ul>
 * 令牌从 {@code Authorization: Bearer <token>} 解析，Redis 换取 userId。
 * 资源归属校验由 Service 以 {@link SecurityFrameworkUtils#getLoginUserId()} 为准实现。
 *
 * @author lxp
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 强制登录的"我的"资源路径（相对 context-path） */
    private static final List<String> PROTECTED_PATHS = Arrays.asList(
            "/spots/mine",
            "/photos/mine"
    );

    @Resource
    private UserTokenRedisDAO userTokenRedisDAO;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = extractToken(request);
        Long userId = token == null ? null : userTokenRedisDAO.getUserIdByToken(token);

        // 1. 有合法令牌：写入上下文（含公开读接口，保证审计字段真实）
        if (userId != null) {
            SecurityFrameworkUtils.setLoginUserId(userId);
            return true;
        }

        // 2. 无合法令牌：仅写操作与"我的"资源拦截，公开读放行
        if (isLoginRequired(request)) {
            log.info("[preHandle][uri={}, method={}] 未登录或令牌无效，拒绝访问",
                    request.getServletPath(), request.getMethod());
            writeUnauthorized(response);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理 ThreadLocal，避免线程复用导致串号
        SecurityFrameworkUtils.clear();
    }

    /**
     * 判断当前请求是否强制登录：写操作或"我的"资源路径。
     */
    private boolean isLoginRequired(HttpServletRequest request) {
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)) {
            return true;
        }
        return PROTECTED_PATHS.contains(request.getServletPath());
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        // 业务语义 401 放 CommonResult（对齐前端 data.code !== 0 的统一判断）
        response.getWriter().write("{\"code\":" + GlobalErrorCodeConstants.UNAUTHORIZED.getCode()
                + ",\"data\":null,\"msg\":\"" + GlobalErrorCodeConstants.UNAUTHORIZED.getMessage() + "\"}");
    }
}
