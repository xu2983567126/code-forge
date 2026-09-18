package com.xly.codeforge.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xly.codeforge.model.entity.Submission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 题目提交 Mapper
 *
 * @author user
 * @description 针对表【question_submit(题目提交)】的数据库操作Mapper
 * @Entity com.xly.myoj.model.entity.Submission
 */
public interface SubmissionMapper extends BaseMapper<Submission> {

    /**
     * 查询某用户在某题上「已通过且耗时最短」的提交 id
     *
     * <p>排序用 {@code CAST(... AS UNSIGNED)}：{@code judge_info} 是 JSON 文本列，
     * 直接比较字符串会出现 {@code "1000" < "200"} 这种字典序错误
     * （因为 '1' < '2'），必须转数字再比。</p>
     *
     * <p>耗时取不到（NULL / 非数字）时用 {@code 999999999} 兜底排到最后，
     * 保证「有 AC」时一定能选出记录，不会因为 JSON 字段缺失而返回空。</p>
     *
     * <p>SQL 里写空格再取下标：MySQL 的 {@code JSON_EXTRACT} 返回带引号的字符串，
     * 用 {@code ->>} 才能拿到裸值（{@code ->} 会带引号，CAST 后得 0）。</p>
     *
     * @param questionId 题目 id
     * @param userId     用户 id
     * @return 提交 id；无 AC 记录时返回 null
     */
    @Select("""
            SELECT id FROM question_submit
            WHERE question_id = #{questionId}
              AND user_id = #{userId}
              AND verdict = 'ACCEPTED'
              AND is_delete = 0
            ORDER BY CAST(COALESCE(judge_info ->> '$.time', '999999999') AS UNSIGNED) ASC
            LIMIT 1
            """)
    Long selectBestAcceptedId(@Param("questionId") Long questionId, @Param("userId") Long userId);

    /**
     * 查询某用户在某题上最近一次提交 id（无 AC 时用于回退展示）
     *
     * @param questionId 题目 id
     * @param userId     用户 id
     * @return 提交 id；无提交时返回 null
     */
    @Select("""
            SELECT id FROM question_submit
            WHERE question_id = #{questionId}
              AND user_id = #{userId}
              AND is_delete = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    Long selectLatestId(@Param("questionId") Long questionId, @Param("userId") Long userId);

    /**
     * 查询某用户在若干题目上「已通过」的题目 id 集合
     *
     * <p>{@code DISTINCT} 必需：一道题可能 AC 多次，不去重会让调用方以为
     * 有多道题通过。</p>
     *
     * <p>返回空集合由调用方先判空 —— {@code IN ()} 是语法错误，不能传空列表进来。</p>
     *
     * @param questionIds 题目 id 集合（不可为空）
     * @param userId      用户 id
     * @return 已通过的题目 id 列表
     */
    @Select("""
            <script>
            SELECT DISTINCT question_id FROM question_submit
            WHERE user_id = #{userId}
              AND verdict = 'ACCEPTED'
              AND is_delete = 0
              AND question_id IN
              <foreach collection="questionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> selectAcceptedQuestionIds(@Param("questionIds") List<Long> questionIds,
                                         @Param("userId") Long userId);

    /**
     * 回填 verdict：把 {@code judge_info.message} 翻译成 verdict code 写回
     *
     * <p>用 CASE 表达式在数据库侧批量完成，不把数据拉到 Java 里循环 update
     * —— 历史数据可能有几万条，逐条往返的开销不可接受。</p>
     *
     * <p>映射表与 {@code VerdictEnum.fromJudgeMessage} 保持一致；
     * 无法识别的文案归入 {@code SYSTEM_ERROR}（宁可显示系统错误也不能留空，
     * 空 verdict 会让记录在筛选中「消失」）。</p>
     *
     * <p>只处理 {@code verdict IS NULL AND status = 2}：status != 2 的记录本来就没有结论
     * （待判题 / 判题中 / 失败），给它们写 verdict 反而是错的。</p>
     *
     * @return 受影响行数
     */
    @Update("""
            UPDATE question_submit SET verdict = CASE
                WHEN judge_info ->> '$.message' = 'Accepted' THEN 'ACCEPTED'
                WHEN judge_info ->> '$.message' = 'Wrong Answer' THEN 'WRONG_ANSWER'
                WHEN judge_info ->> '$.message' = 'Compiling Error' THEN 'COMPILE_ERROR'
                WHEN judge_info ->> '$.message' = 'Runtime Error' THEN 'RUNTIME_ERROR'
                WHEN judge_info ->> '$.message' = 'Time Limit Exceeded' THEN 'TIME_LIMIT_EXCEEDED'
                WHEN judge_info ->> '$.message' = 'Memory Limit Exceeded' THEN 'MEMORY_LIMIT_EXCEEDED'
                WHEN judge_info ->> '$.message' = 'Presentation Error' THEN 'PRESENTATION_ERROR'
                WHEN judge_info ->> '$.message' = 'Dangerous Operation' THEN 'DANGEROUS_OPERATION'
                ELSE 'SYSTEM_ERROR'
            END
            WHERE verdict IS NULL
              AND status = 2
              AND is_delete = 0
            """)
    int backfillVerdict();

