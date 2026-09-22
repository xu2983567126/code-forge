-- ============================================================
-- 修复题库判题用例的 wire key：output → expectedOutput
-- 目标库：myoj
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/2026-09-21-fix-judge-case-wire-key.sql
--
-- 背景（为什么必须回填）：
--   Java 侧 JudgeCase 的字段是 input + expectedOutput，而存量 judge_case 的 JSON key 是 output。
--   Hutool 反序列化对未知 key **静默忽略** → expectedOutput 恒为 null → 正式判题把每个用例
--   都当成「没有标准答案」，落进中性态（逐用例 status=null、聚合 message=null），最终
--   JudgeServiceImpl 把空 message 兜底成 SYSTEM_ERROR，产生
--   「status=2（成功）+ verdict=SYSTEM_ERROR」的自相矛盾记录，而前端只看到「未知」。
--
-- 影响面：只涉及 question.judge_case（json 数组，元素形如 {"input": "...", "output": "..."}）。
--   judge_config 不受影响（其 key 一直是 timeLimit/memoryLimit/compareMode）。
--
-- 幂等：REPLACE 只命中 `"output":` 这一子串，重复执行无副作用；末尾校验语句应返回 0。
-- ============================================================

set names utf8mb4;
use myoj;

-- 0) 备份（可回滚；已存在则不覆盖，保留首次备份）
CREATE TABLE IF NOT EXISTS question_bak_20260921 AS SELECT id, judge_case FROM question;

-- 1) 回填 wire key
UPDATE question
   SET judge_case = REPLACE(judge_case, '"output":', '"expectedOutput":')
 WHERE judge_case LIKE '%"output":%';

-- 2) 校验：残留旧 key 应为 0
SELECT COUNT(*) AS remain_old_key_is_0 FROM question WHERE judge_case LIKE '%"output":%';

-- 3) 校验：每道有用例的题都应能解析出 expectedOutput
SELECT COUNT(*) AS total_questions,
       SUM(judge_case LIKE '%"expectedOutput":%') AS fixed_questions
  FROM question
 WHERE judge_case IS NOT NULL AND judge_case <> '';

-- ============================================================
-- 回滚（如需）：
--   UPDATE question q JOIN question_bak_20260921 b ON q.id = b.id SET q.judge_case = b.judge_case;
-- ============================================================
