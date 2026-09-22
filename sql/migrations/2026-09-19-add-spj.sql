-- M4：题目加特判（SPJ）字段
-- 可逆：DROP COLUMN spj_code; DROP COLUMN spj_language;
-- 幂等：依赖 1060/1061 容错（重复执行报错即忽略）

ALTER TABLE question
  ADD COLUMN spj_code     TEXT        COMMENT '特判程序源码（compareMode=SPJ 时由沙箱执行）',
  ADD COLUMN spj_language VARCHAR(40) COMMENT '特判程序语言，对齐 submission.language 取值';
