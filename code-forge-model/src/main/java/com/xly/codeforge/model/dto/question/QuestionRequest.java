package com.xly.codeforge.model.dto.question;

import java.util.List;

public interface QuestionRequest {
    List<String> getTags();

    List<JudgeCase> getJudgeCase();

    JudgeConfig getJudgeConfig();
}
