-- ============================================================
-- submission.judge_info 由 text 扩为 mediumtext
-- 目标库：myoj
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/2026-09-21-judge-info-mediumtext.sql
--
-- 背景：judge_info 里存的是 JudgeInfo JSON，其中 caseResults 逐用例回带了
--   input / expectedOutput / output（为解除「前端按序对齐」的顺序耦合而有意为之）。
--   沙箱单流输出上限是 1 MiB，而 text 只有 64 KB —— 大输出的提交会在写库这一步
--   直接报 1406（Data too long），且失败兜底 markFailed 也写同一列、同样失败，
--   记录会永远卡在 RUNNING 被 reaper 反复重判。
--
-- 双保险：本迁移把列扩到 16 MB，同时 VerdictResolver 在回写前对逐用例文本做
--   8192 字符限长（见 MAX_CASE_TEXT_LENGTH）—— 列宽是兜底，限长才是常态约束。
-- ============================================================

set names utf8mb4;
use myoj;

ALTER TABLE submission
    MODIFY COLUMN `judge_info` mediumtext NULL COMMENT '判题信息（json 对象）';

-- 校验：Type 应为 mediumtext
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'myoj' AND TABLE_NAME = 'submission' AND COLUMN_NAME = 'judge_info';

-- 回滚（如需）：ALTER TABLE submission MODIFY COLUMN `judge_info` text NULL COMMENT '判题信息（json 对象）';
