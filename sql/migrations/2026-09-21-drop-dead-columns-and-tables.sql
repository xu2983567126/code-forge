-- ============================================================
-- 清理：删除无维护的计数列 + 无对应代码的表 + 测试残留数据
-- 目标库：myoj
-- 执行：先备份，再执行
--   mysqldump -uroot -p --default-character-set=utf8mb4 myoj > myoj-backup-<date>.sql
--   mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/2026-09-21-drop-dead-columns-and-tables.sql
--
-- ⚠️ 本脚本含 DROP TABLE / DROP COLUMN / DELETE，**不可逆**。执行前必须已有备份。
-- ============================================================

set names utf8mb4;
use myoj;

-- ------------------------------------------------------------
-- 1) question 的两个计数列
--    运行期零更新点（只有造数脚本 INSERT 写死 0），统计已改为读取时从 submission 表实时聚合。
--    配套改动：Question 实体的这两个字段加了 @TableField(exist = false)，MP 不再引用本列。
-- ------------------------------------------------------------
ALTER TABLE question DROP COLUMN submit_num, DROP COLUMN accepted_num;

-- ------------------------------------------------------------
-- 2) 社区模块三表（post / post_thumb / post_favour）
--    来自鱼皮原版教程，本微服务项目没有对应的实体 / Service / Controller，表内 0 行。
--    DDL 也一并从 create_table.sql 基线移除。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS post_thumb;
DROP TABLE IF EXISTS post_favour;
DROP TABLE IF EXISTS post;

-- ------------------------------------------------------------
-- 3) judge_case wire key 回填的备份表
--    回填已完成并双向验证（运行态 GET /question/{id}/vo 返回 expectedOutput 有值；
--    生产提交 2102060207253352449 判出 ACCEPTED），备份使命结束。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS question_bak_20260921;

-- ------------------------------------------------------------
-- 4) 无效提交记录
--    2102055044069302273：期望输出断链期间落下的中性态残留 ——
--    judge_info 无 message、逐用例无 status、verdict 被兜底成 SYSTEM_ERROR 却 status=2。
--    同题同代码在修复后已由新提交 2102060207253352449 正常判为 ACCEPTED。
-- ------------------------------------------------------------
DELETE FROM submission WHERE id = 2102055044069302273;

-- ------------------------------------------------------------
-- 5) 测试残留题单与悬空关联
--    5.1 悬空关联：question_bank_question 指向的题目已不存在（多轮造数/清理留下的孤儿行）
--    5.2 自动化测试建的「验证题单 <时间戳>」
--    「入门算法题单」（真人建的）保留，疏理后为空题单。
-- ------------------------------------------------------------
DELETE FROM question_bank_question WHERE question_id NOT IN (SELECT id FROM question);
DELETE FROM question_bank_question
 WHERE question_bank_id IN (SELECT id FROM question_bank WHERE title LIKE '验证题单%');
DELETE FROM question_bank WHERE title LIKE '验证题单%';

-- ------------------------------------------------------------
-- 校验
-- ------------------------------------------------------------
SELECT 'question 列数（不应再有 submit_num/accepted_num）' AS chk;
SELECT COUNT(*) AS should_be_0 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='myoj' AND TABLE_NAME='question' AND COLUMN_NAME IN ('submit_num','accepted_num');
SELECT '剩余表' AS chk;
SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='myoj' ORDER BY TABLE_NAME;
SELECT '剩余数据量' AS chk;
SELECT (SELECT COUNT(*) FROM question) AS question_cnt,
       (SELECT COUNT(*) FROM submission) AS submission_cnt,
       (SELECT COUNT(*) FROM question_bank) AS bank_cnt,
       (SELECT COUNT(*) FROM question_bank_question) AS bank_q_cnt;
SELECT '提交结论分布' AS chk;
SELECT COALESCE(verdict,'NULL') AS verdict, COUNT(*) AS cnt FROM submission GROUP BY verdict;
