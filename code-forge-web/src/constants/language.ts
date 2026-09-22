/**
 * 编程语言选项。
 *
 * value 同时是两种东西：后端 `submission.language` / `spjLanguage` 的取值，
 * 以及 Monaco 的 language id —— 所以不能随便改成中文或大写。
 *
 * <b>当前仅 Java 可用</b>：沙箱只编译运行 Java（见沙箱 `CodeSandboxTemplate` 的
 * `MAIN_CLASS_FILE`）/`javac Main.java`/`java Main`），选其他语言必然 COMPILE_ERROR。
 * 语言下拉只暴露 java，避免给用户一个跑不起来的死选项；恢复多语言前置条件是补沙箱多语言模板。
 */
export interface LanguageOption {
  label: string
  value: string
}

export const LANGUAGE_OPTIONS: LanguageOption[] = [
  { label: 'Java', value: 'java' }
]

export const DEFAULT_LANGUAGE = 'java'
