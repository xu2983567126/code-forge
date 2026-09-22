package com.xly.codeforge.model.judge;

import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CodeTemplateGenerator} 验证：骨架/驱动生成、骨架反解析，以及<b>真实编译并运行</b>装配后的代码
 * （模仿沙箱 {@code javac Main.java} 零第三方 classpath 的环境），确保 driver 在纯 JDK 下可编译可执行。
 */
class CodeTemplateGeneratorTest {

    private static CodeTemplateSignature sig(String method, String ret, Object... typeNamePairs) {
        List<CodeTemplateSignature.Param> params = new ArrayList<>();
        for (int i = 0; i < typeNamePairs.length; i += 2) {
            params.add(new CodeTemplateSignature.Param((String) typeNamePairs[i], (String) typeNamePairs[i + 1]));
        }
        return new CodeTemplateSignature(method, ret, params);
    }

    @Test
    void renderSkeleton_格式正确_类名非public() {
        String skeleton = CodeTemplateGenerator.renderSkeleton(sig("twoSum", "int[]", "int[]", "nums", "int", "target"));
        assertTrue(skeleton.startsWith("class Solution {"));
        assertTrue(skeleton.contains("public int[] twoSum(int[] nums, int target)"));
        assertTrue(skeleton.contains("// TODO: 在此实现"));
    }

    @Test
    void renderDriver_包含方法调用与main() {
        String driver = CodeTemplateGenerator.renderDriver(sig("twoSum", "int[]", "int[]", "nums", "int", "target"));
        assertTrue(driver.contains("public class Main"));
        assertTrue(driver.contains("new Solution().twoSum") || driver.contains("s.twoSum"));
        assertTrue(driver.contains("toIntArray"));
        assertTrue(driver.contains("System.out.println(fmt(result))"));
    }

    @Test
    void fromSkeleton_往返一致() {
        CodeTemplateSignature original = sig("twoSum", "int[]", "int[]", "nums", "int", "target");
        String skeleton = CodeTemplateGenerator.renderSkeleton(original);
        CodeTemplateSignature parsed = CodeTemplateGenerator.fromSkeleton(skeleton);
        assertNotNull(parsed);
        assertEquals("twoSum", parsed.methodName());
        assertEquals("int[]", parsed.returnType());
        assertEquals(2, parsed.params().size());
        assertEquals("int[]", parsed.params().get(0).type());
        assertEquals("nums", parsed.params().get(0).name());
    }

    @Test
    void fromSkeleton_非Solution骨架_返回null() {
        assertNull(CodeTemplateGenerator.fromSkeleton("public class Main { public static void main(String[] a) {} }"));
        assertNull(CodeTemplateGenerator.fromSkeleton(null));
        assertNull(CodeTemplateGenerator.fromSkeleton(""));
    }

