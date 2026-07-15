package org.lxp.mapalbum.framework.common.exception;

import java.text.MessageFormat;

/**
 * 业务异常工具（KSHG 规范 §10.2）。
 *
 * <p>用法：{@code throw exception(USER_NOT_EXISTS);}
 *
 * @author lxp
 */
public final class ServiceExceptionUtil {

    private ServiceExceptionUtil() {
    }

    public static ServiceException exception(ErrorCode errorCode, Object... params) {
        String message = formatMessage(errorCode.getMessage(), params);
        return new ServiceException(errorCode.getCode(), message);
    }

    private static String formatMessage(String message, Object... params) {
        if (message == null) {
            return "";
        }
        if (params == null || params.length == 0) {
            return message;
        }
        // 兼容 {0} 占位符
        return MessageFormat.format(message, params);
    }
}
