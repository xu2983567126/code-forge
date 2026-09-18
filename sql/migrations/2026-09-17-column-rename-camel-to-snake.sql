-- ============================================================
-- 迁移：列名规范化 camelCase → snake_case
-- 日期：2026-09-17
-- 适用：**只对「已建库且仍是 camelCase 列名」的旧库需要执行**。
--       新库直接用 sql/create_table.sql 建表即可，本文件无需执行。
-- 执行：mysql -uroot -p myoj < sql/migrations/2026-09-17-column-rename-camel-to-snake.sql
--       （脚本内部已带 `use myoj;`，也可不带库名执行：mysql -uroot -p < ...）
-- ============================================================
--
-- ⚠️ 先读这段，再执行
-- 背景
--   原工程（鱼皮 OJ → code-forge-new）的 DDL 用 camelCase 列名
--   （userId / createTime / isDelete ...），配合 yaml 里
--   `map-underscore-to-camel-case: false` 实现「Java 字段名即列名」。
--
-- 改为 snake_case 的理由
--   ① 符合 SQL / MySQL 事实标准，去掉大小写敏感与反引号依赖；
--   ② 与参考项目 UltiCode 一致（其 DDL 即 snake_case + 该开关设为 true）；
--   ③ 跨库迁移（PostgreSQL 等）时无需重命名。
--
-- ⚠️ 关键：Java 侧字段名**一个字都不改**（仍写 private String userId）。
--    把 3 个服务的 yaml 里 `map-underscore-to-camel-case` 由 false 改成 true，
--    MyBatis-Plus 会自动完成 `user_id` ↔ `userId` 的映射。
--    因此 VO / DTO / 前端 JSON（createTime 等）全部保持不变。
--
-- ⚠️ 保留业务前缀！`user.user_id` 不简化成 `user.id`：
--    user 表主键已叫 id，外键再叫 id 会在 join 时产生歧义；
--    UltiCode 同样保留（其 user_id 出现 196 次）。
--
-- 生成方式
--    读 information_schema 拼出完整列定义，已用 myoj_dryrun 库试跑验证
--    —— 类型/NULL/DEFAULT/EXTRA/COMMENT 零丢失，仅列名变化。
--
-- ⚠️ 幂等性：本文件**不可重复执行**（列已改名后再跑会报 ERROR 1054）。
--    执行前务必先备份：mysqldump -uroot -p myoj > myoj-before-rename.sql
--
-- 影响面：9 张表 / 53 列 / 13 个索引；数据量极小（最多 29 行），无性能顾虑。
-- 配套改动（同一提交内必须一起生效，否则应用起不来）：
--    · 3 个服务 application.yaml：map-underscore-to-camel-case: false → true
--    · 7 处 Java 字符串（QueryWrapper 里的列名）
--    · 3 处 QuestionMapper 裸 SQL 的 `isDelete` → `is_delete`
--    · 1 处 SqlUtils.validSortField 改白名单（配合 snake_case 排序字段）
--  详见 docs/列名规范化方案-camelCase转snake_case.md
-- ============================================================

-- ⚠️ 字符集与库名声明（必须在最前面）
--   ① set names utf8mb4：本文件含中文注释，Windows 中文系统的 mysql.exe
--      客户端默认 character_set_client=gbk，会把 UTF-8 中文按 GBK 解读，
--      写进 utf8mb4 列时变成非法字节并报 1067/1366。
--   ② use myoj：ALTER TABLE 必须指定库，否则报 1046 No database selected。
set names utf8mb4;
use myoj;


