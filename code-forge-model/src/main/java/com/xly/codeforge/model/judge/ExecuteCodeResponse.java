package com.xly.codeforge.model.judge;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeResponse {

    /**
     * 沙箱回传的原始结论文案（编译/系统错误的 stderr / 异常栈）。
     *
     * <p>判题策略只在编译失败 / 系统错误时把它回写到 {@code JudgeInfo.detail}，
     * 供前端展示「为什么错」；常规用例判定不关心它。</p>
     */
    private String message;

    /**
     * 逐用例执行事实（退出码 / 是否超时 / 输出 / 耗时 / 内存等中性观测值）。
     * 判题结论的唯一依据：编译失败 / 系统错误靠下方布尔标记，逐用例 verdict 靠本字段比对。
     */
    private List<SandboxCaseResult> caseResults;

    /** 编译是否失败（true 时直接判编译错误，不再解析输出） */
    private Boolean compileError;

    /** 沙箱链路自身是否发生系统错误（true 时判系统错误） */
    private Boolean systemError;
}
