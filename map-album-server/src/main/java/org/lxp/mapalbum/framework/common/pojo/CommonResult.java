package org.lxp.mapalbum.framework.common.pojo;

import lombok.Data;
import org.lxp.mapalbum.framework.common.exception.ErrorCode;

import java.io.Serializable;

/**
 * 统一返回结果（KSHG 规范 §5.5）。
 *
 * <p>所有 HTTP 接口统一返回 {@code CommonResult<T>}，成功 code=0（对齐技术方案接口示例）。
 *
 * @author lxp
 */
@Data
public class CommonResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功码 */
    public static final Integer CODE_SUCCESS = 0;

    private Integer code;
    private T data;
    private String msg;

    public CommonResult() {
    }

    public CommonResult(Integer code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(CODE_SUCCESS, data, "");
    }

    public static CommonResult<Void> success() {
        return new CommonResult<>(CODE_SUCCESS, null, "");
    }

    public static <T> CommonResult<T> error(Integer code, String msg) {
        return new CommonResult<>(code, null, msg);
    }

    public static <T> CommonResult<T> error(ErrorCode errorCode) {
        return new CommonResult<>(errorCode.getCode(), null, errorCode.getMessage());
    }
}
