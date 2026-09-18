-- ============================================================
-- OJ 微服务版 —— 数据库全量建表脚本（基线）
-- 目标库：myoj
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/create_table.sql
--       （脚本首行已有 `set names utf8mb4;` 兜底，但命令行显式带上更稳妥）
--
-- 来源与沿革：
--   本文件原先只存在于老单体仓库 code-forge-new/sql/create_table.sql（4-11），
--   微服务项目一直靠 setup-db.py 里硬编码的外部绝对路径引用它。
--   2026-09-17 收编进本仓库，此后建库自包含，不再依赖外部目录。
--
-- 内容 = 老基线（6 张表）+ 以下两处合并：
--   ① upgrade.sql §1 的 `user.uk_account` 唯一索引 —— 直接并入基线
--   ② upgrade.sql §3 的 3 张题单表（question_bank / question_bank_question / question_favourite）
--      以及 question.difficulty、question_submit.verdict 两列 —— 直接并入基线
--
-- ⚠️ 列名风格（2026-09-17 变更）：
--   本基线已统一为 **snake_case**（原基线是 camelCase）。
--   配套要求：3 个服务的 yaml 必须设 `map-underscore-to-camel-case: true`，
--   使 `user_id` 自动映射到 Java 字段 `userId`。
--   Java 侧字段名 / VO / DTO / 前端 JSON **全部保持驼峰不变**。
--   历史迁移语句见 `upgrade.sql §5`（仅对旧库需要，新库无需执行）。
--
-- ⚠️ 外键保留业务前缀：`user_id` 不简化成 `id`（user 表主键已叫 id，外键再叫 id 会歧义）。
--   同表内的冗余前缀已去掉（2026-09-18）：`user.user_avatar` → `user.avatar`、
--   `user.user_profile` → `user.profile`（历史 camelCase→snake_case 见 migrations/2026-09-17-*）。
-- ============================================================

-- ⚠️ 字符集声明（必须放在第一条语句之前）
--   本文件是 UTF-8 编码，含中文字面量（如 question.difficulty 的 DEFAULT '简单'）。
--   Windows 中文系统的 mysql.exe 客户端默认 character_set_client=gbk，
--   会把 UTF-8 的中文按 GBK 解读，写入 utf8mb4 列时报：
--       ERROR 1067 (42000): Invalid default value for 'difficulty'
--   这里强制按 utf8mb4 解析，使脚本在任意平台行为一致。
--   （等价命令行参数：mysql --default-character-set=utf8mb4 ...）
set names utf8mb4;

-- 创建库
create database if not exists myoj;

-- 切换库
use myoj;


-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
create table if not exists `user`
(
    `id`           bigint auto_increment comment 'id' primary key,
    `account`      varchar(256)                           not null comment '账号',
    `password`     varchar(512)                           not null comment '密码',
    `union_id`     varchar(256)                           null comment '微信开放平台id',
    `mp_open_id`   varchar(256)                           null comment '公众号openId',
    `username`     varchar(256)                           null comment '用户昵称',
    `avatar`       varchar(1024)                          null comment '用户头像',
    `profile`      varchar(512)                           null comment '用户简介',
    `role`         varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    `create_time`  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time`  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete`    tinyint      default 0                 not null comment '是否删除',
    unique key `uk_account` (`account`),
    index `idx_union_id` (`union_id`)
) comment '用户' collate = utf8mb4_unicode_ci;


-- ------------------------------------------------------------
-- 题目表
-- difficulty: 难度筛选（题库专题页）。取值 简单/中等/困难，Service 侧有白名单校验。
-- ------------------------------------------------------------
create table if not exists `question`
(
    `id`           bigint auto_increment comment 'id' primary key,
    `title`        varchar(512)                       null comment '标题',
    `content`      text                               null comment '内容',
    `tags`         varchar(1024)                      null comment '标签列表（json 数组）',
    `answer`       text                               null comment '题目答案',
    `difficulty`   varchar(50) default '简单'          not null comment '难度：简单/中等/困难',
    `submit_num`   int         default 0              not null comment '题目提交数',
    `accepted_num` int         default 0              not null comment '题目通过数',
    `judge_case`   text                               null comment '判题用例（json 数组）',
    `judge_config` text                               null comment '判题配置（json 对象）',
    `thumb_num`    int         default 0              not null comment '点赞数',
    `favour_num`   int         default 0              not null comment '收藏数',
    `user_id`      bigint                             not null comment '创建用户 id',
    `create_time`  datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time`  datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete`    tinyint     default 0              not null comment '是否删除',
    index `idx_user_id` (`user_id`)
) comment '题目' collate = utf8mb4_unicode_ci;


