package com.xly.codeforge.question.service.impl;

import com.xly.codeforge.model.dto.dashboard.QuestionStatsDTO;
import com.xly.codeforge.model.entity.Question;
import com.xly.codeforge.question.mapper.QuestionBankMapper;
import com.xly.codeforge.question.mapper.QuestionMapper;
import com.xly.codeforge.question.service.QuestionStatsService;
import com.xly.codeforge.question.service.QuestionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目域统计服务实现
 *
 * @author xuxu
 */
@Service
@Slf4j
public class QuestionStatsServiceImpl implements QuestionStatsService {

    /**
     * 难度为 NULL 时归入的分类名
     *
     * <p>不隐藏这类记录：若直接丢掉，各分组之和会小于总数，
     * 前端饼图看起来「少了一块」却查不出原因。</p>
     */
    private static final String UNCATEGORIZED = "未分类";

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionBankMapper questionBankMapper;

    @Override
    public QuestionStatsDTO loadStats() {
        QuestionStatsDTO stats = new QuestionStatsDTO();

        // 题目总数：走 MyBatis-Plus 的 count（自动带上 @TableLogic 的 is_delete = 0）
        stats.setTotalCount(questionService.count());

        // 难度分布
        stats.setDifficultyDistribution(toMap(questionMapper.countByDifficulty(), UNCATEGORIZED));

        // 标签分布：为 NULL 的标签不参与统计（没打标签的题不该算进「未分类」标签里）
        stats.setTagDistribution(toMap(questionMapper.countByTag(), null));

        // 题单总数（逻辑删除由 @TableLogic 自动过滤）
        stats.setBankCount(questionBankMapper.selectCount(null));

        return stats;
    }

    /**
     * 把「分组计数」行列表转成 Map
     *
     * <p>用 {@link LinkedHashMap} 保持 SQL 的排序（标签统计是按数量降序返回的），
     * 前端拿到的顺序就是稳定的。</p>
     *
     * @param rows           查询结果
     * @param nullKeyFallback bucket 为 null 时使用的替代键；传 null 表示丢弃该行
     * @return 有序 Map
     */
    private Map<String, Long> toMap(List<QuestionMapper.BucketCount> rows, String nullKeyFallback) {
        if (rows == null || rows.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Long> result = new LinkedHashMap<>(rows.size());
        for (QuestionMapper.BucketCount row : rows) {
            String key = row.bucket();
            if (StringUtils.isBlank(key)) {
                if (nullKeyFallback == null) {
                    continue;
                }
                key = nullKeyFallback;
            }
            // 同一 key 出现两次时累加而不是覆盖 —— GROUP BY 理论上不会重复，
            // 但「未分类」兜底可能把 NULL 与空串合并到同一个键上
            result.merge(key, row.cnt() == null ? 0L : row.cnt(), Long::sum);
        }
        return result;
    }
}
