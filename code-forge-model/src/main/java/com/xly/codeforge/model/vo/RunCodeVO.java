package com.xly.codeforge.model.vo;

import com.xly.codeforge.model.dto.submission.JudgeInfo;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 试运行结果视图
 *
 * <p>用于 {@code POST /submission/run} 的返回。刻意<b>不</b>复用 {@code SubmissionVO} ——
 * 试运行没有提交记录，暴露 id / userId / createTime 这些字段只会让前端误以为
 * 「已经在提交列表里了」。</p>
 *
 * @author xuxu
 */
@Data
public class RunCodeVO implements Serializable {

    /**
     * 每个测试用例的输出
     */
    private List<String> outputList;

    /**
     * 沙箱返回的原始状态：1-成功 2-编译错误 3-运行错误 4-系统异常
     *
     * <p>注意这描述的是「代码有没有跑起来」，不是「答案对不对」。
     * 答案对错看 {@link #verdict}。</p>
     */
    private Integer status;

    /**
     * 沙箱返回的信息（编译错误时是编译器的报错文本）
     */
    private String message;

    /**
     * 判题结论
     *
     * <p>仅当请求带了期望输出时才有意义；否则为 null，前端只展示 {@link #outputList}。
     * 取值见 {@link com.xly.codeforge.model.enums.VerdictEnum}。</p>
     */
    private String verdict;

    /**
     * 耗时（ms），各用例求和
     */
    private Long time;

    /**
     * 内存（KB），各用例求和
     */
    private Long memory;

    /**
     * 判题信息原始对象（保留了单用例的运行信息，供需要时展开）
     */
    private JudgeInfo judgeInfo;

    @Serial
    private static final long serialVersionUID = 1L;
}
