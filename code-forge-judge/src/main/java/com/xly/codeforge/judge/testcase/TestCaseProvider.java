package com.xly.codeforge.judge.testcase;

import com.xly.codeforge.model.entity.Question;
import java.util.List;

/**
 * 测试用例提供者。
 *
 * <p>把「用例从哪来、长什么样」与「判题怎么跑」解耦：判题主体只调用 {@link #getTestCases(Question)}，
 * 不关心底层是数据库还是对象存储。当前实现 {@code DatabaseTestCaseProvider} 从 {@link Question} 实体的
 * {@code judgeCase} 列解析；未来可新增 {@code ObjectStorageTestCaseProvider}（MinIO/S3）而不动判题逻辑
 * —— 沙箱契约（stdin 文本）也不动。</p>
 *
 * <p>入参直接收已取好的 {@link Question}（判题主体在抢到租约后已拉过题目，避免提供者内部再发一次 RPC 重复取题）。</p>
 */
public interface TestCaseProvider {

    /**
     * 从已取好的题目中解析全部测试用例（输入 + 期望输出），按 index 升序。
     *
     * @param question 已取好的题目实体（含 judgeCase 列）
     * @return 有序的测试用例列表；题目无用例时返回空列表（不抛异常）
     */
    List<TestCaseData> getTestCases(Question question);
}
