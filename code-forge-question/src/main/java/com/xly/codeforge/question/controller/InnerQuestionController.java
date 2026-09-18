package com.xly.codeforge.question.controller;

import com.xly.codeforge.model.dto.dashboard.QuestionStatsDTO;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.question.service.QuestionService;
import com.xly.codeforge.question.service.QuestionStatsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目服务「内部接口」—— 供 submission-service / judge-service 通过 Feign 调用。
 *
 * <p>完整路径 {@code /api/question/inner/get/id}（context-path 是 /api/question）。</p>
 *
 * @author xuxu
 */
@RestController
@RequestMapping("/inner")
public class InnerQuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionStatsService questionStatsService;

    /**
     * 根据 id 获取题目（Feign: GET /api/question/inner/get/id）
     */
    @GetMapping("/get/id")
    public Question getQuestionById(@RequestParam("id") long id) {
        return questionService.getById(id);
    }

    /**
     * 加载题目域统计（Feign: GET /api/question/inner/stats）
     *
     * <p>供 dashboard 聚合用。放在 {@code /inner} 下而不是公开路径：
     * 统计结果只有聚合方需要，暴露给前端只会多一个需要防爬的接口。</p>
     */
    @GetMapping("/stats")
    public QuestionStatsDTO getStats() {
        return questionStatsService.loadStats();
    }
}
