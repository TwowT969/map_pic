package org.lxp.mapalbum.framework.common.exception;

import lombok.Getter;

/**
 * 业务异常（KSHG 规范 §10.2）。
 *
 * <p>统一通过 {@link ServiceExceptionUtil#exception(ErrorCode, Object...)} 抛出，由全局异常处理器兜底。
 *
 * @author lxp
 */
@Getter
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
