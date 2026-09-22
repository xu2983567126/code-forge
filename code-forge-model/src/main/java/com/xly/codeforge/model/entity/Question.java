package com.xly.codeforge.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目
 * @TableName question
 */
@TableName(value ="question")
@Data
public class Question implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表（json 数组）
     */
    private String tags;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 难度：简单/中等/困难
     * <p>
     * 用于题库专题页的难度筛选；默认「简单」，存量题以此兜底。
     */
    private String difficulty;

    /**
     * 题目提交数
     *
     * <p>⚠️ <b>不是表列</b>（{@code @TableField(exist = false)}）：提交统计属于提交域，
     * 由 {@code SubmissionFeignClient.listStatsByQuestionIds} 在读取时实时聚合后填充。
     * 原先对应的 {@code question.submit_num} 列运行期没有任何更新点，已删除。</p>
     */
    @TableField(exist = false)
    private Integer submitNum;

    /**
     * 题目通过数
     *
     * <p>同 {@link #submitNum}：非表列，读取时由提交域统计填充。</p>
     */
    @TableField(exist = false)
    private Integer acceptedNum;

    /**
     * 判题用例（json 数组）
     */
    private String judgeCase;

    /**
     * 判题配置（json 对象）
     */
    private String judgeConfig;

    /**
     * 特判程序源码（compareMode=SPJ 时由沙箱执行，逐用例拿「标准答案 + 用户输出」当参数）
     * <p>只供 admin/作者查看，不进公开 {@code QuestionVO}。</p>
     */
    private String spjCode;

    /**
     * 特判程序语言，取值与 submission.language 对齐（沙箱按此编译/运行同一套语言）
     */
    private String spjLanguage;

    /**
     * 判题代码模板（编辑器预置骨架，用户可见）
     * <p>公开字段：前端题目页加载到代码编辑器预填，与 spjCode（仅 admin 可见）安全属性相反。</p>
     */
    private String codeTemplate;

    /**
     * 点赞数
     */
    private Integer thumbNum;

    /**
     * 收藏数
     */
    private Integer favourNum;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}