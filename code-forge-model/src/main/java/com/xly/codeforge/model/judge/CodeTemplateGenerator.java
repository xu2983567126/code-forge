package com.xly.codeforge.model.judge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 由 {@link CodeTemplateSignature} 生成 LeetCode 式模板的两段代码，并把骨架反解析回签名。
 *
 * <p>三段职责：
 * <ol>
 *   <li>{@link #renderSkeleton}：生成展示给用户的 {@code Solution} 骨架（非 public，因与驱动的
 *       {@code public class Main} 同处一个 {@code Main.java}）。</li>
 *   <li>{@link #renderDriver}：生成自带 {@code public class Main} 的驱动——读 argv[0] 指向的输入文件、
 *       按参数类型反序列化为实参、调用 {@code Solution}、把返回值规范化为字符串输出，
 *       并在退出时把本用例堆峰值写到 {@code <输入文件>.mem}（供沙箱回传逐用例内存）。</li>
 *   <li>{@link #fromSkeleton}：从骨架反解析出签名，供管理端二次编辑回填。</li>
 * </ol>
 * </p>
 *
 * <p><b>重要约束：驱动代码运行在判题沙箱里，沙箱编译命令为 {@code javac Main.java} 且没有任何第三方
 * classpath</b>，因此驱动只能使用 JDK 标准库——内置一个极简 JSON 解析器，不依赖 Jackson/hutool。</p>
 */
public final class CodeTemplateGenerator {

    /** 受支持的类型白名单（含返回类型与参数类型；void 不在内，首版不支持）。 */
    private static final Set<String> SUPPORTED = Set.of(
            "int", "long", "double", "float", "boolean", "char", "byte", "short", "String",
            "int[]", "long[]", "double[]", "float[]", "boolean[]", "char[]", "byte[]", "short[]", "String[]",
            "List<Integer>", "List<Long>", "List<Double>", "List<Float>", "List<Boolean>", "List<String>"
    );

    private CodeTemplateGenerator() {
    }

    // ============================ 生成：骨架 ============================

    /**
     * 生成展示给用户的 Solution 骨架。类名固定为 {@code Solution}（非 public，详见类注释）。
     */
    public static String renderSkeleton(CodeTemplateSignature sig) {
        validate(sig);
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < sig.params().size(); i++) {
            if (i > 0) {
                params.append(", ");
            }
            CodeTemplateSignature.Param p = sig.params().get(i);
            params.append(p.type()).append(' ').append(p.name());
        }
        return "class Solution {\n"
                + "    public " + sig.returnType() + " " + sig.methodName() + "(" + params + ") {\n"
                + "        // TODO: 在此实现\n"
                + "    }\n"
                + "}\n";
    }

    // ============================ 生成：驱动 ============================

    /**
     * 生成驱动代码（自带 {@code public class Main}）。若签名非法（类型不支持 / void）抛 {@link IllegalArgumentException}。
     */
    public static String renderDriver(CodeTemplateSignature sig) {
        validate(sig);
        String returnType = sig.returnType();
        String methodName = sig.methodName();

        StringBuilder argExprs = new StringBuilder();
        for (int i = 0; i < sig.params().size(); i++) {
            if (i > 0) {
                argExprs.append(", ");
            }
            argExprs.append(argExpr(i, sig.params().get(i).type()));
        }

        String call = "        Solution s = new Solution();\n"
                + "        " + returnType + " result = s." + methodName + "(" + argExprs + ");\n"
                + "        System.out.println(fmt(result));\n";

        return DRIVER_HEADER + call + DRIVER_FOOTER;
    }

    /** 把第 idx 个参数（已解析为 Object）按声明类型转换成 Java 实参表达式。 */
    private static String argExpr(int idx, String type) {
        String get = "argList.get(" + idx + ")";
        return switch (type) {
            case "int" -> "toInt(" + get + ")";
            case "long" -> "toLong(" + get + ")";
            case "double" -> "toDouble(" + get + ")";
            case "float" -> "toFloat(" + get + ")";
            case "boolean" -> "toBoolean(" + get + ")";
            case "char" -> "toChar(" + get + ")";
            case "byte" -> "toByte(" + get + ")";
            case "short" -> "toShort(" + get + ")";
            case "String" -> "toStr(" + get + ")";
            case "int[]" -> "toIntArray((java.util.List<?>) " + get + ")";
            case "long[]" -> "toLongArray((java.util.List<?>) " + get + ")";
            case "double[]" -> "toDoubleArray((java.util.List<?>) " + get + ")";
            case "float[]" -> "toFloatArray((java.util.List<?>) " + get + ")";
            case "boolean[]" -> "toBoolArray((java.util.List<?>) " + get + ")";
            case "char[]" -> "toCharArray((java.util.List<?>) " + get + ")";
            case "byte[]" -> "toByteArray((java.util.List<?>) " + get + ")";
            case "short[]" -> "toShortArray((java.util.List<?>) " + get + ")";
            case "String[]" -> "toStrArray((java.util.List<?>) " + get + ")";
            case "List<Integer>" -> "toIntList((java.util.List<?>) " + get + ")";
            case "List<Long>" -> "toLongList((java.util.List<?>) " + get + ")";
            case "List<Double>" -> "toDoubleList((java.util.List<?>) " + get + ")";
            case "List<Float>" -> "toFloatList((java.util.List<?>) " + get + ")";
            case "List<Boolean>" -> "toBoolList((java.util.List<?>) " + get + ")";
            case "List<String>" -> "toStrList((java.util.List<?>) " + get + ")";
            default -> throw new IllegalArgumentException("不支持的参数类型: " + type);
        };
    }

    private static void validate(CodeTemplateSignature sig) {
        if (sig == null || isBlank(sig.methodName()) || isBlank(sig.returnType())) {
            throw new IllegalArgumentException("方法签名缺失方法名或返回类型");
        }
        if (!SUPPORTED.contains(sig.returnType())) {
            throw new IllegalArgumentException("不支持的返回类型: " + sig.returnType());
        }
        for (CodeTemplateSignature.Param p : sig.params()) {
            if (isBlank(p.type()) || isBlank(p.name())) {
                throw new IllegalArgumentException("参数类型或名称缺失");
            }
            if (!SUPPORTED.contains(p.type())) {
                throw new IllegalArgumentException("不支持的参数类型: " + p.type());
            }
        }
    }

    // ============================ 反解析：骨架 -> 签名 ============================

    /**
     * 从 Solution 骨架反解析出签名。骨架不是规范 Solution 格式时返回 {@code null}（如普通题的代码片段）。
     */
    public static CodeTemplateSignature fromSkeleton(String codeTemplate) {
        if (isBlank(codeTemplate)) {
            return null;
        }
        Matcher m = SKELETON_PATTERN.matcher(codeTemplate);
        if (!m.find()) {
            return null;
        }
        String returnType = normalizeType(m.group(1));
        String methodName = m.group(2);
        String paramsStr = m.group(3).trim();
        List<CodeTemplateSignature.Param> params = new ArrayList<>();
        if (!paramsStr.isEmpty()) {
            for (String part : paramsStr.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int lastSpace = trimmed.lastIndexOf(' ');
                if (lastSpace < 0) {
                    return null;
                }
                String type = normalizeType(trimmed.substring(0, lastSpace));
                String name = trimmed.substring(lastSpace + 1).trim();
                if (type.isEmpty() || name.isEmpty()) {
                    return null;
                }
                params.add(new CodeTemplateSignature.Param(type, name));
            }
        }
        return new CodeTemplateSignature(methodName, returnType, params);
    }

    /** 规范类型字符串：去掉 < > [ ] 附近的空白并合并连续空白，例如 {@code List< Integer >} -> {@code List<Integer>}。 */
    private static String normalizeType(String raw) {
        return raw.trim()
                .replaceAll("\\s*<\\s*", "<")
                .replaceAll("\\s*>\\s*", ">")
                .replaceAll("\\s*\\[\\s*", "[")
                .replaceAll("\\s*\\]\\s*", "]")
                .replaceAll("\\s+", " ");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 便捷方法：若骨架能解析为合法签名则生成驱动，否则返回 {@code null}。供保存题目时派生 driverCode。
     */
    public static String deriveDriverCode(String codeTemplate) {
        CodeTemplateSignature sig = fromSkeleton(codeTemplate);
        if (sig == null) {
            return null;
        }
        return renderDriver(sig);
    }

    // ============================ 驱动代码模板（纯 JDK） ============================

    private static final String DRIVER_HEADER =
            "public class Main {\n"
            + "    public static void main(String[] args) throws Exception {\n"
            + "        reportHeapPeakOnExit(args);\n"
            + "        String raw = java.nio.file.Files.readString(java.nio.file.Path.of(args[0]), java.nio.charset.StandardCharsets.UTF_8).trim();\n"
            + "        java.util.List<Object> argList = (java.util.List<Object>) parse(raw);\n";

    private static final String DRIVER_FOOTER =
            "    }\n"
            + "\n"
            + "    // ===== 平台测量：退出时把本用例的堆峰值（KB）写到 <输入文件路径>.mem =====\n"
            + "    // 走 shutdown hook 而不是在 main 末尾直接写：用户代码自己 System.exit 时也能落盘。\n"
            + "    // 被 SIGKILL（超时/OOM）的用例不写文件，沙箱据此把该用例内存记为未知。\n"
            + "    private static void reportHeapPeakOnExit(String[] args) {\n"
            + "        java.lang.Runtime.getRuntime().addShutdownHook(new java.lang.Thread(() -> writeHeapPeak(args)));\n"
            + "    }\n"
            + "\n"
            + "    private static void writeHeapPeak(String[] args) {\n"
            + "        try {\n"
            + "            if (args.length == 0) { return; }\n"
            + "            long peak = 0L;\n"
            + "            for (java.lang.management.MemoryPoolMXBean pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {\n"
            + "                if (pool.getType() == java.lang.management.MemoryType.HEAP) { peak += pool.getPeakUsage().getUsed(); }\n"
            + "            }\n"
            + "            java.nio.file.Files.writeString(java.nio.file.Path.of(args[0] + \".mem\"), String.valueOf(peak / 1024));\n"
            + "        } catch (Throwable ignored) {\n"
            + "            // 测量失败绝不能影响判题：stdout 已产出，缺的只是内存数字\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // ===== 极简 JSON 解析：仅依赖 JDK，用于把输入文件解析成参数（输入为 JSON 数组） =====\n"
            + "    private static Object parse(String s) {\n"
            + "        int[] i = {0};\n"
            + "        skipWs(s, i);\n"
            + "        return parseValue(s, i);\n"
            + "    }\n"
            + "    private static void skipWs(String s, int[] i) {\n"
            + "        while (i[0] < s.length()) {\n"
            + "            char c = s.charAt(i[0]);\n"
            + "            if (c == ' ' || c == '\\t' || c == '\\n' || c == '\\r') { i[0]++; } else { break; }\n"
            + "        }\n"
            + "    }\n"
            + "    private static Object parseValue(String s, int[] i) {\n"
            + "        skipWs(s, i);\n"
            + "        char c = s.charAt(i[0]);\n"
            + "        if (c == '[') { return parseArray(s, i); }\n"
            + "        if (c == '{') { return parseObject(s, i); }\n"
            + "        if (c == '\"') { return parseString(s, i); }\n"
            + "        if (c == 't' || c == 'f') { return parseBool(s, i); }\n"
            + "        if (c == 'n') { i[0] += 4; return null; }\n"
            + "        return parseNumber(s, i);\n"
            + "    }\n"
            + "    private static java.util.List<Object> parseArray(String s, int[] i) {\n"
            + "        java.util.List<Object> list = new java.util.ArrayList<>();\n"
            + "        i[0]++; // skip '['\n"
            + "        skipWs(s, i);\n"
            + "        if (i[0] < s.length() && s.charAt(i[0]) == ']') { i[0]++; return list; }\n"
            + "        while (true) {\n"
            + "            list.add(parseValue(s, i));\n"
            + "            skipWs(s, i);\n"
            + "            if (i[0] >= s.length()) { break; }\n"
            + "            char c = s.charAt(i[0]);\n"
            + "            if (c == ',') { i[0]++; continue; }\n"
            + "            if (c == ']') { i[0]++; break; }\n"
            + "            break;\n"
            + "        }\n"
            + "        return list;\n"
            + "    }\n"
            + "    private static java.util.Map<String, Object> parseObject(String s, int[] i) {\n"
            + "        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();\n"
            + "        i[0]++; // skip '{'\n"
            + "        skipWs(s, i);\n"
            + "        if (i[0] < s.length() && s.charAt(i[0]) == '}') { i[0]++; return map; }\n"
            + "        while (true) {\n"
            + "            skipWs(s, i);\n"
            + "            String key = parseString(s, i);\n"
            + "            skipWs(s, i);\n"
            + "            if (i[0] < s.length() && s.charAt(i[0]) == ':') { i[0]++; }\n"
            + "            Object val = parseValue(s, i);\n"
            + "            map.put(key, val);\n"
            + "            skipWs(s, i);\n"
            + "            if (i[0] >= s.length()) { break; }\n"
            + "            char c = s.charAt(i[0]);\n"
            + "            if (c == ',') { i[0]++; continue; }\n"
            + "            if (c == '}') { i[0]++; break; }\n"
            + "            break;\n"
            + "        }\n"
            + "        return map;\n"
            + "    }\n"
            + "    private static String parseString(String s, int[] i) {\n"
            + "        StringBuilder sb = new StringBuilder();\n"
            + "        i[0]++; // skip opening quote\n"
            + "        while (i[0] < s.length()) {\n"
            + "            char c = s.charAt(i[0]);\n"
            + "            if (c == '\\\\') {\n"
            + "                i[0]++; char e = s.charAt(i[0]);\n"
            + "                switch (e) {\n"
            + "                    case 'n': sb.append('\\n'); break;\n"
            + "                    case 't': sb.append('\\t'); break;\n"
            + "                    case 'r': sb.append('\\r'); break;\n"
            + "                    case 'b': sb.append('\\b'); break;\n"
            + "                    case 'f': sb.append('\\f'); break;\n"
            + "                    case 'u': sb.append((char) Integer.parseInt(s.substring(i[0] + 1, i[0] + 5), 16)); i[0] += 4; break;\n"
            + "                    default: sb.append(e);\n"
            + "                }\n"
            + "                i[0]++;\n"
            + "            } else if (c == '\"') {\n"
            + "                i[0]++; break;\n"
            + "            } else { sb.append(c); i[0]++; }\n"
            + "        }\n"
            + "        return sb.toString();\n"
            + "    }\n"
            + "    private static Object parseNumber(String s, int[] i) {\n"
            + "        int start = i[0];\n"
            + "        if (i[0] < s.length() && (s.charAt(i[0]) == '-' || s.charAt(i[0]) == '+')) { i[0]++; }\n"
            + "        while (i[0] < s.length()) {\n"
            + "            char c = s.charAt(i[0]);\n"
            + "            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { i[0]++; } else { break; }\n"
            + "        }\n"
            + "        String num = s.substring(start, i[0]);\n"
            + "        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) { return Double.parseDouble(num); }\n"
            + "        long v = Long.parseLong(num);\n"
            + "        if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) { return Integer.valueOf((int) v); }\n"
            + "        return Long.valueOf(v);\n"
            + "    }\n"
            + "    private static boolean parseBool(String s, int[] i) {\n"
            + "        if (s.charAt(i[0]) == 't') { i[0] += 4; return true; }\n"
            + "        i[0] += 5; return false;\n"
            + "    }\n"
            + "\n"
            + "    // ===== 类型转换：从解析出的 Object 转成方法实参 =====\n"
            + "    private static int toInt(Object o) { return ((Number) o).intValue(); }\n"
            + "    private static long toLong(Object o) { return ((Number) o).longValue(); }\n"
            + "    private static double toDouble(Object o) { return ((Number) o).doubleValue(); }\n"
            + "    private static float toFloat(Object o) { return ((Number) o).floatValue(); }\n"
            + "    private static boolean toBoolean(Object o) { return (Boolean) o; }\n"
            + "    private static char toChar(Object o) {\n"
            + "        if (o instanceof String) { String s = (String) o; return s.isEmpty() ? '\\0' : s.charAt(0); }\n"
            + "        return (Character) o;\n"
            + "    }\n"
            + "    private static byte toByte(Object o) { return ((Number) o).byteValue(); }\n"
            + "    private static short toShort(Object o) { return ((Number) o).shortValue(); }\n"
            + "    private static String toStr(Object o) { return o == null ? null : o.toString(); }\n"
            + "    private static int[] toIntArray(java.util.List<?> l) {\n"
            + "        int[] a = new int[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).intValue(); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static long[] toLongArray(java.util.List<?> l) {\n"
            + "        long[] a = new long[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).longValue(); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static double[] toDoubleArray(java.util.List<?> l) {\n"
            + "        double[] a = new double[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).doubleValue(); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static float[] toFloatArray(java.util.List<?> l) {\n"
            + "        float[] a = new float[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).floatValue(); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static boolean[] toBoolArray(java.util.List<?> l) {\n"
            + "        boolean[] a = new boolean[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = (Boolean) l.get(k); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static char[] toCharArray(java.util.List<?> l) {\n"
            + "        char[] a = new char[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = toChar(l.get(k)); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static byte[] toByteArray(java.util.List<?> l) {\n"
            + "        byte[] a = new byte[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).byteValue(); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static short[] toShortArray(java.util.List<?> l) {\n"
            + "        short[] a = new short[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).shortValue(); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static String[] toStrArray(java.util.List<?> l) {\n"
            + "        String[] a = new String[l.size()];\n"
            + "        for (int k = 0; k < l.size(); k++) { a[k] = toStr(l.get(k)); }\n"
            + "        return a;\n"
            + "    }\n"
            + "    private static java.util.List<Integer> toIntList(java.util.List<?> l) {\n"
            + "        java.util.List<Integer> r = new java.util.ArrayList<>(l.size());\n"
            + "        for (Object o : l) { r.add(((Number) o).intValue()); }\n"
            + "        return r;\n"
            + "    }\n"
            + "    private static java.util.List<Long> toLongList(java.util.List<?> l) {\n"
            + "        java.util.List<Long> r = new java.util.ArrayList<>(l.size());\n"
            + "        for (Object o : l) { r.add(((Number) o).longValue()); }\n"
            + "        return r;\n"
            + "    }\n"
            + "    private static java.util.List<Double> toDoubleList(java.util.List<?> l) {\n"
            + "        java.util.List<Double> r = new java.util.ArrayList<>(l.size());\n"
            + "        for (Object o : l) { r.add(((Number) o).doubleValue()); }\n"
            + "        return r;\n"
            + "    }\n"
            + "    private static java.util.List<Float> toFloatList(java.util.List<?> l) {\n"
            + "        java.util.List<Float> r = new java.util.ArrayList<>(l.size());\n"
            + "        for (Object o : l) { r.add(((Number) o).floatValue()); }\n"
            + "        return r;\n"
            + "    }\n"
            + "    private static java.util.List<Boolean> toBoolList(java.util.List<?> l) {\n"
            + "        java.util.List<Boolean> r = new java.util.ArrayList<>(l.size());\n"
            + "        for (Object o : l) { r.add((Boolean) o); }\n"
            + "        return r;\n"
            + "    }\n"
            + "    private static java.util.List<String> toStrList(java.util.List<?> l) {\n"
            + "        java.util.List<String> r = new java.util.ArrayList<>(l.size());\n"
            + "        for (Object o : l) { r.add(toStr(o)); }\n"
            + "        return r;\n"
            + "    }\n"
            + "\n"
            + "    // ===== 输出格式化：与判题标准答案字符串对齐 =====\n"
            + "    private static String fmt(Object o) {\n"
            + "        if (o == null) { return \"null\"; }\n"
            + "        if (o instanceof java.util.List) {\n"
            + "            java.util.List<?> list = (java.util.List<?>) o;\n"
            + "            StringBuilder sb = new StringBuilder(\"[\");\n"
            + "            for (int k = 0; k < list.size(); k++) { if (k > 0) { sb.append(\",\"); } sb.append(fmt(list.get(k))); }\n"
            + "            return sb.append(\"]\").toString();\n"
            + "        }\n"
            + "        if (o.getClass().isArray()) {\n"
            + "            int len = java.lang.reflect.Array.getLength(o);\n"
            + "            StringBuilder sb = new StringBuilder(\"[\");\n"
            + "            for (int k = 0; k < len; k++) { if (k > 0) { sb.append(\",\"); } sb.append(fmt(java.lang.reflect.Array.get(o, k))); }\n"
            + "            return sb.append(\"]\").toString();\n"
            + "        }\n"
            + "        if (o instanceof Character) { return String.valueOf((Character) o); }\n"
            + "        if (o instanceof String) { return (String) o; }\n"
            + "        return String.valueOf(o);\n"
            + "    }\n"
            + "}\n";

    /** 骨架正则：class Solution { ... public <返回类型> <方法名>(<参数>) ... } */
    private static final Pattern SKELETON_PATTERN = Pattern.compile(
            "class\\s+Solution\\s*\\{[\\s\\S]*?public\\s+([\\w\\[\\]<>,\\s]*?)\\s+([A-Za-z_]\\w*)\\s*\\(([^)]*)\\)");
}
