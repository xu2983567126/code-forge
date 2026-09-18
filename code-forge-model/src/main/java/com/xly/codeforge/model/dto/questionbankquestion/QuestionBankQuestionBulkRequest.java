package com.xly.codeforge.model.dto.questionbankquestion;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量操作题单-题目关联请求
 *
 * <p><b>为什么用单一接口 + {@code action} 而不是 ADD/DELETE 两个接口</b>：
 * HTTP 的 DELETE 语义上不适合承载请求体（部分代理/客户端会丢弃 DELETE body，
 * 参考项目 UltiCode 就在注释里记录过这个坑）。批量删除若走
 * {@code DELETE /xxx?ids=1,2,3} 则受 URL 长度限制，几百道题就会超。
 * 因此选定「POST + bulk + action」这一种写法，两条路都走 POST。</p>
 *
 * <p><b>action 取值走枚举而非裸字符串</b>：避免前端拼错（{@code add}/{@code ADD}/{@code plus}）
 * 导致静默无操作。非法值直接抛参数错误。</p>
 *
 * @author xuxu
 */
@Data
public class QuestionBankQuestionBulkRequest implements Serializable {

    /**
     * 题单 id
     */
    private Long questionBankId;

    /**
     * 题目 id 列表
     */
    private List<Long> questionIdList;

    /**
     * 操作类型：ADD-批量加入 REMOVE-批量移出
     */
    private String action;

    @Serial
    private static final long serialVersionUID = 1L;
}
