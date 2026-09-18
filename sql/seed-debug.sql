-- ============================================================
-- 本地调试种子数据（seed-debug）
-- 目标库：myoj
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p123456 myoj < sql/seed-debug.sql
-- 说明：
--   ① admin 账号已存在，此处将其密码重置为 admin123（与 config.example.yaml 一致），便于本地登录调试。
--      密码 = md5("yupi" + 明文)：md5("yupiadmin123") = 563a6264ccf929ab703cc8600a82382c
--   ② user1 为新增普通测试账号（12345678）：md5("yupi12345678") = b0dd3697a192885d7c055db46155b26a
--   ③ question_bank 原本为空，补 1 个公开题单并挂 2 道新题，供题单/仪表板功能调试。
--   ④ 新增 2 道带 judge_case/judge_config 的题目 + 5 条覆盖各 status/verdict 的提交，供判题链路调试。
-- 幂等性：user1 用 INSERT IGNORE（已存在则跳过）；其余为追加，重复执行会+2题/+5提交/+1题单。
-- ============================================================
set names utf8mb4;
use myoj;

-- 1) 重置 admin 密码为 admin123（覆盖现有未知密码，使 config.example.yaml 凭据可登录）
UPDATE user
SET password = '563a6264ccf929ab703cc8600a82382c',
    username = '管理员',
    profile = '本地调试管理员账号'
WHERE account = 'admin';

-- 2) 新增普通测试账号 user1 / 12345678（已存在则跳过）
INSERT IGNORE INTO user (account, password, username, profile, role)
VALUES ('user1', 'b0dd3697a192885d7c055db46155b26a', '测试用户', '本地调试普通账号', 'user');

-- 3) 新增 2 道带判题用例的题目（user_id=1 即 admin）
INSERT INTO question (title, content, tags, answer, difficulty, judge_case, judge_config, user_id)
VALUES ('两数之和',
        '给定一个整数数组 nums 和一个目标值 target，请在数组中找出和为目标值的两个整数，并返回它们的下标。',
        '["数组","哈希表"]',
        '使用哈希表记录已遍历数字的下标，O(n) 一次遍历即可。',
        '简单',
        '[{"input":"[2,7,11,15]\\n9","output":"[0,1]"},{"input":"[3,2,4]\\n6","output":"[1,2]"}]',
        '{"timeLimit":1000,"memoryLimit":262144,"compareMode":"TEXT"}',
        1);
SET @q1 = LAST_INSERT_ID();

INSERT INTO question (title, content, tags, answer, difficulty, judge_case, judge_config, user_id)
VALUES ('反转字符串',
        '编写一个函数，将输入字符串反转后返回。',
        '["字符串","双指针"]',
        '双指针从两端向中间交换字符。',
        '简单',
        '[{"input":"hello","output":"olleh"},{"input":"a","output":"a"}]',
        '{"timeLimit":1000,"memoryLimit":262144,"compareMode":"TEXT"}',
        1);
SET @q2 = LAST_INSERT_ID();

-- 4) 新增 5 条提交，覆盖 4 种 status 与典型 verdict
INSERT INTO question_submit (language, code, judge_info, status, verdict, question_id, user_id)
VALUES
    ('java', 'class Main{ public static void main(String[] a){} }',
     '{"message":"Accepted","time":12,"memory":1024}', 2, 'ACCEPTED', @q1, 1),
    ('java', 'class Main{ public static void main(String[] a){} }',
     '{"message":"Wrong Answer","time":10,"memory":1024}', 2, 'WRONG_ANSWER', @q1, 2),
    ('java', 'class Main{ public static void main(String[] a){} }',
     NULL, 0, NULL, @q2, 1),
    ('java', 'class Main{ public static void main(String[] a){} }',
     NULL, 1, NULL, @q2, 2),
    ('java', 'class Main{ public static void main(String[] a){} }',
     '{"message":"System Error","time":0,"memory":0}', 3, 'SYSTEM_ERROR', @q1, 1);

-- 5) 新增 1 个公开题单，挂上面 2 道新题（question_bank 原本为空）
INSERT INTO question_bank (title, description, user_id, is_public)
VALUES ('入门算法题单', '适合新手的数组与字符串练习。', 2, 1);
SET @b1 = LAST_INSERT_ID();

INSERT INTO question_bank_question (question_bank_id, question_id, user_id)
VALUES (@b1, @q1, 2), (@b1, @q2, 2);

-- 自检
SELECT 'user' t, COUNT(*) c FROM user
UNION ALL SELECT 'question', COUNT(*) FROM question
UNION ALL SELECT 'question_submit', COUNT(*) FROM question_submit
UNION ALL SELECT 'question_bank', COUNT(*) FROM question_bank;
