package com.xly.codeforge.common.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * SQL 工具
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
public class SqlUtils {

    /**
     * 合法列名 / 排序字段：字母或下划线开头，后续只允许字母、数字、下划线。
     * <p>
     * 这一条就足够挡住 SQL 注入 —— 分号、引号、括号、空格、反引号、注释符全都不在白名单里。
     */
    private static final Pattern SAFE_COLUMN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * 单个标识符长度上限（MySQL 列名上限 64，这里留出余量足够）。
     */
    private static final int MAX_LENGTH = 64;

    /**
     * 校验排序字段是否合法（防止 SQL 注入）。
     * <p>
     * 白名单校验：排序字段必须是纯粹的下划线 / 字母数字标识符，形如 {@code create_time}。
     * <p>
     * 黑名单式的「只拦几个敏感字符」挡不住 {@code id;drop}、{@code id`} 这类输入，
     * 因此这里反过来只放行合法标识符。
     *
     * @param sortField 待校验的排序字段（DB 列名）
     * @return true 表示可安全拼接进 SQL
     */
    public static boolean validSortField(String sortField) {
        if (StringUtils.isBlank(sortField) || sortField.length() > MAX_LENGTH) {
            return false;
        }
        return SAFE_COLUMN.matcher(sortField).matches();
    }
}
