package com.xly.codeforge.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 带提交统计的用户视图（管理端专用）
 *
 * <p>在 {@link UserVO} 基础上追加「提交数 / 通过数 / 通过率」。
 * 这三个数字需要跨服务取（提交记录在 submission-service），
 * 所以不能直接塞进 {@code UserVO} —— 那会让公开榜单接口也被迫依赖 submission 服务。</p>
 *
 * <p>继承而不是新建独立类是刻意的：前端表格的列是「用户名 + 头像 + 角色 + 统计」，
 * 继承让 {@code BeanUtils.copyProperties} 一次填好基础字段，也保证脱敏规则与
 * {@code UserVO} 永远一致（不会漏掉某个敏感字段）。</p>
 *
 * @author xuxu
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserWithStatsVO extends UserVO {

    /**
     * 提交总数
     */
    private Long submitCount;

    /**
     * 通过题目数（DISTINCT question_id，不是 AC 次数）
     */
    private Long acceptedCount;

    /**
     * 通过率，0~1 之间保留 4 位小数
     */
    private Double acceptedRate;

    @Serial
    private static final long serialVersionUID = 1L;
}
