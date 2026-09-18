package com.xly.codeforge.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 相邻题目视图
 * <p>
 * 用于题目详情页的「上一题 / 下一题」导航。只带导航所需的最小字段，
 * 不返回上一题/下一题的完整内容 —— 前端点击时才去拉详情，避免一次返回三道题的全文。
 *
 * @author bot
 */
@Data
public class QuestionAdjacentVO implements Serializable {

    /**
     * 当前题目 id
     */
    private Long currentId;

    /**
     * 上一题（id 更小的最近一道）；已是第一题时为 null
     */
    private QuestionNavVO prev;

    /**
     * 下一题（id 更大的最近一道）；已是最后一题时为 null
     */
    private QuestionNavVO next;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 导航项（题目 id + 标题）
     * <p>
     * 单独抽出来是因为 prev/next 都要用同一结构，
     * 且只暴露 id 与 title 两个字段，不含任何判题信息。
     */
    @Data
    public static class QuestionNavVO implements Serializable {

        /**
         * 题目 id
         */
        private Long id;

        /**
         * 题目标题
         */
        private String title;

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