    @Test
    void renderDriver_不支持的类型_抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> CodeTemplateGenerator.renderDriver(sig("f", "List<List<Integer>>")));
        assertThrows(IllegalArgumentException.class,
                () -> CodeTemplateGenerator.renderDriver(sig("v", "void", "int", "x")));
    }

    // ============================ 端到端：真实编译 + 运行 ============================

    @Test
    void 端到端_twoSum_编译运行通过() throws Exception {
        CodeTemplateSignature signature = sig("twoSum", "int[]", "int[]", "nums", "int", "target");
        runAndAssert(signature,
                "class Solution {\n"
                        + "    public int[] twoSum(int[] nums, int target) {\n"
                        + "        for (int i = 0; i < nums.length; i++) {\n"
                        + "            for (int j = i + 1; j < nums.length; j++) {\n"
                        + "                if (nums[i] + nums[j] == target) return new int[]{i, j};\n"
                        + "            }\n"
                        + "        }\n"
                        + "        return new int[]{};\n"
                        + "    }\n"
                        + "}",
                "[[2,7,11,15], 9]", "[0,1]");
    }

    @Test
    void 端到端_基本类型与字符串_编译运行通过() throws Exception {
        CodeTemplateSignature signature = sig("greet", "String", "String", "name");
        runAndAssert(signature,
                "class Solution {\n"
                        + "    public String greet(String name) {\n"
                        + "        return \"hello \" + name;\n"
                        + "    }\n"
                        + "}",
                "[\"world\"]", "hello world");
    }

    @Test
    void 端到端_List返回_编译运行通过() throws Exception {
        CodeTemplateSignature signature = sig("doubleList", "List<Integer>", "List<Integer>", "nums");
        runAndAssert(signature,
                "class Solution {\n"
                        + "    public java.util.List<Integer> doubleList(java.util.List<Integer> nums) {\n"
                        + "        java.util.List<Integer> r = new java.util.ArrayList<>();\n"
                        + "        for (int n : nums) r.add(n * 2);\n"
                        + "        return r;\n"
                        + "    }\n"
                        + "}",
                "[[1,2,3]]", "[2,4,6]");
    }

    /**
     * 真实复刻沙箱：用 {@code SandboxCodeAssembler} 装配（含默认 import 注入），写入 {@code Main.java}，
     * 以零额外 classpath 编译，再把输入写成文件、按 argv[0] 交给驱动运行，断言 stdout 等于 expected。
     */
    private void runAndAssert(CodeTemplateSignature signature, String userSolution, String input, String expected)
            throws Exception {
        Path dir = compileAssembled(signature, userSolution);
        String actual = runJava(dir, input).stdout();
        assertEquals(expected, actual, "运行输出不符");
    }

    /**
     * 端到端钉住「驱动自报堆峰值」这条链路：文件必须落在输入文件旁（{@code <输入文件>.mem}），
     * 且值必须是<b>用户程序自己的</b>堆峰值（几 MB），而不是容器/进程级水位。
     *
     * <p>沙箱侧按同一规则读该文件（{@code JavaCodeSandboxTemplate#memoryFilePathForRun}），
     * 任何一边改了路径规则这条测试都会红。</p>
     */
    @Test
    void 端到端_驱动退出时把堆峰值写到输入文件旁的mem() throws Exception {
        CodeTemplateSignature signature = sig("take", "int", "int", "n");
        String userSolution = "class Solution {\n"
                + "    public int take(int n) {\n"
                + "        long[] block = new long[500000];\n"   // 约 3.9MB，确保峰值明显高于 JVM 基线
                + "        block[0] = n;\n"
                + "        return (int) block[0];\n"
                + "    }\n"
                + "}";

        Path dir = compileAssembled(signature, userSolution);
        // 输入是 JSON 数组（实参列表）
        RunOutcome outcome = runJava(dir, "[7]");

        assertEquals("7", outcome.stdout());
        assertNotNull(outcome.memoryKb(), "驱动没有写出堆峰值自报文件（<输入文件>.mem）");
        // 下界 = 数组本身；上界放宽到 128MB，只用于排除「把容器/进程水位当程序内存」的错值
        assertTrue(outcome.memoryKb() >= 3900,
                "堆峰值偏小，疑似没量到用户数组：" + outcome.memoryKb() + "KB");
        assertTrue(outcome.memoryKb() <= 131072,
                "堆峰值偏大，疑似量到了进程/容器级水位：" + outcome.memoryKb() + "KB");
    }

    /** 装配 + 编译，返回编译产物所在目录。 */
    private Path compileAssembled(CodeTemplateSignature signature, String userSolution) throws Exception {
        String driver = CodeTemplateGenerator.renderDriver(signature);
        // 与真实判题一致：用户代码在前、驱动在后，并注入默认 import
        String assembled = SandboxCodeAssembler.assemble(userSolution, driver);

        Path dir = Files.createTempDirectory("cf-tpl-test");
        Path mainFile = dir.resolve("Main.java");
        Files.writeString(mainFile, assembled);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, List.of("-d", dir.toString()), null,
                compiler.getStandardFileManager(null, null, null).getJavaFileObjects(mainFile));
        assertTrue(task.call(), "编译失败，源码：\n" + assembled);
        return dir;
    }

    /** 一次运行的可观测产物：stdout + 驱动自报的内存（KB），后者缺失为 null。 */
    private record RunOutcome(String stdout, Long memoryKb) {
    }

    private RunOutcome runJava(Path classDir, String input) throws IOException, InterruptedException {
        String javaExe = javaExecutable();
        // 驱动按 argv[0] 给出的路径读输入文件，不读标准输入
        Path inputFile = Files.createTempFile(classDir, "input-", ".json");
        Files.writeString(inputFile, input, java.nio.charset.StandardCharsets.UTF_8);
        ProcessBuilder pb = new ProcessBuilder(javaExe, "-cp", classDir.toString(), "Main", inputFile.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String out = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int code = process.waitFor();
        if (code != 0) {
            throw new AssertionError("运行失败，退出码=" + code + "，输出=" + out);
        }
        return new RunOutcome(out, readMemoryKb(inputFile));
    }

    /**
     * 读出驱动自报的堆峰值（KB）。驱动按「输入文件路径 + .mem」写，读不到返回 null。
     */
    private Long readMemoryKb(Path inputFile) throws IOException {
        Path memoryFile = Path.of(inputFile + ".mem");
        if (!Files.isRegularFile(memoryFile)) {
            return null;
        }
        String text = Files.readString(memoryFile).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }

    private String javaExecutable() {
        String home = System.getProperty("java.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        String name = os.contains("win") ? "java.exe" : "java";
        Path p = Path.of(home, "bin", name);
        if (!Files.exists(p) && os.contains("win")) {
            p = Path.of(home, "bin", "java.exe");
        }
        return p.toString();
    }
}
