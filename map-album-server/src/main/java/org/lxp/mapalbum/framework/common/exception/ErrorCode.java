package org.lxp.mapalbum.framework.common.exception;

import lombok.Getter;

/**
 * 错误码（KSHG 规范 §10.1）。
 *
 * <p>集中定义于 {@code ErrorCodeConstants}，message 支持 {0} 占位符（MessageFormat）。
 *
 * @author lxp
 */
@Getter
public class ErrorCode {

    private final Integer code;
    private final String message;

    public ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
