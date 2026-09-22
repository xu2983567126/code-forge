-- ============================================================
-- 废弃 question.submit_num / accepted_num 两列
-- 目标库：myoj
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/2026-09-21-deprecate-question-count-columns.sql
--
-- 背景：这两列原本是「题目提交数 / 通过数」的冗余计数，但全仓 grep 确认**运行期没有任何
--   更新点** —— 只有造数脚本 setup-db.py 的 INSERT 写死 0。真正有数据的是 submission 表。
--   前端（题库列表 / 管理列表）却按这两列算通过率，结果恒显示 0%。
--
-- 处置：统计改由 submission 表在读取时实时聚合
--   （POST /api/submission/inner/question/stats → SubmissionFeignClient.listStatsByQuestionIds，
--    由 QuestionServiceImpl#fillSubmissionStats 回填 VO）。
--   本迁移只把列注释标为废弃，**不 DROP** —— 删列不可逆，留给确认后单独执行（见文末）。
-- ============================================================

set names utf8mb4;
use myoj;

ALTER TABLE question
    MODIFY COLUMN `submit_num` int NOT NULL DEFAULT 0
        COMMENT '【已废弃】题目提交数：运行期无更新点，改由 submission 表读取时实时聚合',
    MODIFY COLUMN `accepted_num` int NOT NULL DEFAULT 0
        COMMENT '【已废弃】题目通过数：运行期无更新点，改由 submission 表读取时实时聚合';

-- 校验：两列注释应带「已废弃」
SELECT COLUMN_NAME, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'myoj' AND TABLE_NAME = 'question'
   AND COLUMN_NAME IN ('submit_num', 'accepted_num');

-- ============================================================
-- 确认统计稳定后（可选，不可逆）：删列
--   注意同时要删 Question 实体的两个字段与 create_table.sql 里的列定义，
--   否则 MyBatis-Plus 生成的 SQL 会引用不存在的列。
--   ALTER TABLE question DROP COLUMN submit_num, DROP COLUMN accepted_num;
-- ============================================================
