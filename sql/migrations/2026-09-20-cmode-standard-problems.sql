-- 把 6 道标准题转为 LeetCode 核心代码模式（code_template + judge_case 对齐纯 JDK 驱动）
--
-- ⚠️ 本脚本是历史记录，**不要在新库上照抄执行**：其中的 judge_case 用的是旧 wire key `output`，
--    后来 Java 侧 JudgeCase 字段改名为 expectedOutput，跑完必须再执行
--    `2026-09-21-fix-judge-case-wire-key.sql` 把 key 补正，否则判题会落进中性态。
--    脚本正文保持当时口径，不再回改。
SET sql_mode = 'NO_BACKSLASH_ESCAPES';

UPDATE question SET code_template = 'class Solution {
    public int add(int a, int b) {
        // TODO: 在此实现
    }
}
', judge_case = '[{"input": "[1,2]", "output": "3"}, {"input": "[-5,8]", "output": "3"}, {"input": "[100,200]", "output": "300"}]' WHERE id = 2101562818830618625; -- A+B Problem
UPDATE question SET code_template = 'class Solution {
    public int fib(int n) {
        // TODO: 在此实现
    }
}
', judge_case = '[{"input": "[1]", "output": "1"}, {"input": "[5]", "output": "5"}, {"input": "[10]", "output": "55"}]' WHERE id = 2101562819019362306; -- 斐波那契数列
UPDATE question SET code_template = 'class Solution {
    public long factorial(int n) {
        // TODO: 在此实现
    }
}
', judge_case = '[{"input": "[0]", "output": "1"}, {"input": "[5]", "output": "120"}, {"input": "[10]", "output": "3628800"}]' WHERE id = 2101562819153580033; -- 阶乘
UPDATE question SET code_template = 'class Solution {
    public boolean isPrime(int n) {
        // TODO: 在此实现
    }
}
', judge_case = '[{"input": "[2]", "output": "true"}, {"input": "[9]", "output": "false"}, {"input": "[97]", "output": "true"}]' WHERE id = 2101562819346518018; -- 素数判定
UPDATE question SET code_template = 'class Solution {
    public int[] bubbleSort(int[] arr) {
        // TODO: 在此实现
    }
}
', judge_case = '[{"input": "[3,1,2]", "output": "[1,2,3]"}, {"input": "[5,4,3,2,1]", "output": "[1,2,3,4,5]"}]' WHERE id = 2101562819476541441; -- 冒泡排序
UPDATE question SET code_template = 'class Solution {
    public int gcd(int a, int b) {
        // TODO: 在此实现
    }
}
', judge_case = '[{"input": "[12,18]", "output": "6"}, {"input": "[7,13]", "output": "1"}, {"input": "[100,100]", "output": "100"}]' WHERE id = 2101562819606564866; -- 最大公约数
