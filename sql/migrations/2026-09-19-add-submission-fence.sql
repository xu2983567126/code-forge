-- M1 判题并发 fencing：submission 加 generation / current_attempt_id / judging_lease_expires_at 三列 + 索引
--
-- 存量行靠 DEFAULT 回填：generation=1，current_attempt_id=NULL，judging_lease_expires_at=NULL。
-- 本脚本幂等：重复执行时若列已存在，MySQL 会报 Duplicate column 错误，属预期（说明已应用过）。
--
-- 设计来源：对齐 UltiCode DefaultJudgeAttemptExecutor + JudgingLeaseReaper + SubmissionMapper 的生产写法。
-- 三列用途：
--   generation              代次号，每次抢占/回收 +1，与 current_attempt_id 组成双轴 fencing
--   current_attempt_id      本次判题 worker 的 UUID（全局唯一）
--   judging_lease_expires_at 租约过期时间（DB 时钟），reaper 据此回收僵尸；NULL 表示无租约（WAITING/终态）
-- 索引 idx_lease_expiry 供 reaper 批量扫过期租约走索引，避免全表扫。

ALTER TABLE submission
    ADD COLUMN generation BIGINT NOT NULL DEFAULT 1 COMMENT '判题代次号：每次抢占/回收 +1，用于 fencing',
    ADD COLUMN current_attempt_id VARCHAR(40) DEFAULT NULL COMMENT '本次判题 worker 的 UUID',
    ADD COLUMN judging_lease_expires_at DATETIME(3) DEFAULT NULL COMMENT '判题租约过期时间（DB 时钟）',
    ADD KEY idx_lease_expiry (status, judging_lease_expires_at);
