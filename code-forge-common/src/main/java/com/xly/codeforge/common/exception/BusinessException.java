package com.xly.codeforge.common.exception;

import com.xly.codeforge.common.common.ErrorCode;
import lombok.Getter;

/**
 * 自定义异常类
 *
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final ErrorCode code;


    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode;
    }

}
