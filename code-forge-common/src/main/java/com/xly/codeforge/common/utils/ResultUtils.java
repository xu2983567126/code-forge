package com.xly.codeforge.common.utils;

import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.common.Result;

/**
 * 返回工具类
 *
 */
public class ResultUtils {

    /**
     * 成功
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, data, "ok");
    }

    /**
     * 失败
     */
    public static Result<?> error(ErrorCode errorCode) {
        return new Result<>(errorCode);
    }

    /**
     * 失败
     */
    public static Result<?> error(int code, String message) {
        return new Result<>(code, null, message);
    }

    /**
     * 失败
     */
    public static Result<?> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), null, message);
    }
}
