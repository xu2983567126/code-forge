package com.xly.codeforge.judge.controller;

import com.xly.codeforge.judge.JudgeService;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import com.xly.codeforge.model.vo.SubmissionVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 判题服务「内部接口」—— 供 submission-service 通过 Feign 调用。
 *
 * @author xuxu
 */
@RestController
@RequestMapping("/inner")
public class InnerJudgeController {

    @Resource
    private JudgeService judgeService;

    /**
     * 执行判题（Feign: POST /api/judge/inner/do）
     *
     * @param questionSubmitId 提交记录 id
     * @return 判题后的提交记录 VO
     */
    @PostMapping("/do")
    public SubmissionVO doJudge(@RequestParam("questionSubmitId") long questionSubmitId) {
        return judgeService.judge(questionSubmitId);
    }

    /**
     * 试运行代码（Feign: POST /api/judge/inner/run）
     *
     * <p>沙箱调用逻辑全部留在本服务 —— submission-service 没有
     * {@code codesandbox.type} 配置、没有 {@code CodeSandboxFactory}，
     * 让它直连沙箱会形成第二套沙箱接入代码，日后改签名/改地址必然漏改一处。</p>
     *
     * <p>之所以把入参包成 {@code ExecuteCodeRequest} 而不是散装三个参数：
     * Feign 的 {@code @RequestParam} 传 {@code List<String>} 需要逐个拼
     * {@code inputList=1&inputList=2}，用 body 更稳，也不受 URL 长度限制。</p>
     *
     * @param executeCodeRequest 执行请求（含 code / language / inputList）
     * @return 沙箱原始执行结果
     */
    @PostMapping("/run")
    public ExecuteCodeResponse runCode(@RequestBody com.xly.codeforge.model.judge.ExecuteCodeRequest executeCodeRequest) {
        return judgeService.runCode(executeCodeRequest.getCode(), executeCodeRequest.getLanguage(),
                executeCodeRequest.getInputList());
    }
}
