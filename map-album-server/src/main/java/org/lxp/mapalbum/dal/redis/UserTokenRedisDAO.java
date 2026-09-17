package org.lxp.mapalbum.dal.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 用户登录令牌 Redis 访问（KSHG 规范 §3.2 dal/redis 分层）。
 *
 * <p>token -> userId 映射，TTL 即会话有效期，续期采用"每次校验刷新"的滑动过期。
 * 单实例内存 Token 也可满足测试阶段，但重启即失效且无法水平扩展，故直接落 Redis。
 *
 * @author lxp
 */
@Slf4j
@Repository
public class UserTokenRedisDAO {

    private static final String KEY_PREFIX = "map_album:user:token:";

    /** 默认会话有效期：30 天 */
    private static final Duration DEFAULT_EXPIRE = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;

    public UserTokenRedisDAO(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 创建令牌。
     *
     * @param token  令牌（UUID）
     * @param userId 登录用户 ID
     */
    public void create(String token, Long userId) {
        redisTemplate.opsForValue().set(key(token), String.valueOf(userId), DEFAULT_EXPIRE);
    }

    /**
     * 校验令牌并滑动续期。
     *
     * @param token 令牌
     * @return 登录用户 ID；令牌无效返回 null（由拦截器转为 401）
     */
    public Long getUserIdByToken(String token) {
        String key = key(token);
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            return null;
        }
        // 滑动过期：每次有效访问重置 TTL
        redisTemplate.expire(key, DEFAULT_EXPIRE);
        return Long.valueOf(userId);
    }

    /**
     * 删除令牌（登出）。
     *
     * @param token 令牌
     */
    public void delete(String token) {
        redisTemplate.delete(key(token));
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
