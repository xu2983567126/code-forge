package com.xly.codeforge.client.service;


import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.vo.SubmissionVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "code-forge-judge", path = "/api/judge/inner")
public interface JudgeFeignClient {

    /**
     * 执行判题（异步链路用：submission-service 发事件后由 listener 调）
     *
     * @param questionSubmitId 提交记录 id
     * @return 判题后的提交记录
     */
    @PostMapping("/do")
    SubmissionVO judge(@RequestParam("questionSubmitId") long questionSubmitId);

    /**
     * 试运行代码（同步，不落库）
     *
     * <p>沙箱调用集中在 judge-service，submission-service 只做转发，
     * 避免出现第二套沙箱接入代码。</p>
     *
     * @param executeCodeRequest 执行请求（code / language / inputList）
     * @return 沙箱原始执行结果
     */
    @PostMapping("/run")
    ExecuteCodeResponse runCode(@RequestBody ExecuteCodeRequest executeCodeRequest);
}
