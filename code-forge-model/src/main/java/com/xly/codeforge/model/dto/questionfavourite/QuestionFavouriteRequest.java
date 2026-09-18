package com.xly.codeforge.model.dto.questionfavourite;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目收藏请求
 *
 * <p>收藏与取消收藏共用本请求：Service 内部按「已收藏则删、未收藏则插」切换，
 * 对外表现为切换式接口（返回 +1 表示已收藏、-1 表示已取消）。
 * 这与项目现有 {@code POST /question/{id}/save} 的路径语义一致 —— 同一路径，
 * 通过 POST / DELETE 切换关系，无需前端判断当前状态。</p>
 *
 * @author xuxu
 */
@Data
public class QuestionFavouriteRequest implements Serializable {

    /**
     * 题目 id
     */
    private Long questionId;

    @Serial
    private static final long serialVersionUID = 1L;
}
