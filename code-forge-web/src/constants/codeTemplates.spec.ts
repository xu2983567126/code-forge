import { describe, it, expect } from 'vitest'
import {
  CODE_TEMPLATES,
  SUPPORTED_TEMPLATE_LANGUAGES,
  getTemplateCategories,
  getTemplatesByLanguage,
  getTemplatesByCategory,
  getTemplateById,
  hasTemplatesForLanguage,
  getDefaultTemplate,
  type TemplateCategory
} from './codeTemplates'

describe('codeTemplates 结构', () => {
  it('T1 每条模板字段齐全且语言为 java', () => {
    expect(CODE_TEMPLATES.length).toBeGreaterThan(0)
    for (const t of CODE_TEMPLATES) {
      expect(t.id).toBeTruthy()
      expect(t.name).toBeTruthy()
      expect(t.description).toBeTruthy()
      expect(t.language).toBe('java')
      expect(t.category).toBeTruthy()
      expect(t.code).toBeTruthy()
      // id 唯一
      const same = CODE_TEMPLATES.filter((x) => x.id === t.id)
      expect(same.length).toBe(1)
    }
  })
})

describe('codeTemplates 核心代码模式契约', () => {
  it('T2 每个模板都是 Solution 骨架（非 public 的 class Solution，无独立 Main）', () => {
    for (const t of CODE_TEMPLATES) {
      expect(t.code).toContain('class Solution')
      // 核心模式下驱动由后端生成，模板本身不得自带 public class Main
      expect(t.code).not.toContain('public class Main')
    }
  })
})

describe('codeTemplates API', () => {
  it('T3 getTemplatesByLanguage(java) 返回全部 Java 模板', () => {
    const java = getTemplatesByLanguage('java')
    expect(java.length).toBe(CODE_TEMPLATES.length)
  })

  it('T3 不支持的语言返回空数组', () => {
    expect(getTemplatesByLanguage('cpp')).toEqual([])
    expect(hasTemplatesForLanguage('cpp')).toBe(false)
  })

  it('T3 getTemplateById 命中', () => {
    expect(getTemplateById('java-solution')?.id).toBe('java-solution')
    expect(getTemplateById('not-exist')).toBeUndefined()
  })

  it('T3 getTemplateCategories 返回 1 类', () => {
    const cats = getTemplateCategories()
    expect(cats.map((c) => c.value).sort()).toEqual(['basic'])
  })

  it('T3 getDefaultTemplate(java) 返回 java-solution', () => {
    expect(getDefaultTemplate('java')?.id).toBe('java-solution')
  })

  it('SUPPORTED_TEMPLATE_LANGUAGES 仅含 java', () => {
    expect(SUPPORTED_TEMPLATE_LANGUAGES).toEqual(['java'])
  })
})

describe('codeTemplates 分类覆盖', () => {
  it('T4 basic 分类至少一条', () => {
    const cats: TemplateCategory[] = ['basic']
    for (const c of cats) {
      expect(getTemplatesByCategory(c).length).toBeGreaterThanOrEqual(1)
    }
  })
})
