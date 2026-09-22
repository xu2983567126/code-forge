/**
 * LeetCode 式 Solution 模板：方法签名 <-> 骨架/驱动字符串 的纯前端镜像。
 *
 * 两段职责：
 * 1) 骨架：管理端表单录入签名时实时预览（buildSolutionSkeleton），编辑题目时把题级 codeTemplate
 *    反解析回签名表单（parseSolutionSkeleton）。骨架格式必须与后端 renderSkeleton 完全对齐。
 * 2) 驱动（仅展示用）：题目页把「驱动代码」展示给用户看（deriveSolutionDriver），便于理解输入输出
 *    如何被解析。后端 CodeTemplateGenerator.renderDriver 是判题执行时的唯一权威（沙箱实际编译运行的
 *    驱动由它生成），本文件镜像同一份驱动仅供展示。
 *
 * ⚠️ 后端改了 renderDriver（输入读取 / 解析器 / 类型转换器 / fmt / 堆峰值自报）时，本文件的 DRIVER_HEADER /
 *    DRIVER_FOOTER / argExpr 必须同步改，否则题目页展示的驱动与沙箱实际执行的驱动不一致。
 *    受支持类型与后端白名单一致。
 */

export interface SolutionParam {
  type: string
  name: string
}

export interface SolutionSignature {
  methodName: string
  returnType: string
  params: SolutionParam[]
}

/** 受支持类型（与后端 CodeTemplateGenerator.SUPPORTED 对齐）。 */
export const SOLUTION_TYPE_OPTIONS: string[] = [
  'int',
  'long',
  'double',
  'float',
  'boolean',
  'char',
  'byte',
  'short',
  'String',
  'int[]',
  'long[]',
  'double[]',
  'float[]',
  'boolean[]',
  'char[]',
  'byte[]',
  'short[]',
  'String[]',
  'List<Integer>',
  'List<Long>',
  'List<Double>',
  'List<Float>',
  'List<Boolean>',
  'List<String>'
]

/**
 * 由签名生成 Solution 骨架（镜像后端 renderSkeleton）。
 * 类名固定 Solution（非 public，与后端生成的 public class Main 同处一个 Main.java）。
 */
export function buildSolutionSkeleton(sig: SolutionSignature): string {
  const params = sig.params.map((p) => `${p.type} ${p.name}`).join(', ')
  return `class Solution {\n    public ${sig.returnType} ${sig.methodName}(${params}) {\n        // TODO: 在此实现\n    }\n}\n`
}

function normalizeType(raw: string): string {
  return raw
    .trim()
    .replace(/\s*<\s*/g, '<')
    .replace(/\s*>\s*/g, '>')
    .replace(/\s*\[\s*/g, '[')
    .replace(/\s*\]\s*/g, ']')
    .replace(/\s+/g, ' ')
}

/**
 * 从骨架反解析签名（镜像后端 fromSkeleton）。骨架不是规范 Solution 格式时返回 null。
 */
export function parseSolutionSkeleton(codeTemplate?: string | null): SolutionSignature | null {
  if (!codeTemplate) {
    return null
  }
  const m = codeTemplate.match(
    /class\s+Solution\s*\{[\s\S]*?public\s+([\w[\]<>,\s]*?)\s+([A-Za-z_]\w*)\s*\(([^)]*)\)/
  )
  if (!m) {
    return null
  }
  const returnType = normalizeType(m[1])
  const methodName = m[2]
  const paramsStr = m[3].trim()
  const params: SolutionParam[] = []
  if (paramsStr) {
    for (const part of paramsStr.split(',')) {
      const t = part.trim()
      if (!t) {
        continue
      }
      const idx = t.lastIndexOf(' ')
      if (idx < 0) {
        return null
      }
      const type = normalizeType(t.substring(0, idx))
      const name = t.substring(idx + 1).trim()
      if (!type || !name) {
        return null
      }
      params.push({ type, name })
    }
  }
  return { methodName, returnType, params }
}

// ============================ 生成：驱动（仅展示用，镜像后端 renderDriver） ============================

