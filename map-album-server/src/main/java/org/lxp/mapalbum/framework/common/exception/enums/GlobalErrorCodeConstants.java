package org.lxp.mapalbum.framework.common.exception.enums;

import org.lxp.mapalbum.framework.common.exception.ErrorCode;

/**
 * 系统级通用错误码（KSHG 规范 §10.1）。
 *
 * <p>业务模块错误码请下沉到 {@code org.lxp.mapalbum.enums} 包。
 *
 * @author lxp
 */
public interface GlobalErrorCodeConstants {

    ErrorCode SUCCESS = new ErrorCode(0, "成功");
    ErrorCode BAD_REQUEST = new ErrorCode(400, "请求参数不正确");
    ErrorCode UNAUTHORIZED = new ErrorCode(401, "账号未登录");
    ErrorCode FORBIDDEN = new ErrorCode(403, "没有该操作权限");
    ErrorCode NOT_FOUND = new ErrorCode(404, "请求资源不存在");
    ErrorCode INTERNAL_SERVER_ERROR = new ErrorCode(500, "系统异常");
    ErrorCode NOT_IMPLEMENTED = new ErrorCode(501, "功能尚未实现");
}