-- ------------------------------------------------------------
-- 题目提交表
-- verdict: 判题结果冗余列。status 只有 4 值，而 JudgeInfoMessageEnum 有 10 种。
--          仅当 status = 2（SUCCEED）时 verdict 才有意义；status = 3 时为 SYSTEM_ERROR。
--          由判题服务写入（沙箱只回传执行事实，不做 verdict 判定）。
-- ------------------------------------------------------------
create table if not exists `question_submit`
(
    `id`          bigint auto_increment comment 'id' primary key,
    `language`    varchar(128)                       not null comment '编程语言',
    `code`        text                               not null comment '用户代码',
    `judge_info`  text                               null comment '判题信息（json 对象）',
    `status`      int      default 0                 not null comment '判题状态（0 - 待判题、1 - 判题中、2 - 成功、3 - 失败）',
    `verdict`     varchar(50)                        null comment '判题结果：ACCEPTED/WRONG_ANSWER/...（status=2 时有意义）',
    `question_id` bigint                             not null comment '题目 id',
    `user_id`     bigint                             not null comment '创建用户 id',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete`   tinyint  default 0                 not null comment '是否删除',
    index `idx_question_id` (`question_id`),
    index `idx_user_id` (`user_id`),
    index `idx_status_verdict` (`status`, `verdict`)
) comment '题目提交';


-- ------------------------------------------------------------
-- 帖子表
-- ⚠️ post / post_thumb / post_favour 三张表来自鱼皮原版教程的社区模块。
--    本微服务项目**没有对应的 Java 实体/Service/Controller**（实测 0 文件），
--    表内亦无数据。保留是为了不破坏老库结构、并给将来做社区功能留位。
--    若确认不做社区功能，可整体删除这三张表。
-- ------------------------------------------------------------
create table if not exists `post`
(
    `id`          bigint auto_increment comment 'id' primary key,
    `title`       varchar(512)                       null comment '标题',
    `content`     text                               null comment '内容',
    `tags`        varchar(1024)                      null comment '标签列表（json 数组）',
    `thumb_num`   int      default 0                 not null comment '点赞数',
    `favour_num`  int      default 0                 not null comment '收藏数',
    `user_id`     bigint                             not null comment '创建用户 id',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete`   tinyint  default 0                 not null comment '是否删除',
    index `idx_user_id` (`user_id`)
) comment '帖子' collate = utf8mb4_unicode_ci;


-- 帖子点赞表（硬删除，无 is_delete）
create table if not exists `post_thumb`
(
    `id`          bigint auto_increment comment 'id' primary key,
    `post_id`     bigint                             not null comment '帖子 id',
    `user_id`     bigint                             not null comment '创建用户 id',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index `idx_post_id` (`post_id`),
    index `idx_user_id` (`user_id`)
) comment '帖子点赞';


-- 帖子收藏表（硬删除，无 is_delete）
create table if not exists `post_favour`
(
    `id`          bigint auto_increment comment 'id' primary key,
    `post_id`     bigint                             not null comment '帖子 id',
    `user_id`     bigint                             not null comment '创建用户 id',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index `idx_post_id` (`post_id`),
    index `idx_user_id` (`user_id`)
) comment '帖子收藏';


