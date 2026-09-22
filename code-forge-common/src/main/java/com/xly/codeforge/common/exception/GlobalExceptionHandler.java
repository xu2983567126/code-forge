package com.xly.codeforge.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.xly.codeforge.common.common.Result;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.utils.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> businessExceptionHandler(BusinessException e) {
        ErrorCode errorCode = e.getCode();
        String message = e.getMessage();

        if (ErrorCode.NOT_LOGIN_ERROR == errorCode) {
            log.warn("未登录: code={}, message={}", errorCode, message);
        } else {
            log.error("BusinessException: code={}, message={}", errorCode, message, e);
        }
        return ResultUtils.error(e.getCode(), e.getMessage());

    }

    /**
     * Sa-Token 未登录：未带 token / token 无效或过期 / 被顶下线等。
     *
     * <p>必须单独映射。{@link NotLoginException} 是 {@link RuntimeException} 的子类，
     * 若落进下面的兜底分支，未登录会被报成「系统错误 50000」并打整条堆栈 ——
     * 前端因此无法区分「没登录」和「服务故障」，匿名请求还会把日志刷满。</p>
     *
     * <p>Spring 按最具体的异常类型匹配 handler，故不受兜底分支影响。</p>
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<?> notLoginExceptionHandler(NotLoginException e) {
        // 未登录属正常业务态：单行 warn，不打堆栈
        log.warn("未登录（type={}）: {}", e.getType(), e.getMessage());
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
