-- 集成测试专用：仅含 submission 服务测试所需的最小表结构。
-- 与生产 DDL（sql/create_table.sql）保持列一致；新增列时两处都要改。
CREATE TABLE `submission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `language` varchar(128) NOT NULL COMMENT '编程语言',
  `code` text NOT NULL COMMENT '用户代码',
  `judge_info` text COMMENT '判题信息（json 对象）',
  `status` int NOT NULL DEFAULT '0' COMMENT '判题状态（0 - 待判题、1 - 判题中、2 - 成功、3 - 失败）',
  `verdict` varchar(50) DEFAULT NULL COMMENT '判题结果：ACCEPTED/WRONG_ANSWER/...（status=2 时有意义）',
  `generation` bigint NOT NULL DEFAULT '1' COMMENT '判题代次号：每次抢占/回收 +1，用于 fencing',
  `current_attempt_id` varchar(40) DEFAULT NULL COMMENT '本次判题 worker 的 UUID',
  `judging_lease_expires_at` datetime(3) DEFAULT NULL COMMENT '判题租约过期时间（DB 时钟）',
  `question_id` bigint NOT NULL COMMENT '题目 id',
  `user_id` bigint NOT NULL COMMENT '创建用户 id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status_verdict` (`status`,`verdict`),
  KEY `idx_lease_expiry` (`status`,`judging_lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目提交';
