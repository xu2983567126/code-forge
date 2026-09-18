package com.xly.codeforge.model.dto.questionbankfavourite;

import com.xly.codeforge.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题单收藏 / 分页查询请求
 *
 * <p>收藏动作只用 {@link #bankId}（路径参数已带则忽略）；分页查询只用分页字段。</p>
 *
 * @author xuxu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionBankFavouriteRequest extends PageRequest implements Serializable {

    /**
     * 题单 id（收藏动作使用）
     */
    private Long bankId;

    /**
     * 搜索关键词（模糊匹配题单标题，分页查询使用）
     */
    private String searchText;

    @Serial
    private static final long serialVersionUID = 1L;
}
