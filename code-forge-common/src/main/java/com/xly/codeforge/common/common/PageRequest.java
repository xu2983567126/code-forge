package com.xly.codeforge.common.common;

import com.xly.codeforge.common.constant.CommonConstant;
import lombok.Data;

/**
 * 分页请求
 *
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段（**数据库列名**，下划线风格，如 {@code create_time}）
     * <p>
     * ⚠️ 本字段直接拼进 {@code ORDER BY}，传驼峰（{@code createTime}）会报
     * {@code Unknown column}（DB 列全为 snake_case），并且会被
     * {@link com.xly.codeforge.common.utils.SqlUtils#validSortField(String)} 白名单挡下。
     */
    private String sortField;

    /**
     * 排序顺序（默认升序）
     */
    private String sortOrder = CommonConstant.SORT_ORDER_ASC;
}
