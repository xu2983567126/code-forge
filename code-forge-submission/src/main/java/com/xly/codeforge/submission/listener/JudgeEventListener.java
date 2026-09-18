package com.xly.codeforge.submission.listener;

import com.xly.codeforge.submission.event.JudgeEvent;
import com.xly.codeforge.client.service.JudgeFeignClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JudgeEventListener {

    @Resource
    private JudgeFeignClient judgeFeignClient;

    @Async
    @EventListener
    public void handleJudgeEvent(JudgeEvent event) {
        Long submissionId = event.getSubmissionId();
        log.info("收到判题事件，submissionId: {}", submissionId);
        try {
            judgeFeignClient.judge(submissionId);
        } catch (Exception e) {
            log.error("判题失败，submissionId: {}", submissionId, e);
            // 可根据需要增加重试或死信队列处理
        }
    }
}
