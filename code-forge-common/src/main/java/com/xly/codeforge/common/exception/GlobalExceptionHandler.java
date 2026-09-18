package com.xly.codeforge.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.xly.codeforge.common.common.BaseResponse;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        // 「未登录」是匿名访问下的正常业务态，不打整条堆栈（否则日志被刷屏、真故障难定位）
        if (e.getCode() == ErrorCode.NOT_LOGIN_ERROR.getCode()) {
            log.warn("未登录: {}", e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        }
        log.error("BusinessException", e);
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
    public BaseResponse<?> notLoginExceptionHandler(NotLoginException e) {
        // 未登录属正常业务态：单行 warn，不打堆栈
        log.warn("未登录（type={}）: {}", e.getType(), e.getMessage());
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