/** 驱动头部：读取 argv[0] 指向的输入文件（JSON 数组）并解析成实参列表。与后端 DRIVER_HEADER 一致。 */
const DRIVER_HEADER = `public class Main {
    public static void main(String[] args) throws Exception {
        reportHeapPeakOnExit(args);
        String raw = java.nio.file.Files.readString(java.nio.file.Path.of(args[0]), java.nio.charset.StandardCharsets.UTF_8).trim();
        java.util.List<Object> argList = (java.util.List<Object>) parse(raw);
`

/** 第 idx 个参数（已解析为 Object）按声明类型转换成 Java 实参表达式。与后端 argExpr 映射一致。 */
function argExpr(idx: number, type: string): string {
  const get = `argList.get(${idx})`
  switch (type) {
    case 'int': return `toInt(${get})`
    case 'long': return `toLong(${get})`
    case 'double': return `toDouble(${get})`
    case 'float': return `toFloat(${get})`
    case 'boolean': return `toBoolean(${get})`
    case 'char': return `toChar(${get})`
    case 'byte': return `toByte(${get})`
    case 'short': return `toShort(${get})`
    case 'String': return `toStr(${get})`
    case 'int[]': return `toIntArray((java.util.List<?>) ${get})`
    case 'long[]': return `toLongArray((java.util.List<?>) ${get})`
    case 'double[]': return `toDoubleArray((java.util.List<?>) ${get})`
    case 'float[]': return `toFloatArray((java.util.List<?>) ${get})`
    case 'boolean[]': return `toBoolArray((java.util.List<?>) ${get})`
    case 'char[]': return `toCharArray((java.util.List<?>) ${get})`
    case 'byte[]': return `toByteArray((java.util.List<?>) ${get})`
    case 'short[]': return `toShortArray((java.util.List<?>) ${get})`
    case 'String[]': return `toStrArray((java.util.List<?>) ${get})`
    case 'List<Integer>': return `toIntList((java.util.List<?>) ${get})`
    case 'List<Long>': return `toLongList((java.util.List<?>) ${get})`
    case 'List<Double>': return `toDoubleList((java.util.List<?>) ${get})`
    case 'List<Float>': return `toFloatList((java.util.List<?>) ${get})`
    case 'List<Boolean>': return `toBoolList((java.util.List<?>) ${get})`
    case 'List<String>': return `toStrList((java.util.List<?>) ${get})`
    default: throw new Error('不支持的参数类型: ' + type)
  }
}

/** 由签名生成驱动代码（镜像后端 renderDriver）。普通题（签名缺失）返回 null。 */
export function buildSolutionDriver(sig: SolutionSignature): string | null {
  if (!sig || !sig.methodName?.trim() || !sig.returnType?.trim()) {
    return null
  }
  const argExprs = sig.params.map((p, i) => argExpr(i, p.type)).join(', ')
  const call = `        Solution s = new Solution();
        ${sig.returnType} result = s.${sig.methodName}(${argExprs});
        System.out.println(fmt(result));
`
  return DRIVER_HEADER + call + DRIVER_FOOTER
}

/** 便捷方法：骨架能解析为合法签名则生成驱动，否则返回 null（镜像后端 deriveDriverCode）。 */
export function deriveSolutionDriver(codeTemplate?: string | null): string | null {
  const sig = parseSolutionSkeleton(codeTemplate)
  if (!sig) {
    return null
  }
  return buildSolutionDriver(sig)
}

