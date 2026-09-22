package com.xly.codeforge.common.exception;


import cn.hutool.core.collection.CollUtil;
import com.xly.codeforge.common.common.ErrorCode;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * 抛异常工具类
 *
 */
public final class BusinessAssert {

    /**
     * 条件成立则抛异常
     */

    private static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throw new BusinessException(errorCode);
        }
    }

    private static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        if (condition) {
            throw new BusinessException(errorCode, message);
        }
    }

    public static void isTrue(boolean condition, ErrorCode errorCode) {
        throwIf(!condition, errorCode);
    }

    public static void isTrue(boolean condition, ErrorCode errorCode, String message) {
        throwIf(!condition, errorCode, message);
    }

    public static void notBlank(String value, ErrorCode errorCode, String message) {
        throwIf(StringUtils.isBlank(value), errorCode, message);
    }

    public static void notBlank(String[] values, ErrorCode errorCode, String message) {
        throwIf(StringUtils.isAnyBlank(values), errorCode, message);
    }

    public static void notEmpty(Collection<?> collection, ErrorCode errorCode, String message) {
        throwIf(CollUtil.isEmpty(collection), errorCode, message);
    }

    public static <T> void notNull(T value, ErrorCode errorCode) {
        throwIf(value == null, errorCode);
    }

    public static <T> void notNull(T value, ErrorCode errorCode, String message) {
        throwIf(value == null, errorCode, message);
    }

    public static void positive(Long num, ErrorCode errorCode) {
        notNull(num, errorCode);
        isTrue(num > 0, errorCode);
    }
}
