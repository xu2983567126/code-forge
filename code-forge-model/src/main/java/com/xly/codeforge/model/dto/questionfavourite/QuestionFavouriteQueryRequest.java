package com.xly.codeforge.model.dto.questionfavourite;

import com.xly.codeforge.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 我的题目收藏分页查询请求
 *
 * @author xuxu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionFavouriteQueryRequest extends PageRequest implements Serializable {

    /**
     * 搜索关键词（模糊匹配题目标题）
     */
    private String searchText;

    /**
     * 难度筛选：简单/中等/困难
     */
    private String difficulty;

    @Serial
    private static final long serialVersionUID = 1L;
}