/** 驱动尾部：极简 JSON 解析 + 类型转换 + 输出格式化。与后端 DRIVER_FOOTER 逐字一致。 */
const DRIVER_FOOTER = `    }

    // ===== 平台测量：退出时把本用例的堆峰值（KB）写到 <输入文件路径>.mem =====
    // 走 shutdown hook 而不是在 main 末尾直接写：用户代码自己 System.exit 时也能落盘。
    // 被 SIGKILL（超时/OOM）的用例不写文件，沙箱据此把该用例内存记为未知。
    private static void reportHeapPeakOnExit(String[] args) {
        java.lang.Runtime.getRuntime().addShutdownHook(new java.lang.Thread(() -> writeHeapPeak(args)));
    }

    private static void writeHeapPeak(String[] args) {
        try {
            if (args.length == 0) { return; }
            long peak = 0L;
            for (java.lang.management.MemoryPoolMXBean pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getType() == java.lang.management.MemoryType.HEAP) { peak += pool.getPeakUsage().getUsed(); }
            }
            java.nio.file.Files.writeString(java.nio.file.Path.of(args[0] + ".mem"), String.valueOf(peak / 1024));
        } catch (Throwable ignored) {
            // 测量失败绝不能影响判题：stdout 已产出，缺的只是内存数字
        }
    }

    // ===== 极简 JSON 解析：仅依赖 JDK，用于把输入文件解析成参数（输入为 JSON 数组） =====
    private static Object parse(String s) {
        int[] i = {0};
        skipWs(s, i);
        return parseValue(s, i);
    }
    private static void skipWs(String s, int[] i) {
        while (i[0] < s.length()) {
            char c = s.charAt(i[0]);
            if (c == ' ' || c == '\\t' || c == '\\n' || c == '\\r') { i[0]++; } else { break; }
        }
    }
    private static Object parseValue(String s, int[] i) {
        skipWs(s, i);
        char c = s.charAt(i[0]);
        if (c == '[') { return parseArray(s, i); }
        if (c == '{') { return parseObject(s, i); }
        if (c == '"') { return parseString(s, i); }
        if (c == 't' || c == 'f') { return parseBool(s, i); }
        if (c == 'n') { i[0] += 4; return null; }
        return parseNumber(s, i);
    }
    private static java.util.List<Object> parseArray(String s, int[] i) {
        java.util.List<Object> list = new java.util.ArrayList<>();
        i[0]++; // skip '['
        skipWs(s, i);
        if (i[0] < s.length() && s.charAt(i[0]) == ']') { i[0]++; return list; }
        while (true) {
            list.add(parseValue(s, i));
            skipWs(s, i);
            if (i[0] >= s.length()) { break; }
            char c = s.charAt(i[0]);
            if (c == ',') { i[0]++; continue; }
            if (c == ']') { i[0]++; break; }
            break;
        }
        return list;
    }
    private static java.util.Map<String, Object> parseObject(String s, int[] i) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        i[0]++; // skip '{'
        skipWs(s, i);
        if (i[0] < s.length() && s.charAt(i[0]) == '}') { i[0]++; return map; }
        while (true) {
            skipWs(s, i);
            String key = parseString(s, i);
            skipWs(s, i);
            if (i[0] < s.length() && s.charAt(i[0]) == ':') { i[0]++; }
            Object val = parseValue(s, i);
            map.put(key, val);
            skipWs(s, i);
            if (i[0] >= s.length()) { break; }
            char c = s.charAt(i[0]);
            if (c == ',') { i[0]++; continue; }
            if (c == '}') { i[0]++; break; }
            break;
        }
        return map;
    }
    private static String parseString(String s, int[] i) {
        StringBuilder sb = new StringBuilder();
        i[0]++; // skip opening quote
        while (i[0] < s.length()) {
            char c = s.charAt(i[0]);
            if (c == '\\\\') {
                i[0]++; char e = s.charAt(i[0]);
                switch (e) {
                    case 'n': sb.append('\\n'); break;
                    case 't': sb.append('\\t'); break;
                    case 'r': sb.append('\\r'); break;
                    case 'b': sb.append('\\b'); break;
                    case 'f': sb.append('\\f'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i[0] + 1, i[0] + 5), 16)); i[0] += 4; break;
                    default: sb.append(e);
                }
                i[0]++;
            } else if (c == '"') {
                i[0]++; break;
            } else { sb.append(c); i[0]++; }
        }
        return sb.toString();
    }
    private static Object parseNumber(String s, int[] i) {
        int start = i[0];
        if (i[0] < s.length() && (s.charAt(i[0]) == '-' || s.charAt(i[0]) == '+')) { i[0]++; }
        while (i[0] < s.length()) {
            char c = s.charAt(i[0]);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { i[0]++; } else { break; }
        }
        String num = s.substring(start, i[0]);
        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) { return Double.parseDouble(num); }
        long v = Long.parseLong(num);
        if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) { return Integer.valueOf((int) v); }
        return Long.valueOf(v);
    }
    private static boolean parseBool(String s, int[] i) {
        if (s.charAt(i[0]) == 't') { i[0] += 4; return true; }
        i[0] += 5; return false;
    }

    // ===== 类型转换：从解析出的 Object 转成方法实参 =====
    private static int toInt(Object o) { return ((Number) o).intValue(); }
    private static long toLong(Object o) { return ((Number) o).longValue(); }
    private static double toDouble(Object o) { return ((Number) o).doubleValue(); }
    private static float toFloat(Object o) { return ((Number) o).floatValue(); }
    private static boolean toBoolean(Object o) { return (Boolean) o; }
    private static char toChar(Object o) {
        if (o instanceof String) { String s = (String) o; return s.isEmpty() ? '\\0' : s.charAt(0); }
        return (Character) o;
    }
    private static byte toByte(Object o) { return ((Number) o).byteValue(); }
    private static short toShort(Object o) { return ((Number) o).shortValue(); }
    private static String toStr(Object o) { return o == null ? null : o.toString(); }
    private static int[] toIntArray(java.util.List<?> l) {
        int[] a = new int[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).intValue(); }
        return a;
    }
    private static long[] toLongArray(java.util.List<?> l) {
        long[] a = new long[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).longValue(); }
        return a;
    }
    private static double[] toDoubleArray(java.util.List<?> l) {
        double[] a = new double[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).doubleValue(); }
        return a;
    }
    private static float[] toFloatArray(java.util.List<?> l) {
        float[] a = new float[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).floatValue(); }
        return a;
    }
    private static boolean[] toBoolArray(java.util.List<?> l) {
        boolean[] a = new boolean[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = (Boolean) l.get(k); }
        return a;
    }
    private static char[] toCharArray(java.util.List<?> l) {
        char[] a = new char[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = toChar(l.get(k)); }
        return a;
    }
    private static byte[] toByteArray(java.util.List<?> l) {
        byte[] a = new byte[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).byteValue(); }
        return a;
    }
    private static short[] toShortArray(java.util.List<?> l) {
        short[] a = new short[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = ((Number) l.get(k)).shortValue(); }
        return a;
    }
    private static String[] toStrArray(java.util.List<?> l) {
        String[] a = new String[l.size()];
        for (int k = 0; k < l.size(); k++) { a[k] = toStr(l.get(k)); }
        return a;
    }
    private static java.util.List<Integer> toIntList(java.util.List<?> l) {
        java.util.List<Integer> r = new java.util.ArrayList<>(l.size());
        for (Object o : l) { r.add(((Number) o).intValue()); }
        return r;
    }
    private static java.util.List<Long> toLongList(java.util.List<?> l) {
        java.util.List<Long> r = new java.util.ArrayList<>(l.size());
        for (Object o : l) { r.add(((Number) o).longValue()); }
        return r;
    }
    private static java.util.List<Double> toDoubleList(java.util.List<?> l) {
        java.util.List<Double> r = new java.util.ArrayList<>(l.size());
        for (Object o : l) { r.add(((Number) o).doubleValue()); }
        return r;
    }
    private static java.util.List<Float> toFloatList(java.util.List<?> l) {
        java.util.List<Float> r = new java.util.ArrayList<>(l.size());
        for (Object o : l) { r.add(((Number) o).floatValue()); }
        return r;
    }
    private static java.util.List<Boolean> toBoolList(java.util.List<?> l) {
        java.util.List<Boolean> r = new java.util.ArrayList<>(l.size());
        for (Object o : l) { r.add((Boolean) o); }
        return r;
    }
    private static java.util.List<String> toStrList(java.util.List<?> l) {
        java.util.List<String> r = new java.util.ArrayList<>(l.size());
        for (Object o : l) { r.add(toStr(o)); }
        return r;
    }

    // ===== 输出格式化：与判题标准答案字符串对齐 =====
    private static String fmt(Object o) {
        if (o == null) { return "null"; }
        if (o instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) o;
            StringBuilder sb = new StringBuilder("[");
            for (int k = 0; k < list.size(); k++) { if (k > 0) { sb.append(","); } sb.append(fmt(list.get(k))); }
            return sb.append("]").toString();
        }
        if (o.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(o);
            StringBuilder sb = new StringBuilder("[");
            for (int k = 0; k < len; k++) { if (k > 0) { sb.append(","); } sb.append(fmt(java.lang.reflect.Array.get(o, k))); }
            return sb.append("]").toString();
        }
        if (o instanceof Character) { return String.valueOf((Character) o); }
        if (o instanceof String) { return (String) o; }
        return String.valueOf(o);
    }
}
`
