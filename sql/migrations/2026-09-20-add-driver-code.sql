-- M7-3：题目加判题驱动代码（核心代码模式）
-- 可逆：DROP COLUMN driver_code;
-- 幂等：依赖 1060/1061 容错（重复执行报错即忽略）

ALTER TABLE question
  ADD COLUMN driver_code TEXT COMMENT '判题驱动代码，含 public class Main；为空表示普通题';
