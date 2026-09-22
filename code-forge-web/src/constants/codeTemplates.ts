/**
 * 判题代码模板库（LeetCode 式核心代码模式）。
 *
 * 题目统一采用 Solution + 方法签名模板：用户只实现 Solution 里的方法，
 * 驱动代码（public class Main，含 stdin 解析与输出格式化）由后端按方法签名自动生成
 * （见后端 CodeTemplateGenerator）。前端不再维护 stdin/stdout 的 IO 模板——
 * 沙箱只编译运行后端拼装后的 Main.java。
 *
 * 模板库当前仅作为「题级 codeTemplate 缺失时的兜底占位」与模板弹窗的极简内容存在；
 * 核心代码模式下做题页隐藏模板弹窗，用户直接拿到题目自带的 Solution 骨架。
 */

export type TemplateCategory = 'basic'

export interface CodeTemplate {
  id: string
  name: string
  description: string
  /** 对齐 LANGUAGE_OPTIONS 的 value */
  language: string
  category: TemplateCategory
  code: string
}

/** 本工程支持的「模板语言」集合。当前只有 Java（沙箱仅编译运行 Java）。 */
export const SUPPORTED_TEMPLATE_LANGUAGES: string[] = ['java']

/** 默认骨架（题级 codeTemplate 缺失时回退）：核心代码模式的 Solution 占位（正常不应命中，所有题均带骨架）。 */
const DEFAULT_JAVA_TEMPLATE: CodeTemplate = {
  id: 'java-solution',
  name: 'Java Solution 骨架',
  description: '实现 Solution 中的指定方法即可，输入解析与输出格式化由系统驱动处理。',
  language: 'java',
  category: 'basic',
  code: ``
}

export const CODE_TEMPLATES: CodeTemplate[] = [DEFAULT_JAVA_TEMPLATE]

const CATEGORY_LABELS: Record<TemplateCategory, string> = {
  basic: '基础'
}

/** 模板分类清单（供弹窗分组展示）。 */
export function getTemplateCategories(): { value: TemplateCategory; label: string }[] {
  return (Object.keys(CATEGORY_LABELS) as TemplateCategory[]).map((value) => ({
    value,
    label: CATEGORY_LABELS[value]
  }))
}

/** 按语言取模板；语言不在支持集合内返回空数组（避免暴露跑不起来的死模板）。 */
export function getTemplatesByLanguage(language: string): CodeTemplate[] {
  if (!SUPPORTED_TEMPLATE_LANGUAGES.includes(language)) {
    return []
  }
  return CODE_TEMPLATES.filter((t) => t.language === language)
}

/** 按分类取模板。 */
export function getTemplatesByCategory(category: TemplateCategory): CodeTemplate[] {
  return CODE_TEMPLATES.filter((t) => t.category === category)
}

/** 按 id 取单个模板。 */
export function getTemplateById(id: string): CodeTemplate | undefined {
  return CODE_TEMPLATES.find((t) => t.id === id)
}

/** 该语言是否有可用模板。 */
export function hasTemplatesForLanguage(language: string): boolean {
  return getTemplatesByLanguage(language).length > 0
}

/** 默认预填骨架（题级模板缺失时回退）。 */
export function getDefaultTemplate(language: string): CodeTemplate | undefined {
  return getTemplatesByLanguage(language).find((t) => t.id === DEFAULT_JAVA_TEMPLATE.id)
}