-- ------------------------------------------------------------
-- 题单主表（题单详情页）
-- is_public = 0 私有（仅创建者可见）/ 1 公开。
-- source_bank_id 记录 fork 来源，NULL = 原创。
-- 参考 pandora 的 question_bank，但增加了 is_public / source_bank_id（自研）。
-- ------------------------------------------------------------
create table if not exists `question_bank`
(
    `id`             bigint auto_increment comment 'id' primary key,
    `title`          varchar(512)                       not null comment '题单标题',
    `description`    text                               null comment '题单描述',
    `picture`        varchar(1024)                      null comment '题单封面图 URL',
    `user_id`        bigint                             not null comment '创建用户 id',
    `is_public`      tinyint  default 1                 not null comment '是否公开：0-私有 1-公开',
    `source_bank_id` bigint                             null comment 'fork 来源题单 id（NULL = 原创）',
    `fork_num`       int      default 0                 not null comment '被 fork 次数',
    `create_time`    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time`    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete`      tinyint  default 0                 not null comment '是否删除',
    index `idx_user_id` (`user_id`),
    index `idx_is_public` (`is_public`),
    index `idx_title` (`title`)
) comment '题单' collate = utf8mb4_unicode_ci;


-- ------------------------------------------------------------
-- 题单-题目关联表
-- UNIQUE(question_bank_id, question_id) 防止同一题重复加入同一题单。
-- 硬删除（无 is_delete）：移出题单即真删，符合直觉且让唯一约束可直接复用。
-- ------------------------------------------------------------
create table if not exists `question_bank_question`
(
    `id`               bigint auto_increment comment 'id' primary key,
    `question_bank_id` bigint                             not null comment '题单 id',
    `question_id`      bigint                             not null comment '题目 id',
    `user_id`          bigint                             not null comment '操作（添加）用户 id',
    `create_time`      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time`      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key `uk_bank_question` (`question_bank_id`, `question_id`),
    index `idx_question_id` (`question_id`)
) comment '题单-题目关联（硬删除）' collate = utf8mb4_unicode_ci;


-- ------------------------------------------------------------
-- 题目收藏表（抄 pandora）
-- UNIQUE(question_id, user_id) 保证一人一题只收藏一次。
-- 硬删除（无 is_delete）：取消收藏即真删。
-- ------------------------------------------------------------
create table if not exists `question_favourite`
(
    `id`          bigint auto_increment comment 'id' primary key,
    `question_id` bigint                             not null comment '题目 id',
    `user_id`     bigint                             not null comment '创建用户 id',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key `uk_question_user` (`question_id`, `user_id`),
    index `idx_user_id` (`user_id`)
) comment '题目收藏（硬删除）' collate = utf8mb4_unicode_ci;


-- ------------------------------------------------------------
-- 题单收藏表
-- 与 question_favourite 同构：UNIQUE(bank_id, user_id) 保证一人一题单只收藏一次。
-- 之所以不复用 question_favourite：那张表的 question_id 语义是「题目」，
-- 混入题单 id 会造成「同一个 id 空间两套含义」，查询与统计都会踩坑。
-- 硬删除（无 is_delete）：取消收藏即真删。
-- ------------------------------------------------------------
create table if not exists `question_bank_favourite`
(
    `id`          bigint auto_increment comment 'id' primary key,
    `bank_id`     bigint                             not null comment '题单 id',
    `user_id`     bigint                             not null comment '创建用户 id',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key `uk_bank_user` (`bank_id`, `user_id`),
    index `idx_user_id` (`user_id`)
) comment '题单收藏（硬删除）' collate = utf8mb4_unicode_ci;


-- ============================================================
-- 执行后自检（应返回 10 张表）
--   show tables;
-- 预期：post / post_favour / post_thumb / question /
--       question_bank / question_bank_favourite / question_bank_question /
--       question_favourite / question_submit / user
--
-- 列名风格自检（应返回 0）：
--   SELECT COUNT(*) FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA='myoj'
--     AND BINARY COLUMN_NAME <> BINARY LOWER(COLUMN_NAME);
-- ⚠️ 必须用 BINARY 比较。information_schema 的列默认是 ci（大小写不敏感）排序规则，
--    以下两种写法都会「假阴性」（永远返回 0，查不出残留驼峰列）：
--      ① COLUMN_NAME <> LOWER(COLUMN_NAME)            —— ci 排序下 'userId' = 'userid'
--      ② CONVERT(COLUMN_NAME USING utf8mb4) <> ...     —— utf8mb4 默认 collation 仍是 ci
--      ③ COLUMN_NAME REGEXP '[A-Z]'                    —— 同上，ci 下匹配到全小写列
-- ============================================================
