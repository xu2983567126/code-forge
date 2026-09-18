package com.xly.codeforge.client.service;

import com.xly.codeforge.model.dto.dashboard.QuestionStatsDTO;
import com.xly.codeforge.model.entity.Question;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 题目服务
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@FeignClient(name = "code-forge-question", path = "/api/question/inner")
public interface QuestionFeignClient {

    /**
     * 根据 id 查询题目信息
     *
     * @param id
     * @return
     */
    @GetMapping("/get/id")
    Question getQuestionById(@RequestParam("id") long id);

    /**
     * 加载题目域统计（dashboard 聚合用）
     *
     * @return 题目总数 / 难度分布 / 标签分布 / 题单数
     */
    @GetMapping("/stats")
    QuestionStatsDTO getStats();
}
