package com.xly.codeforge.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xly.codeforge.model.dto.submission.SubmissionFenceRequest;
import com.xly.codeforge.model.dto.submission.SubmissionVerdictRequest;
import com.xly.codeforge.model.entity.Submission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 题目提交 Mapper
 *
 * @author user
 * @description 针对表【submission(题目提交)】的数据库操作Mapper
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
            SELECT id FROM submission
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
            SELECT id FROM submission
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
    List<Long> selectAcceptedQuestionIds(@Param("questionIds") List<Long> questionIds,
                                         @Param("userId") Long userId);

    /**
     * 回填 verdict：把 {@code judge_info.message} 翻译成 verdict code 写回
     *
     * <p>用 CASE 表达式在数据库侧批量完成，不把数据拉到 Java 里循环 update
     * —— 历史数据可能有几万条，逐条往返的开销不可接受。</p>
     *
     * <p>历史数据回填用：早期 {@code judge_info.message} 存的是展示文案（Accepted / Wrong Answer…），
     * 现收敛为单枚举后 message 即 verdict code；本 CASE 仅把遗留英文文案翻成 code。
     * 无法识别的文案归入 {@code SYSTEM_ERROR}（宁可显示系统错误也不能留空，
     * 空 verdict 会让记录在筛选中「消失」）。</p>
     *
     * <p>只处理 {@code verdict IS NULL AND status = 2}：status != 2 的记录本来就没有结论
     * （待判题 / 判题中 / 失败），给它们写 verdict 反而是错的。</p>
     *
     * @return 受影响行数
     */
    @Update("""
            UPDATE submission SET verdict = CASE
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

    // region 判题并发 fencing（M1，对齐 UltiCode SubmissionMapper，适配 INT status）
    //
    // 五条 CAS + 两条捞取，全部以 DB 时钟 + generation CAS 保证多实例安全。
    // judge-service 经 Feign 只传 generation/attemptId，真正的 CAS 落在这里。

    /**
     * ① 抢占租约：把 WAITING 的提交原子地置为 RUNNING，写入 attemptId + 租约过期时间（DB 时钟）。
     *
     * <p>返回 1=抢到；0=已被别人抢走或已非 WAITING（含已终态 / 重复事件）。</p>
     */
    @Update("""
            UPDATE submission
            SET status = 1,
                current_attempt_id = #{req.attemptId},
                judging_lease_expires_at = DATE_ADD(NOW(), INTERVAL #{req.ttlSeconds} SECOND)
            WHERE id = #{req.id} AND status = 0 AND generation = #{req.generation} AND is_delete = 0
            """)
    int acquireLease(@Param("req") SubmissionFenceRequest req);

    /**
     * ② 心跳续租：仅按 attemptId 续租（attempt 全局唯一）。
     *
     * <p>返回 0=已丢租约（被 reaper 回收重派），worker 应丢弃在途结果。</p>
     */
    @Update("""
            UPDATE submission
            SET judging_lease_expires_at = DATE_ADD(NOW(), INTERVAL #{req.ttlSeconds} SECOND)
            WHERE id = #{req.id} AND current_attempt_id = #{req.attemptId} AND is_delete = 0
            """)
    int renewLease(@Param("req") SubmissionFenceRequest req);

    /**
     * ③ 写回判题结论（SUCCEED / FAILED）：CAS 必须同时匹配 generation + attemptId。
     *
     * <p>返回 0=结果已 stale（被 reaper 回收重派），丢弃不写。</p>
     */
    @Update("""
            UPDATE submission
            SET status = #{req.status},
                verdict = #{req.verdict},
                judge_info = #{req.judgeInfo},
                current_attempt_id = NULL,
                judging_lease_expires_at = NULL
            WHERE id = #{req.id} AND generation = #{req.generation} AND current_attempt_id = #{req.attemptId} AND is_delete = 0
            """)
    int writeVerdictFenced(@Param("req") SubmissionVerdictRequest req);

    /**
     * ④ reaper 回收僵尸：把过期 RUNNING 复位为 WAITING + generation+1。
     *
     * <p>CAS 必须带 {@code expectedGen}：另一 reaper 实例已回收（代次已动）时本实例跳过 —— 多实例安全。</p>
     */
    @Update("""
            UPDATE submission
            SET status = 0,
                generation = #{newGen},
                current_attempt_id = NULL,
                judging_lease_expires_at = NULL
            WHERE id = #{id} AND status = 1 AND generation = #{expectedGen}
              AND judging_lease_expires_at IS NOT NULL AND is_delete = 0
            """)
    int bumpGenerationAndReset(@Param("id") long id,
                               @Param("expectedGen") long expectedGen,
                               @Param("newGen") long newGen);

    /**
     * ⑤ reaper 捞过期租约（主回收路径）：过期 RUNNING + 有租约。
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} 保证多 reaper 实例不抢同一批行。</p>
     */
    List<Submission> selectExpiredJudgingForUpdate(@Param("batchSize") int batchSize);

    /**
     * 二级安全网：捞「无租约且闲置过久」的卡住行 —— 覆盖「首次分发 / 重派事件丢失」。
     *
     * <p>两类：WAITING(0) 一直未被消费；或 RUNNING(1) 但租约从未写入（M1 之前遗留的僵尸）。
     * 返回后由 reaper 复位为 WAITING 再重派；因 acquireLease 是 CAS，重复分发无害。</p>
     */
    List<Submission> selectStuckWithoutLease(@Param("batchSize") int batchSize,
                                             @Param("idleSeconds") int idleSeconds);

    /**
     * 二级安全网复位：把卡住行原子地复位为 WAITING + generation+1。
     *
     * <p>CAS 带 {@code expectedGen}：另一 reaper 已复位（代次已动）时本实例跳过。</p>
     */
    @Update("""
            UPDATE submission
            SET status = 0,
                generation = generation + 1,
                current_attempt_id = NULL,
                judging_lease_expires_at = NULL
            WHERE id = #{id} AND generation = #{expectedGen}
              AND (status = 0 OR (status = 1 AND judging_lease_expires_at IS NULL)) AND is_delete = 0
            """)
    int resetStuckToWaiting(@Param("id") long id, @Param("expectedGen") long expectedGen);

    // endregion

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
    List<com.xly.codeforge.model.dto.SubmissionStatsItemDTO> selectStatsByUserIds(
            @Param("userIds") List<Long> userIds);

    /**
     * 批量统计若干题目的提交数与通过数
     *
     * <p>用于题库列表 / 题目详情的通过率展示：原先读的是 {@code question.submit_num} /
     * {@code accepted_num} 两个列，但它们运行期没有任何更新点（恒为 0），
     * 现改为读取时从本表实时聚合。</p>
     *
     * <p><b>一次 SQL 覆盖整页题目</b>，返回结果只包含有提交记录的题目 ——
     * 没有任何提交的题在结果里根本不出现，调用方必须给缺行补 0。</p>
     *
     * <p>{@code acceptedCount} 用 {@code COUNT(CASE WHEN ... THEN 1 END)} 而非
     * {@code SUM(CASE ... )}：{@code SUM} 在 MySQL 里返回 DECIMAL，多一层到 Long 的隐式转换。</p>
     *
     * @param questionIds 题目 id 列表（不可为空）
     * @return 每行：questionId / submitCount / acceptedCount
     */
    List<com.xly.codeforge.model.dto.QuestionSubmissionStatsDTO> selectStatsByQuestionIds(
            @Param("questionIds") List<Long> questionIds);

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
            FROM submission
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
            FROM submission
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
