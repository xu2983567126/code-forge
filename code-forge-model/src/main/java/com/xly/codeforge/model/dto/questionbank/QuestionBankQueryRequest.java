package com.xly.codeforge.model.dto.questionbank;

import com.xly.codeforge.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询题单请求
 *
 * @author xuxu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionBankQueryRequest extends PageRequest implements Serializable {

    /**
     * 题单 id
     */
    private Long id;

    /**
     * 排除的题单 id
     */
    private Long notId;

    /**
     * 搜索关键词（同时模糊匹配标题、描述）
     */
    private String searchText;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建用户 id
     *
     * <p>「我的题单」接口会由后端强制覆盖为当前登录用户 id，前端传什么都无效 ——
     * 这是防越权读他人题单列表的关键。</p>
     */
    private Long userId;

    /**
     * 是否公开：0-私有 1-公开
     *
     * <p>普通列表接口会把「公开的 OR 自己的」作为默认过滤条件（见 Service），
     * 该字段用于前端主动筛选（如只看公开题单）。</p>
     */
    private Integer isPublic;

    @Serial
    private static final long serialVersionUID = 1L;
}
