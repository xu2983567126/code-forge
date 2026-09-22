-- ============================================================
-- OJ 微服务版 —— 迁移索引（历史变更记录）
-- 目标库：myoj
--
-- ⚠️ 本文件**不再承载可执行语句**，只记录「改过什么、为什么改」。
--
-- 建库 / 建表请用：
--     mysql -uroot -p myoj < sql/create_table.sql
--   （该文件是全量基线，已包含下列全部历史变更的最终状态）
--
-- 旧库原地升级请按 sql/migrations/ 下的文件逐个执行。
-- ============================================================


-- ------------------------------------------------------------
-- 2026-09-17  列名规范化：camelCase → snake_case
--   迁移脚本：sql/migrations/2026-09-17-column-rename-camel-to-snake.sql
--   状态：✅ 已并入 create_table.sql 基线
--
--   改动：9 张表 / 53 列 / 13 个索引改下划线命名。
--   配套：3 个服务 yaml 的 map-underscore-to-camel-case 由 false 改 true；
--         7 处 QueryWrapper 列名字符串；3 处 QuestionMapper 裸 SQL；
--         SqlUtils.validSortField 改白名单（原黑名单只拦 4 个字符）。
--   保留：业务前缀（user.user_id 不简化成 id）。
--   详见：docs/列名规范化方案-camelCase转snake_case.md
-- ------------------------------------------------------------


-- ------------------------------------------------------------
-- 2026-09-17  新页面支撑（7 个新页面）
--   状态：✅ 已并入 create_table.sql 基线
--   对应文档：docs/页面与后端实现规划-7个新页面.md
--
--   改动：
--     · question.difficulty            加列（难度：简单/中等/困难，默认 '简单'）
--     · submission.verdict        加列（判题结果 ACCEPTED/WRONG_ANSWER/...）
--     · submission.idx_status_verdict   加联合索引（按 verdict 统计/筛选）
--     · 建表 question_bank             题单主表
--     · 建表 question_bank_question    题单-题目关联（硬删除，UNIQUE(bank,question)）
--     · 建表 question_favourite        题目收藏（硬删除，UNIQUE(question,user)）
--
--   说明：difficulty 与 verdict 作为新列直接写入基线，新库建表即有，无需 ALTER。
--         存量库升级时若已建过这些列，重复执行 ALTER 会报 1060，
--         可按需手工跳过。
-- ------------------------------------------------------------


-- ------------------------------------------------------------
-- 2026-09-15  user.account 唯一索引
--   状态：✅ 已并入 create_table.sql 基线（uk_account）
--
--   背景：原先靠 UserServiceImpl 里的 synchronized (account.intern()) 防重复注册，
--         该写法只锁得住单个 JVM，改多实例部署后形同虚设。
--         改由数据库唯一约束兜底（代码侧已改为捕获 DuplicateKeyException）。
--   注意：若表中已有重复账号，建索引会失败。自查：
--         SELECT account, COUNT(*) AS c FROM `user` GROUP BY account HAVING c > 1;
-- ------------------------------------------------------------


-- ------------------------------------------------------------
-- 2026-09-15  逻辑删除字段对齐
--   状态：✅ 已并入 create_table.sql 基线（两表均有 is_delete，默认 0）
--
--   背景：Submission 实体补 @TableLogic，与 Question 行为对齐，
--         使 MyBatis-Plus 在原生条件构造器里自动附加逻辑删除过滤。
-- ------------------------------------------------------------


-- ============================================================
-- 目录约定
--   sql/create_table.sql          全量基线，唯一建库入口（幂等，可重复执行）
--   sql/migrations/*.sql          按日期命名的原地升级脚本（通常不可重复执行）
--   sql/upgrade.sql               本文件：迁移索引，只记录不改库
-- ============================================================
