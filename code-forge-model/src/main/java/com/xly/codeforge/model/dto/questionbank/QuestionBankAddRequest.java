package com.xly.codeforge.model.dto.questionbank;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建题单请求
 *
 * <p>不含 {@code userId}：创建者一律从登录态取，防止前端伪造他人 id 建题单。</p>
 *
 * @author xuxu
 */
@Data
public class QuestionBankAddRequest implements Serializable {

    /**
     * 题单标题
     */
    private String title;

    /**
     * 题单描述
     */
    private String description;

    /**
     * 题单封面图 URL
     */
    private String picture;

    /**
     * 是否公开：0-私有 1-公开（不传默认公开）
     */
    private Integer isPublic;

    /**
     * 初始加入的题目 id 列表（可空）
     *
     * <p>与「批量加题」接口的区别：本字段用于「建题单时顺手选几道题」的场景，
     * 避免前端建完题单再发一次批量请求。Service 内部会复用同一套批量逻辑。</p>
     */
    private List<Long> questionIdList;

    @Serial
    private static final long serialVersionUID = 1L;
}
