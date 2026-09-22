package com.xly.codeforge.model.judge;

import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 核心代码模式下的「用户代码 + 驱动代码」装配。
 *
 * <p>沙箱只编译并运行一个 {@code Main.java}（见沙箱侧 {@code MAIN_CLASS_FILE}、{@code javac Main.java}、
 * {@code java Main}），因此驱动代码与用户代码必须拼成同一份源码。Java 要求 {@code import} 语句出现在
 * 所有类声明之前，而驱动又是整段拼到用户类之后的，所以这里先把<b>双方的 import 抽到文件最前</b>（去重），
 * 再按「用户代码在前、驱动在后」拼接——既保证编译通过，又让编译报错的行号尽量贴近用户编辑器。</p>
 *
 * <p>驱动代码为空表示普通题：用户自己写 {@code main} 与输入输出，此时不拼接也不校验。</p>
 */
public final class SandboxCodeAssembler {

    /**
     * LeetCode 式默认 import：核心代码模式下用户只写 {@code class Solution}，却常直接使用
     * {@code List / ArrayList / HashMap} 等而不写 import。沙箱编译命令是 {@code javac Main.java}
     * 且没有任何第三方 classpath，所以这里在装配时统一预置这些默认 import，等价于 LeetCode 的隐式导入。
     *
     * <p>⚠️ 这 5 行 + 1 个空行会把用户代码整体下推 6 行，javac 报的行号因此比编辑器所见大 6
     * （已实测：编辑器第 8 行报成 {@code Main.java:14}）。</p>
     *
     * <p>TODO 未来扩展点：消掉这 6 行偏移。① 用 Java 25+ 隐式类（隐式类的 {@code java.base}
     * 隐式导入覆盖本常量全部 5 个包），driver 不再前置任何内容；② 把这几行 import 写进用户可见的
     * {@code code_template}，改为「用户看到的即编译的」。两条路都需同步改前端
     * {@code solutionTemplate.ts} 的镜像文本与沙箱契约测试。触发条件：用户开始按报错行号定位不到代码。</p>
     */
    private static final String DEFAULT_IMPORTS = String.join("\n",
            "import java.util.*;",
            "import java.util.function.*;",
            "import java.util.stream.*;",
            "import java.math.*;",
            "import java.io.*;") + "\n";

    /**
     * 整行 import 语句（含行尾换行），用于抽到文件头部并去重。
     * 捕获组 1 是纯 {@code import ...;}（不含首尾空白与换行），便于去重比较。
     */
    private static final Pattern IMPORT_LINE = Pattern.compile("(?m)^\\s*(import\\s+[^;]+;)\\s*\\n?");

    /**
     * 用户自带 {@code public class Main} 即视为绕过驱动的输入解析。
     * 允许 {@code Main} 出现在其他位置（如注释、字符串）以外的声明形式。
     */
    private static final Pattern USER_MAIN_CLASS = Pattern.compile("public\\s+class\\s+Main\\b");

    /**
     * 用户自带 main 方法同样要拦：即使没有 Main 类，也可以借驱动的类壳塞自己的入口。
     */
    private static final Pattern USER_MAIN_METHOD = Pattern.compile("static\\s+void\\s+main\\s*\\(");

    private SandboxCodeAssembler() {
    }

    /**
     * 拼装交给沙箱的源码。
     *
     * @param userCode   用户提交/试运行的代码
     * @param driverCode 题目的驱动代码，为空表示普通题
     * @return 驱动为空时原样返回用户代码；否则「import 头 + 用户代码 + 驱动」
     */
    public static String assemble(String userCode, String driverCode) {
        if (isBlank(driverCode)) {
            return userCode;
        }
        String safeUserCode = userCode == null ? "" : userCode;
        // import 必须先于任何类声明，故抽到最前并去重（保留首次出现顺序）
        List<String> imports = collectImports(safeUserCode, new ArrayList<>());
        collectImports(driverCode, imports);
        String header = String.join("\n", imports);
        String userBody = stripImports(safeUserCode);
        String driverBody = stripImports(driverCode);
        String body = header.isEmpty()
                ? userBody + "\n\n" + driverBody
                : header + "\n\n" + userBody + "\n\n" + driverBody;
        // 核心代码模式：预置 LeetCode 式默认 import，免去用户手写 import（见 DEFAULT_IMPORTS 注释）
        return DEFAULT_IMPORTS + "\n" + body;
    }

    private static List<String> collectImports(String code, List<String> out) {
        Matcher m = IMPORT_LINE.matcher(code);
        while (m.find()) {
            String imp = m.group(1);
            if (!out.contains(imp)) {
                out.add(imp);
            }
        }
        return out;
    }

    private static String stripImports(String code) {
        return IMPORT_LINE.matcher(code).replaceAll("");
    }

    /**
     * 校验用户代码没有自带入口，避免绕过驱动。驱动为空（普通题）时直接放行。
     *
     * @throws BusinessException 用户代码含 {@code public class Main} 或 {@code static void main(}
     */
    public static void checkUserCode(String userCode, String driverCode) {
        if (isBlank(driverCode) || isBlank(userCode)) {
            return;
        }
        // 先去掉注释再比：避免用户在注释里写「public class Main」被误拦
        String stripped = stripComments(userCode);
        if (USER_MAIN_CLASS.matcher(stripped).find() || USER_MAIN_METHOD.matcher(stripped).find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "核心代码模式下只需实现指定方法，请勿编写 main 方法或 Main 类");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 去掉块注释 {@code /* ... *\/} 与行注释 {@code // ...}（近似：不区分字符串字面量内的 //，
     * 解题代码极少出现，对「是否自带入口」的护栏判断无实质影响）。
     */
    private static String stripComments(String code) {
        return code.replaceAll("/\\*[\\s\\S]*?\\*/", " ")
                .replaceAll("//[^\\n]*", " ");
    }
}
