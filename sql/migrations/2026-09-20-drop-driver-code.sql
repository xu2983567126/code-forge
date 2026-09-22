-- 舍弃 driver_code：核心代码模式驱动改为运行时由 code_template 派生，
-- 不再存储易失同步的副本（详见 docs/M7-3-代码模板-细化方案.md）。
--
-- ⚠️ 本脚本是历史记录，**不要在新库上照抄执行**：下面的 judge_case 用的是旧 wire key `output`，
--    后来 Java 侧 JudgeCase 字段改名为 expectedOutput，跑完必须再执行
--    `2026-09-21-fix-judge-case-wire-key.sql` 补正 key，否则判题会落进中性态。
--    脚本正文保持当时口径，不再回改。
--
-- 同步把 5 道 C 模式题的 judge_case 改为新驱动口径：
--   - 旧驱动用 Scanner 按空格分词读 stdin；新驱动读整段 stdin 当 JSON 数组。
--   - 输出旧驱动各自打印；新驱动统一用 fmt()：数组->[..]、值->原值。

ALTER TABLE question DROP COLUMN driver_code;

UPDATE question SET judge_case = '[{"input": "[\\"abc\\"]", "output": "cba"}, {"input": "[\\"hello\\"]", "output": "olleh"}, {"input": "[\\"a\\"]", "output": "a"}]' WHERE title = '反转字符串';
UPDATE question SET judge_case = '[{"input": "[[-2,1,-3,4]]", "output": "4"}, {"input": "[[1,-2,3,4,-5]]", "output": "7"}, {"input": "[[-1,-2,-3]]", "output": "-1"}]' WHERE title = '最大子数组和';
UPDATE question SET judge_case = '[{"input": "[121]", "output": "true"}, {"input": "[-121]", "output": "false"}, {"input": "[10]", "output": "false"}]' WHERE title = '回文数';
UPDATE question SET judge_case = '[{"input": "[\\"()\\"]", "output": "true"}, {"input": "[\\"()[]{}\\"]", "output": "true"}, {"input": "[\\"(]\\"]", "output": "false"}, {"input": "[\\"([)]\\"]", "output": "false"}]' WHERE title = '有效的括号';
UPDATE question SET judge_case = '[{"input": "[[2,7,11,15],9]", "output": "[0,1]"}, {"input": "[[3,2,4],6]", "output": "[1,2]"}]' WHERE title = '两数之和';
