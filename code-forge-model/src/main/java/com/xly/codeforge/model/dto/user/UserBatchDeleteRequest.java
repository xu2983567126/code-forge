package com.xly.codeforge.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量删除用户请求（管理端）
 *
 * <p>为什么不用 {@code DELETE} + body：部分网关/代理会丢弃 DELETE 的请求体，
 * 参数会静默变成空列表 —— 那种「接口返回成功但什么都没删」比报错更难查。
 * 因此批量删除走 {@code POST} + body，单条删除才用 {@code DELETE /{id}}。</p>
 *
 * @author xuxu
 */
@Data
public class UserBatchDeleteRequest implements Serializable {

    /**
     * 待删除的用户 id 列表
     */
    private List<Long> idList;

    @Serial
    private static final long serialVersionUID = 1L;
}
