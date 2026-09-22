package com.xly.codeforge.model.dto.question;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目配置
 */

@Data
public class JudgeConfig implements Serializable {

    /**
     * 时间限制（ms）
     */
    private Long timeLimit;

    /**
     * 空间限制（KB）
     */
    private Long memoryLimit;

    /**
     * 堆栈限制（kB）
     *
     * <p>⚠️ <b>仅供展示</b>：题目详情页会显示它、题目表单会编辑它，但<b>判题链路不读</b>——
     * 沙箱没有据此施加 {@code ulimit -s}，真正生效的限制是容器内存上限与 CPU 时间上限。
     * 保留是因为它属于题目配置契约且前端有两处 UI；若要让它真正生效，
     * 需要把该值下发给沙箱（{@code ExecuteCodeRequest} 加字段）并在容器内使用。</p>
     */
    private Long stackLimit;

    /**
     * 输出比对模式：STANDARD / STRICT / FLOAT / SPJ，缺省 STANDARD。
     * 由题目配置决定「用户输出与标准答案算不算一致」的口径。
     */
    private String compareMode = "STANDARD";

    private static final long serialVersionUID = 1L;
}