    /**
     * 批量统计若干用户的提交数与通过题目数
     *
     * <p><b>一次 SQL 覆盖整页用户</b>，调用方拿到的行数 ≤ userIds.size()：
     * 没有任何提交的用户在结果里根本不出现（不是 cnt=0 的行），
     * 所以调用方必须给缺行补 0，不能假设「每行都有」。</p>
     *
     * <p>通过数用 {@code COUNT(DISTINCT question_id)} 而不是 {@code COUNT(*)}：
     * 同一道题 AC 多次只算一道，{@code COUNT(*)} 得到的是 AC 次数。</p>
     *
     * <p>用 {@code SUM(CASE WHEN ...)} 而不是 {@code COUNT(DISTINCT ...)} 拆两条 SQL：
     * 两个指标来自同一次全表扫描，少一次数据库往返。</p>
     *
     * @param userIds 用户 id 列表（不可为空）
     * @return 每行：userId / submitCount / acceptedCount
     */
    @Select("""
            <script>
            SELECT user_id AS userId,
                   COUNT(*) AS submitCount,
                   COUNT(DISTINCT CASE WHEN verdict = 'ACCEPTED' THEN question_id END) AS acceptedCount
            FROM question_submit
            WHERE is_delete = 0
              AND user_id IN
              <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
            GROUP BY user_id
            </script>
            """)
    List<com.xly.codeforge.model.dto.SubmissionStatsItemDTO> selectStatsByUserIds(
            @Param("userIds") List<Long> userIds);

    // region dashboard 统计（只读聚合，供 /inner/stats 使用）

    /**
     * 统计符合条件的提交数
     *
     * <p>用 MyBatis-Plus 的 {@code selectCount} 配 Wrapper 也能做，但这里走原生 SQL
     * 是为了让「今日」「总数」两类统计共用同一段 SQL —— 差异只在 {@code extraCondition}，
     * 避免在 Service 里拼 Wrapper 判断。</p>
     *
     * @param todayStart 今日零点；传 null 表示统计全部
     * @return 提交数
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM question_submit
            WHERE is_delete = 0
            <if test="todayStart != null">
              AND create_time >= #{todayStart}
            </if>
            </script>
            """)
    Long countSubmissions(@Param("todayStart") java.time.LocalDateTime todayStart);

    /**
     * 按 verdict 分组统计提交数
     *
     * <p>{@code COALESCE(verdict, 'UNKNOWN')}：verdict 是后加的列，历史记录该列为 NULL。
     * 若不合并，前端饼图会出现一个 null 扇区，且各项之和与总数对不上。</p>
     *
     * @return 每行：bucket（verdict code 或 UNKNOWN） / cnt
     */
    @Select("""
            SELECT COALESCE(verdict, 'UNKNOWN') AS bucket, COUNT(*) AS cnt
            FROM question_submit
            WHERE is_delete = 0
            GROUP BY COALESCE(verdict, 'UNKNOWN')
            """)
    List<BucketCount> countByVerdict();

    /**
     * 按编程语言分组统计提交数
     *
     * @return 每行：language / cnt
     */
    @Select("""
            SELECT language AS bucket, COUNT(*) AS cnt
            FROM question_submit
            WHERE is_delete = 0
            GROUP BY language
            ORDER BY cnt DESC
            """)
    List<BucketCount> countByLanguage();

    /**
     * 按日期分组统计提交数（用于趋势图 / 热力图）
     *
     * <p>日期用 {@code DATE(create_time)} 截断到天，再按天分组。</p>
     *
     * <p><b>本查询只返回有提交的日期</b>，缺失的日期由 Service 侧补零
     * （见 {@code SubmissionServiceImpl#fillDateGaps}）——SQL 里造日期序列
     * 需要递归 CTE，复杂且难读，放在 Java 里用 LocalDate 迭代更清楚。</p>
     *
     * @param start    起始时间（含）
     * @param end      结束时间（不含）
     * @param userId   用户 id；传 null 表示统计全部用户
     * @return 每行：日期字符串 yyyy-MM-dd / cnt，按日期升序
     */
    @Select("""
            <script>
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS bucket, COUNT(*) AS cnt
            FROM question_submit
            WHERE is_delete = 0
              AND create_time &gt;= #{start}
              AND create_time &lt; #{end}
            <if test="userId != null">
              AND user_id = #{userId}
            </if>
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
            ORDER BY bucket ASC
            </script>
            """)
    List<BucketCount> countByDay(@Param("start") java.time.LocalDateTime start,
                                 @Param("end") java.time.LocalDateTime end,
                                 @Param("userId") Long userId);

    /**
     * 通用「分组计数」结果载体（与 {@code QuestionMapper.BucketCount} 同构）
     *
     * <p>两份 record 而不是共用一份：mapper 属不同服务模块，
     * 为了共用一个 record 而把它塞进 model 模块，会让「统计载体」这个纯技术细节
     * 泄漏到公共契约里。重复 4 行代码换来边界清晰，值得。</p>
     */
    record BucketCount(String bucket, Long cnt) {
    }

    // endregion
}
