package com.xly.codeforge.submission.service;

import com.xly.codeforge.model.dto.SubmissionStatsItemDTO;
import com.xly.codeforge.model.dto.dashboard.SubmissionStatsDTO;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;

import java.util.List;

/**
 * 提交域统计服务
 *
 * <p>统计 SQL 留在本服务（数据属主），只对外暴露算好的数字。
 * 详见 {@code QuestionStatsService} 的类注释说明。</p>
 *
 * @author xuxu
 */
public interface SubmissionStatsService {

    /**
     * 加载提交域全部统计块
     *
     * @return 统计结果
     */
    SubmissionStatsDTO loadStats();

    /**
     * 加载某用户的提交热力图
     *
     * @param userId 用户 id
     * @param days   统计窗口天数（1~365）
     * @return 热力图数据
     */
    UserHeatmapDTO loadHeatmap(Long userId, int days);

    /**
     * 批量统计若干用户的提交数与通过题目数（管理端用户列表用）
     *
     * <p>一次 SQL 覆盖整页用户。返回结果<b>只包含有提交记录的用户</b>，
     * 调用方需给缺行补 0。</p>
     *
     * @param userIds 用户 id 列表；为空时返回空列表
     * @return 每个用户的统计
     */
    List<SubmissionStatsItemDTO> listStatsByUserIds(List<Long> userIds);
}