-- ---------- post（6 列） ----------
ALTER TABLE `post`
  CHANGE COLUMN `thumbNum` `thumb_num` int NOT NULL DEFAULT 0 COMMENT '点赞数',
  CHANGE COLUMN `favourNum` `favour_num` int NOT NULL DEFAULT 0 COMMENT '收藏数',
  CHANGE COLUMN `userId` `user_id` bigint NOT NULL COMMENT '创建用户 id',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CHANGE COLUMN `isDelete` `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除';

ALTER TABLE `post` RENAME INDEX `idx_userId` TO `idx_user_id`;


-- ---------- post_favour（4 列） ----------
ALTER TABLE `post_favour`
  CHANGE COLUMN `postId` `post_id` bigint NOT NULL COMMENT '帖子 id',
  CHANGE COLUMN `userId` `user_id` bigint NOT NULL COMMENT '创建用户 id',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `post_favour` RENAME INDEX `idx_postId` TO `idx_post_id`;
ALTER TABLE `post_favour` RENAME INDEX `idx_userId` TO `idx_user_id`;


-- ---------- post_thumb（4 列） ----------
ALTER TABLE `post_thumb`
  CHANGE COLUMN `postId` `post_id` bigint NOT NULL COMMENT '帖子 id',
  CHANGE COLUMN `userId` `user_id` bigint NOT NULL COMMENT '创建用户 id',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `post_thumb` RENAME INDEX `idx_postId` TO `idx_post_id`;
ALTER TABLE `post_thumb` RENAME INDEX `idx_userId` TO `idx_user_id`;


-- ---------- question（10 列） ----------
ALTER TABLE `question`
  CHANGE COLUMN `submitNum` `submit_num` int NOT NULL DEFAULT 0 COMMENT '题目提交数',
  CHANGE COLUMN `acceptedNum` `accepted_num` int NOT NULL DEFAULT 0 COMMENT '题目通过数',
  CHANGE COLUMN `judgeCase` `judge_case` text NULL DEFAULT NULL COMMENT '判题用例（json 数组）',
  CHANGE COLUMN `judgeConfig` `judge_config` text NULL DEFAULT NULL COMMENT '判题配置（json 对象）',
  CHANGE COLUMN `thumbNum` `thumb_num` int NOT NULL DEFAULT 0 COMMENT '点赞数',
  CHANGE COLUMN `favourNum` `favour_num` int NOT NULL DEFAULT 0 COMMENT '收藏数',
  CHANGE COLUMN `userId` `user_id` bigint NOT NULL COMMENT '创建用户 id',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CHANGE COLUMN `isDelete` `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除';

ALTER TABLE `question` RENAME INDEX `idx_userId` TO `idx_user_id`;


-- ---------- question_bank（7 列） ----------
ALTER TABLE `question_bank`
  CHANGE COLUMN `userId` `user_id` bigint NOT NULL COMMENT '创建用户 id',
  CHANGE COLUMN `isPublic` `is_public` tinyint NOT NULL DEFAULT 1 COMMENT '是否公开：0-私有 1-公开',
  CHANGE COLUMN `sourceBankId` `source_bank_id` bigint NULL DEFAULT NULL COMMENT 'fork 来源题单 id（NULL = 原创）',
  CHANGE COLUMN `forkNum` `fork_num` int NOT NULL DEFAULT 0 COMMENT '被 fork 次数',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CHANGE COLUMN `isDelete` `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除';

ALTER TABLE `question_bank` RENAME INDEX `idx_isPublic` TO `idx_is_public`;
ALTER TABLE `question_bank` RENAME INDEX `idx_userId` TO `idx_user_id`;


-- ---------- question_bank_question（5 列） ----------
ALTER TABLE `question_bank_question`
  CHANGE COLUMN `questionBankId` `question_bank_id` bigint NOT NULL COMMENT '题单 id',
  CHANGE COLUMN `questionId` `question_id` bigint NOT NULL COMMENT '题目 id',
  CHANGE COLUMN `userId` `user_id` bigint NOT NULL COMMENT '操作（添加）用户 id',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `question_bank_question` RENAME INDEX `idx_questionId` TO `idx_question_id`;


-- ---------- question_favourite（4 列） ----------
ALTER TABLE `question_favourite`
  CHANGE COLUMN `questionId` `question_id` bigint NOT NULL COMMENT '题目 id',
  CHANGE COLUMN `userId` `user_id` bigint NOT NULL COMMENT '创建用户 id',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `question_favourite` RENAME INDEX `idx_userId` TO `idx_user_id`;


-- ---------- question_submit（6 列） ----------
ALTER TABLE `question_submit`
  CHANGE COLUMN `judgeInfo` `judge_info` text NULL DEFAULT NULL COMMENT '判题信息（json 对象）',
  CHANGE COLUMN `questionId` `question_id` bigint NOT NULL COMMENT '题目 id',
  CHANGE COLUMN `userId` `user_id` bigint NOT NULL COMMENT '创建用户 id',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CHANGE COLUMN `isDelete` `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除';

