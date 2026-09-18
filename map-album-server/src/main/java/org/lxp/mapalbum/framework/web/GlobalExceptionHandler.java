package org.lxp.mapalbum.framework.web;

import lombok.extern.slf4j.Slf4j;
import org.lxp.mapalbum.framework.common.exception.ServiceException;
import org.lxp.mapalbum.framework.common.exception.enums.GlobalErrorCodeConstants;
import org.lxp.mapalbum.framework.common.pojo.CommonResult;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（KSHG 规范 §10.3）。
 *
 * <p>业务异常透传业务码；参数校验异常统一 400；兜底异常 500 并记录 ERROR 日志。
 *
 * @author lxp
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(ServiceException.class)
    public CommonResult<Void> serviceExceptionHandler(ServiceException ex) {
        return CommonResult.error(ex.getCode(), ex.getMessage());
    }

    /** @RequestBody 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return CommonResult.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }

    /** 表单参数校验失败 */
    @ExceptionHandler(BindException.class)
    public CommonResult<Void> bindExceptionHandler(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return CommonResult.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }

    /** @RequestParam/@PathVariable 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResult<Void> constraintViolationExceptionHandler(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return CommonResult.error(GlobalErrorCodeConstants.BAD_REQUEST.getCode(), message);
    }

    /** 请求方法不支持（如 DELETE 到集合路径）：返回 405 语义而不是 500 系统异常 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public CommonResult<Void> methodNotSupportedHandler(HttpServletRequest req, HttpRequestMethodNotSupportedException ex) {
        log.warn("[methodNotSupportedHandler][uri={} method={}] {}",
                req.getRequestURI(), req.getMethod(), ex.getMessage());
        return CommonResult.error(GlobalErrorCodeConstants.METHOD_NOT_ALLOWED.getCode(),
                "请求方法不支持: " + req.getMethod());
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public CommonResult<Void> defaultExceptionHandler(HttpServletRequest req, Exception ex) {
        log.error("[defaultExceptionHandler][uri={} method={}] 系统异常",
                req.getRequestURI(), req.getMethod(), ex);
        return CommonResult.error(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
