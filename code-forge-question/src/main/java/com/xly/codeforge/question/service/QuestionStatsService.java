package com.xly.codeforge.question.service;

import com.xly.codeforge.model.dto.dashboard.QuestionStatsDTO;

/**
 * 题目域统计服务
 *
 * <p><b>为什么统计逻辑放这里而不是放调用方（dashboard）</b>：
 * 统计 SQL 必须贴着数据写。放到 dashboard 那边意味着它要 import question 的表结构，
 * 一旦题目表加字段、改索引，dashboard 会跟着编译失败或跑出错误数字。
 * 这里只对外暴露一个「已经算好的数字」DTO，SQL 改动的影响面被锁在本服务内。</p>
 *
 * <p>这也是 UltiCode 的做法：其 {@code DashboardAdminReadPort} 的接口注释明确写了
 * 「App provider owns all SQL … Admin never imports those mappers or tables」。</p>
 *
 * @author xuxu
 */
public interface QuestionStatsService {

    /**
     * 加载题目域全部统计块
     *
     * @return 统计结果
     */
    QuestionStatsDTO loadStats();
}
