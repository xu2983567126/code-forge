package com.xly.codeforge.submission.event;

import org.springframework.context.ApplicationEvent;

public class JudgeEvent extends ApplicationEvent {
    private final Long questionSubmitId;

    public JudgeEvent(Object source, Long questionSubmitId) {
        super(source);
        this.questionSubmitId = questionSubmitId;
    }

    public Long getSubmissionId() {
        return questionSubmitId;
    }
}
