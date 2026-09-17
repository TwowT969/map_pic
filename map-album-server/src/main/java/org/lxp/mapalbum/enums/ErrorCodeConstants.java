package org.lxp.mapalbum.enums;

import org.lxp.mapalbum.framework.common.exception.ErrorCode;

/**
 * 业务错误码常量（KSHG 规范 §10.1）。
 *
 * <p>系统级通用错误码见 {@code GlobalErrorCodeConstants}。
 * 业务模块错误码定义于此，按域分段：用户(1xxx)、点位(2xxx)、照片(3xxx)。
 *
 * @author lxp
 */
public interface ErrorCodeConstants {

    // ==================== 用户 1xxx ====================

    ErrorCode USER_NOT_EXISTS = new ErrorCode(1001, "用户不存在");
    ErrorCode USER_DISABLED = new ErrorCode(1002, "用户已禁用");
    ErrorCode USER_SSO_CONFLICT = new ErrorCode(1003, "SSO 用户标识冲突");
    ErrorCode USER_NOT_LOGIN = new ErrorCode(1004, "账号未登录");

    // ==================== 点位 2xxx ====================

    ErrorCode SPOT_NOT_EXISTS = new ErrorCode(2001, "点位不存在");
    ErrorCode SPOT_NOT_ONLINE = new ErrorCode(2002, "点位未上线，不可操作");
    ErrorCode SPOT_NAME_DUPLICATE = new ErrorCode(2003, "点位名称重复");

    // ==================== 照片 3xxx ====================

    ErrorCode PHOTO_NOT_EXISTS = new ErrorCode(3001, "照片不存在");
    ErrorCode PHOTO_AUDIT_PENDING = new ErrorCode(3002, "照片审核中，不可操作");
    ErrorCode PHOTO_AUDIT_REJECTED = new ErrorCode(3003, "照片已被驳回");
    ErrorCode PHOTO_UPLOAD_FAIL = new ErrorCode(3004, "照片上传失败");
    ErrorCode PHOTO_NO_PERMISSION = new ErrorCode(3005, "无权操作该照片");
}
