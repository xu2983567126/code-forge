package com.xly.codeforge.model.vo;

import com.xly.codeforge.model.dto.submission.JudgeCaseResult;
import com.xly.codeforge.model.dto.submission.JudgeInfo;
import com.xly.codeforge.model.enums.VerdictEnum;
import com.xly.codeforge.model.entity.Submission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SubmissionVO#objToVo(Submission)}：judgeInfo JSON 的解析与字段拷贝。
 */
class SubmissionVOTest {

    @Test
    void null输入返回null不抛错() {
        assertThat(SubmissionVO.objToVo(null)).isNull();
    }

    @Test
    void judgeInfo字符串解析为结构化对象() {
        Submission submission = new Submission();
        submission.setId(42L);
        submission.setLanguage("java");
        submission.setCode("public class Main {}");
        submission.setStatus(2);
        submission.setVerdict("ACCEPTED");
        submission.setUserId(1L);
        submission.setQuestionId(7L);
        // 固件里的 message 与逐用例 status 都用 VerdictEnum 的 code（历史文案 Accepted 已废弃）
        submission.setJudgeInfo(
                "{\"message\":\"ACCEPTED\",\"time\":70,\"memory\":3072,"
                        + "\"caseResults\":[{\"status\":\"ACCEPTED\",\"time\":30,\"memory\":1024}]}");

        SubmissionVO vo = SubmissionVO.objToVo(submission);

        assertThat(vo.getId()).isEqualTo(42L);
        assertThat(vo.getLanguage()).isEqualTo("java");
        assertThat(vo.getStatus()).isEqualTo(2);
        assertThat(vo.getVerdict()).isEqualTo("ACCEPTED");

        JudgeInfo judgeInfo = vo.getJudgeInfo();
        assertThat(judgeInfo).isNotNull();
        assertThat(judgeInfo.getMessage()).isEqualTo(VerdictEnum.ACCEPTED);
        assertThat(judgeInfo.getTime()).isEqualTo(70L);
        assertThat(judgeInfo.getMemory()).isEqualTo(3072L);
        assertThat(judgeInfo.getCaseResults())
                .hasSize(1)
                .first()
                .extracting(JudgeCaseResult::getStatus)
                .isEqualTo(VerdictEnum.ACCEPTED);
    }

    @Test
    void judgeInfo为null时VO侧保持null() {
        Submission submission = new Submission();
        submission.setId(1L);
        submission.setJudgeInfo(null);

        SubmissionVO vo = SubmissionVO.objToVo(submission);

        // 待判题 / 判题中的记录没有 judgeInfo，属正常状态而非错误
        assertThat(vo.getJudgeInfo()).isNull();
    }
}
