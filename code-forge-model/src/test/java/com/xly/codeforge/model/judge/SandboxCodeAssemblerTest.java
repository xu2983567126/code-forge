package com.xly.codeforge.model.judge;

import com.xly.codeforge.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 核心代码模式装配：普通题原样返回、核心题拼「用户代码在前 + driver 在后」、且用户自带入口被拦。
 */
class SandboxCodeAssemblerTest {

    @Test
    void assemble_普通题_driver为空_原样返回() {
        String user = "public class Main {\n  public static void main(String[] a) {}\n}";
        assertEquals(user, SandboxCodeAssembler.assemble(user, null));
        assertEquals(user, SandboxCodeAssembler.assemble(user, ""));
        assertEquals(user, SandboxCodeAssembler.assemble(user, "   "));
    }

    @Test
    void assemble_核心题_用户代码拼在driver之前() {
        String user = "class Solution {\n  public int add(int a, int b) { return a + b; }\n}";
        String driver = "public class Main {\n  public static void main(String[] a) {\n    // 读输入调 Solution\n  }\n}";

        String result = SandboxCodeAssembler.assemble(user, driver);

        // 顺序：用户代码在前、driver 在后，确保编译行号与编辑器对齐
        // 注：核心模式会在最前预置默认 import，故不再要求以用户代码开头
        assertTrue(result.indexOf("class Solution") < result.indexOf("public class Main"));
        // 用户代码整体位于 driver 之前
        assertTrue(result.contains(user));
        assertTrue(result.endsWith(driver));
    }

    @Test
    void assemble_用户代码null_按空串处理() {
        String driver = "public class Main {}";
        String result = SandboxCodeAssembler.assemble(null, driver);
        // 核心模式最前预置默认 import，故不再要求以 "\n\n" 开头；只断言仍以 driver 收尾
        assertTrue(result.endsWith(driver));
    }

    @Test
    void assemble_核心题_driver含import_抽到文件最前() {
        // Java 要求 import 在任何类声明之前；driver 的 import 原本会落在用户类之后导致编译失败
        String user = "import java.util.List;\nclass Solution {\n  public int add(int a, int b) { return a + b; }\n}";
        String driver = "import java.util.Scanner;\npublic class Main {\n  public static void main(String[] a) {\n    new Solution().add(1, 2);\n  }\n}";

        String result = SandboxCodeAssembler.assemble(user, driver);

        // import 全部抽到文件最前，且去重
        assertTrue(result.indexOf("import java.util.List;") < result.indexOf("class Solution"));
        assertTrue(result.indexOf("import java.util.Scanner;") < result.indexOf("public class Main"));
        assertEquals(1, countOccurrences(result, "import java.util.Scanner;"));
        // 类声明之后不能再出现 import
        int lastClass = Math.max(result.lastIndexOf("class Solution"), result.lastIndexOf("public class Main"));
        assertTrue(result.indexOf("import ", lastClass) < 0);
    }

    private static int countOccurrences(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            n++;
            i += sub.length();
        }
        return n;
    }

    @Test
    void checkUserCode_普通题_放行() {
        // driver 为空：无论用户写什么都不拦
        SandboxCodeAssembler.checkUserCode("public class Main {}", null);
        SandboxCodeAssembler.checkUserCode("static void main(String[] a) {}", "");
    }

    @Test
    void checkUserCode_核心题_用户写Main类_拒绝() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SandboxCodeAssembler.checkUserCode("public class Main {\n  int x;\n}", "public class Main {}"));
        assertEquals("核心代码模式下只需实现指定方法，请勿编写 main 方法或 Main 类", ex.getMessage());
    }

    @Test
    void checkUserCode_核心题_用户写main方法_拒绝() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SandboxCodeAssembler.checkUserCode("static void main(String[] args) {}", "public class Main {}"));
        assertEquals("核心代码模式下只需实现指定方法，请勿编写 main 方法或 Main 类", ex.getMessage());
    }

    @Test
    void checkUserCode_核心题_用户只实现Solution_放行() {
        SandboxCodeAssembler.checkUserCode(
                "class Solution {\n  public int add(int a, int b) { return a + b; }\n}",
                "public class Main {\n  public static void main(String[] a) {}\n}");
    }

    @Test
    void checkUserCode_用户在注释里写Main_放行() {
        // 仅字符串出现、非声明式 public class Main —— 正则要求 public class Main 紧邻，注释不触发
        SandboxCodeAssembler.checkUserCode(
                "// public class Main 是驱动的，别抄\nclass Solution {}",
                "public class Main {}");
    }

    private static void assertTrue(boolean cond) {
        if (!cond) {
            throw new AssertionError("expected true");
        }
    }
}
