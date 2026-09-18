-- ============================================================
-- 迁移：去掉 user 表列的冗余「同名前缀」
-- 日期：2026-09-18
-- 适用：**已建库且列名仍为 user_avatar / user_profile 的库**。
--       新库直接用 sql/create_table.sql 建表即可，本文件无需执行。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p myoj < sql/migrations/2026-09-18-drop-redundant-user-prefix.sql
--       （脚本内部已带 `use myoj;`，也可不带库名执行）
-- ============================================================
--
-- 背景
--   user 表的 user_avatar / user_profile 里，`user_` 前缀与表名重复，属冗余：
--   在 user 表内 avatar / profile 已足够无歧义。
--
-- ⚠️ 只改「与表名同名的前缀列」。**外键引用前缀一律保留**：
--   user_id / question_id / post_id / bank_id / question_bank_id / source_bank_id
--   等（这些前缀用于指明「引用的是哪张表」，不是冗余，删了会产生歧义）。
--   全库实测：只有 user 表存在同名前缀列（`COLUMN_NAME LIKE 表名%` 扫描，10 张表命中 2 列）。
--
-- ⚠️ 配套 Java 改动（必须同一提交一起生效，否则应用起不来）：
--   · entity/User、vo/LoginUserVO、vo/UserVO、4 个 User*Request DTO：字段 userAvatar → avatar、
--     userProfile → profile（map-underscore-to-camel-case: true 下，列 avatar ↔ 字段 avatar）
--   · UserServiceImpl 里 QueryWrapper 列名 "user_profile" → "profile"（列名必须同步）
--   · 前端 JSON key 随之由 userAvatar/userProfile → avatar/profile
--     （生成 SDK 需重跑 `npm run generate:api:force`；手写代码实测 0 处引用）
--
-- ⚠️ 幂等性：本文件**可安全重复执行**——内部用 information_schema 守卫，
--   若列已改名则跳过（不会报 ERROR 1054）。
--   执行前仍建议备份：mysqldump -uroot -p myoj > myoj-before-prefix-drop.sql
-- ============================================================

set names utf8mb4;

use myoj;

-- 1) user_avatar → avatar（列定义照抄自 information_schema：varchar(1024) NULL）
set @has_old := (
    select count(*) from information_schema.COLUMNS
    where TABLE_SCHEMA = 'myoj' and TABLE_NAME = 'user' and COLUMN_NAME = 'user_avatar'
);
set @sql := if(@has_old > 0,
    'alter table `user` change column `user_avatar` `avatar` varchar(1024) null default null comment ''用户头像''',
    'select ''user_avatar 已改名，跳过'' as note'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2) user_profile → profile（列定义照抄自 information_schema：varchar(512) NULL）
set @has_old := (
    select count(*) from information_schema.COLUMNS
    where TABLE_SCHEMA = 'myoj' and TABLE_NAME = 'user' and COLUMN_NAME = 'user_profile'
);
set @sql := if(@has_old > 0,
    'alter table `user` change column `user_profile` `profile` varchar(512) null default null comment ''用户简介''',
    'select ''user_profile 已改名，跳过'' as note'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;


-- ============================================================
-- 执行后自检（应返回 0 行；若有输出说明残留同名前缀列）
--   SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA='myoj'
--     AND (COLUMN_NAME LIKE CONCAT(TABLE_NAME,'\_%') OR COLUMN_NAME LIKE CONCAT(TABLE_NAME,'%'));
-- 预期 user 表只剩 `username`（它是整词「用户名/昵称」，非前缀，2026-09-18 决定不改）。
-- ============================================================
