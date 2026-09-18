package com.xly.codeforge.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 题目提交
 * @TableName question_submit
 */
@TableName(value ="question_submit")
@Data
public class Submission {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 判题信息（json 对象）
     */
    private String judgeInfo;

    /**
     * 判题状态（0 - 待判题、1 - 判题中、2 - 成功、3 - 失败）
     */
    private Integer status;

    /**
     * 判题结果（verdict）
     *
     * <p>取值见 {@link com.xly.codeforge.model.enums.VerdictEnum}，
     * 如 {@code ACCEPTED} / {@code WRONG_ANSWER}。
     * 与 {@link #status} 的区别：status 描述<b>判题流程</b>走到哪一步，
     * verdict 描述<b>代码本身</b>的判定结论（AC / WA / TLE ...）。</p>
     *
     * <p>之所以从 {@code judgeInfo} JSON 里把结论再抽一列出来：状态筛选与统计
     * （如「只看答案错误的提交」、按 verdict 聚合的通过率）若走 JSON 提取，
     * 无法命中索引，只能全表扫。抽成独立列后可走 {@code idx_status_verdict}。</p>
     */
    private String verdict;

    /**
     * 题目 id
     */
    private Long questionId;

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

}