ALTER TABLE `question_submit` RENAME INDEX `idx_questionId` TO `idx_question_id`;
ALTER TABLE `question_submit` RENAME INDEX `idx_userId` TO `idx_user_id`;


-- ---------- user（7 列） ----------
-- 注意：`user.createTime` 等 6 列改名，`account` / `password` / `username` / `role` 不动。
--       同表内冗余前缀已去掉：userAvatar → user_avatar、userProfile → user_profile。
ALTER TABLE `user`
  CHANGE COLUMN `unionId` `union_id` varchar(256) NULL DEFAULT NULL COMMENT '微信开放平台id',
  CHANGE COLUMN `mpOpenId` `mp_open_id` varchar(256) NULL DEFAULT NULL COMMENT '公众号openId',
  CHANGE COLUMN `userAvatar` `user_avatar` varchar(1024) NULL DEFAULT NULL COMMENT '用户头像',
  CHANGE COLUMN `userProfile` `user_profile` varchar(512) NULL DEFAULT NULL COMMENT '用户简介',
  CHANGE COLUMN `createTime` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updateTime` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CHANGE COLUMN `isDelete` `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除';

ALTER TABLE `user` RENAME INDEX `idx_unionId` TO `idx_union_id`;


-- ============================================================
-- 执行后自检（以下应全部返回 0）
--
-- ① 残留驼峰列（必须为 0）：
--    SELECT COUNT(*) FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA='myoj'
--      AND BINARY COLUMN_NAME <> BINARY LOWER(COLUMN_NAME);
--
--    ⚠️ 两个坑，都实测踩过：
--    · 不要写 `REGEXP '[A-Z]'` —— information_schema 的排序规则是 ci
--      （大小写不敏感），该正则会匹配到全小写列（实测误报 81 = 总列数）。
--    · 不要写 `CONVERT(COLUMN_NAME USING utf8mb4) <> LOWER(COLUMN_NAME)`
--      —— 在 MySQL 8.4 上实测对 `submitNum` vs `submitnum` 返回「相等」，
--      即恒为 0，是个**假阴性**（漏报）。本次就该写法的失效做过对照实验。
--    · 也不能给 utf8mb3 的列加 `COLLATE utf8mb4_bin`（会报排序规则不兼容）。
--
--    正解：两侧都套 `BINARY`，强制按字节比较，与排序规则彻底解耦。
--
-- ② 残留驼峰索引：同法查 information_schema.STATISTICS 的 INDEX_NAME。
--    SELECT COUNT(*) FROM information_schema.STATISTICS
--    WHERE TABLE_SCHEMA='myoj'
--      AND BINARY INDEX_NAME <> BINARY LOWER(INDEX_NAME);
--    注意：`PRIMARY` 会被算进去（它天生大写），
--    预期残留数 = 表数量（9），且必须**全部是 PRIMARY**。
--    自查：SELECT DISTINCT TABLE_NAME, INDEX_NAME FROM information_schema.STATISTICS
--          WHERE TABLE_SCHEMA='myoj' AND BINARY INDEX_NAME <> BINARY LOWER(INDEX_NAME);
--
-- ③ 属性未丢：SHOW CREATE TABLE `user`;
--    对照备份确认 DEFAULT / COMMENT / ON UPDATE 仍在。
--    尤其核对 question.difficulty 的默认值仍是中文「简单」。
--
-- ④ 应用层回归：
--    uv run scripts/e2e-test.py --gateway http://127.0.0.1:8101   期望 10/10
--    uv run python scripts/verify-sortfield.py
--        期望 snake_case 排序字段（create_time）可用 → code=0
--             驼峰排序字段（createTime）→ 报 50000，反证列已改名
--             注入串（id;drop）→ 被白名单挡下，不拼进 SQL
-- ============================================================
