package com.xly.codeforge.common.common;

import java.io.Serializable;

/**
 * 通用返回类
 *
 * @param <T>
 */
public record Result<T>(int code, T data, String message) implements Serializable {

    public Result(int code, T data) {
        this(code, data, "");
    }

    public Result(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
