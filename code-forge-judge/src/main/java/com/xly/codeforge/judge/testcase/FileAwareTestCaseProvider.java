package com.xly.codeforge.judge.testcase;

import java.util.List;

/**
 * 真·文件判题（档位 B）的可选扩展 —— 当前仅作为演进口子的「牌子」，不实现。
 *
 * <p>与 {@link TestCaseProvider} 的默认契约（内存态 {@code TestCaseData} + stdin 喂沙箱）正交。
 * 只有需要「程序读文件而非 stdin」的题目才实现本接口；判题侧检测到实现类同时是
 * {@code FileAwareTestCaseProvider} 且 {@link #supportsFileMode()} 为真时，才走文件物化路径。</p>
 */
public interface FileAwareTestCaseProvider extends TestCaseProvider {

    /**
     * 该题目是否走真·文件判题（程序读文件而非 stdin）
     */
    boolean supportsFileMode();

    /**
     * 用例文件引用列表（对象 key / 路径 + 逻辑文件名），供沙箱物化
     */
    List<TestCaseFileRef> getTestCaseFileRefs(Long problemId);
}
