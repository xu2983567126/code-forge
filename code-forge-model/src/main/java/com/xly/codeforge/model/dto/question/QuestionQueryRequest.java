package com.xly.codeforge.model.dto.question;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.xly.codeforge.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 查询请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
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
    private List<String> tags;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 难度：简单/中等/困难
     * <p>
     * 精确匹配，用于题库专题页的难度筛选。
     */
    private String difficulty;

    /**
     * 搜索关键词（模糊匹配标题或内容）
     * <p>
     * 与 {@link #title}/{@link #content} 的区别：那两个是各自独立的精确字段模糊匹配，
     * 本字段是「标题 or 内容」的合并搜索，用于搜索框一站到底的输入。
     */
    private String searchText;

    /**
     * 排除的题目 id
     * <p>
     * 用于「随机一题」时避开当前正在看的题，以及相邻题查询时排除自身。
     */
    private Long notId;

    /**
     * 创建用户 id
     */
    private Long userId;

    @Serial
    private static final long serialVersionUID = 1L;
}