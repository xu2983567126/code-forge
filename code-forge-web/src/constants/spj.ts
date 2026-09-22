/**
 * 特判（SPJ）相关常量。
 *
 * 契约来自后端 `SandboxSpecialJudgeExecutor`：逐用例调用一次特判程序，
 * stdin = 测试输入，argv = [标准答案文件路径, 用户输出文件路径]，退出码决定该用例结论。
 * argv 传的是沙箱物化出的文件路径而非内容本身，故不受单参 128KB 长度上限约束。
 */

export interface CompareModeOption {
  /** 后端 CompareMode 枚举名 */
  value: string
  label: string
  /** 卡片里的一行说明，解释「算不算对」的口径 */
  hint: string
}

export const COMPARE_MODE_OPTIONS: CompareModeOption[] = [
  {
    value: 'STANDARD',
    label: '标准比对',
    hint: '忽略首尾空白、逐行去行尾空白、忽略末尾空行；仅内部空白不同判「格式错误」。'
  },
  { value: 'STRICT', label: '严格比对', hint: '仅归一化换行符，整串精确比较。' },
  {
    value: 'FLOAT',
    label: '浮点容差',
    hint: '按 epsilon 容差逐数值比较，适合答案含浮点数的题目。'
  },
  {
    value: 'SPJ',
    label: '特判（SPJ）',
    hint: '由题目自带的判定程序决定对错，需填写下方特判程序。'
  }
]

export const DEFAULT_COMPARE_MODE = 'STANDARD'

/** 特判程序的退出码约定（其余退出码全部记系统错误）。 */
export const SPJ_EXIT_CODES = [
  { code: 0, text: '通过' },
  { code: 1, text: '答案错误' },
  { code: 2, text: '格式错误' }
] as const

/**
 * 各语言的特判程序模板，key 对齐 LANGUAGE_OPTIONS 的 value。
 *
 * <b>当前仅 Java 可用</b>：沙箱只编译运行 Java（见沙箱 `CodeSandboxTemplate` 的
 * `MAIN_CLASS_FILE`/`javac Main.java`/`java Main`），选其他语言必然 COMPILE_ERROR，
 * 故 SPJ 模板只保留 java，避免给用户一个跑不起来的死选项。
 */
export const SPJ_TEMPLATES: Record<string, string> = {
  java: `import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {
    // stdin = 测试输入，args[0] = 标准答案文件路径，args[1] = 用户输出文件路径
    public static void main(String[] args) throws Exception {
        String expected = args.length > 0 ? Files.readString(Path.of(args[0])) : "";
        String actual = args.length > 1 ? Files.readString(Path.of(args[1])) : "";
        Scanner in = new Scanner(System.in);

        // TODO: 按题目语义判定
        System.exit(expected.trim().equals(actual.trim()) ? 0 : 1);
    }
}
`
}
