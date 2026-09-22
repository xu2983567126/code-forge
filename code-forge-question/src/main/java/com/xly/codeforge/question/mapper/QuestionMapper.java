package com.xly.codeforge.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xly.codeforge.model.entity.Question;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author user
* @description 针对表【question(题目)】的数据库操作Mapper
* @createDate 2026-04-11 17:55:37
* @Entity com.xly.myoj.model.entity.Question
*/
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 随机取一道未删除的题目的 id。
     * <p>
     * 参考 pandora 的做法，用 {@code ORDER BY RAND()} 由数据库排序后取一行。
     * 当前题量很小（十几道），全表排序开销可忽略；数据量上千后再换
     * 「按自增 id 区间随机 + 回退查询」的方案，不要提前优化。
     * <p>
     * 注意：{@code is_delete = 0} 必须显式写 —— 原生 SQL 不经过 MyBatis-Plus 的
     * {@code @TableLogic} 过滤，漏了会把已删除的题返回给前端。
     * <p>
     * 这里写的是**数据库列名**，故用下划线风格（DB 全表 snake_case）。
     *
     * @param excludeId 需要排除的题目 id（通常是当前正在浏览的题），可为 null
     * @return 随机题目的 id；题库为空时返回 null
     */
    Long selectRandomQuestionId(@Param("excludeId") Long excludeId);

    /**
     * 查询指定题目之后（id 更大）的第一道题。
     * <p>
     * 用于题目详情页的「下一题」。按 id 升序而不是按创建时间，
     * 因为 id 是自增主键，几乎无排序开销且结果稳定；创建时间可能重复导致翻页跳动。
     *
     * @param id 当前题目 id
     * @return 下一题的 id；已是最后一题时返回 null
     */
    @Select("""
            SELECT id FROM question
            WHERE is_delete = 0 AND id > #{id}
            ORDER BY id ASC
            LIMIT 1
            """)
    Long selectNextQuestionId(@Param("id") Long id);

    /**
     * 查询指定题目之前（id 更小）的最后一道题。
     * <p>
     * 用于题目详情页的「上一题」。id 降序 + LIMIT 1 取到的是「比当前小的最大值」，
     * 即紧邻的前一道题。
     * <p>
     * ⚠️ 注意：{@code @Select} 注解里的 SQL 是**纯字符串**，不能写 XML 实体
     * （写成 {@code &lt;} 会被原样发给 MySQL，报 {@code You have an error in your SQL syntax}）。
     * 只有 XML Mapper 文件里才需要转义。
     *
     * @param id 当前题目 id
     * @return 上一题的 id；已是第一题时返回 null
     */
    @Select("""
            SELECT id FROM question
            WHERE is_delete = 0 AND id < #{id}
            ORDER BY id DESC
            LIMIT 1
            """)
    Long selectPrevQuestionId(@Param("id") Long id);

    /**
     * 按难度统计题目数（供 dashboard）
     *
     * <p>返回 {@code [difficulty, count]} 两列。难度为 NULL 的记录<b>也会出现</b>
     * （key 为 null），调用方需兜底成「未分类」—— 否则各分组之和会小于总数。
     * 这是 {{@code GROUP BY} 的固有行为：NULL 自成一组。</p>
     *
     * @return 每行：difficulty / cnt
     */
    @Select("""
            SELECT difficulty AS bucket, COUNT(*) AS cnt
            FROM question
            WHERE is_delete = 0
            GROUP BY difficulty
            """)
    List<BucketCount> countByDifficulty();

    /**
     * 按标签统计题目数（供 dashboard）
     *
     * <p>{@code tags} 列存的是 JSON 数组字符串（如 {@code ["数组","哈希表"]}），
     * 用 MySQL 的 {@code JSON_TABLE} 把它展开成多行再聚合。</p>
     *
     * <p><b>为什么不用 {@code LIKE '%"标签"%'} 循环匹配</b>：那样代码里要先查出所有
     * 不同标签、再对每个标签发一条 count，是典型的 N+1。这里一条 SQL 出结果。</p>
     *
     * <p>⚠️ 要求 MySQL 8.0+（{@code JSON_TABLE} 在 8.0.4 引入）。本机是 8.x，可用。
     * 若将来降级到 5.7，需改为应用层解析。</p>
     *
     * @return 每行：标签 / cnt，按数量降序
     */
    @Select("""
            SELECT jt.tag AS bucket, COUNT(*) AS cnt
            FROM question q,
                 JSON_TABLE(q.tags, '$[*]' COLUMNS (tag VARCHAR(128) PATH '$')) jt
            WHERE q.is_delete = 0
              AND q.tags IS NOT NULL
              AND JSON_VALID(q.tags)
            GROUP BY jt.tag
            ORDER BY cnt DESC
            """)
    List<BucketCount> countByTag();

    /**
     * 通用「分组计数」结果载体
     *
     * <p>列别名固定为 {@code bucket} / {@code cnt}，让多条统计 SQL 共用同一个 record
     * —— 否则每个统计都要定义一个 record，mapper 会被撑爆。</p>
     *
     * <p>{@code bucket} 用 String：难度是中文、标签也是字符串；数量统计没有用 id 做分组的需求。</p>
     */
    record BucketCount(String bucket, Long cnt) {
    }

}
