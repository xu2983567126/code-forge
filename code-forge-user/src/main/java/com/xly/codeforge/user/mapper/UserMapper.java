package com.xly.codeforge.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xly.codeforge.model.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户数据库操作
 *
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 统计符合条件的用户数
     *
     * @param todayStart   今日零点；传 null 表示统计全部
     * @param role         指定角色；传 null 表示不限
     * @return 用户数
     */
    Long countUsers(@Param("todayStart") LocalDateTime todayStart, @Param("role") String role);

    /**
     * 按角色分组统计用户数（供 dashboard）
     *
     * @return 每行：bucket（角色）/ cnt
     */
    @Select("""
            SELECT role AS bucket, COUNT(*) AS cnt
            FROM user
            WHERE is_delete = 0
            GROUP BY role
            """)
    List<BucketCount> countByRole();

    /**
     * 按日期分组统计新增用户数（用于趋势图）
     *
     * <p>只返回有新增的日期，缺失日期由 Service 侧补零。</p>
     *
     * @param start 起始时间（含）
     * @param end   结束时间（不含）
     * @return 每行：日期 yyyy-MM-dd / cnt
     */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS bucket, COUNT(*) AS cnt
            FROM user
            WHERE is_delete = 0
              AND create_time >= #{start}
              AND create_time < #{end}
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
            ORDER BY bucket ASC
            """)
    List<BucketCount> countByDay(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * 通用「分组计数」结果载体
     */
    record BucketCount(String bucket, Long cnt) {
    }
}
