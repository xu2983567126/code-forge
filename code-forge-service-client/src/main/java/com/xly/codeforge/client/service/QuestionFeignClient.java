package com.xly.codeforge.client.service;

import com.xly.codeforge.model.dto.dashboard.QuestionStatsDTO;
import com.xly.codeforge.model.entity.Question;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 题目服务
 *
 */
@HttpExchange("http://code-forge-question/api/question/inner")
public interface QuestionFeignClient {

    /**
     * 根据 id 查询题目信息
     *
     * @param id
     * @return
     */
    @GetExchange("/get/id")
    Question getQuestionById(@RequestParam("id") long id);

    /**
     * 加载题目域统计（dashboard 聚合用）
     *
     * @return 题目总数 / 难度分布 / 标签分布 / 题单数
     */
    @GetExchange("/stats")
    QuestionStatsDTO getStats();
}
