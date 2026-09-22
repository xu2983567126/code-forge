import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { SOLARIZED_PALETTE } from '../palette'

// 契约对象是 src/style.css —— Garden 设计系统的 CSS 唯一权威。
// 本文件把"当前视觉语言"钉成可执行断言：改令牌时要么按预期更新断言，
// 要么会被拦下重新审视。见 docs/设计契约-Garden设计系统.md。
// 路径基于 vitest 的运行根（项目根），不依赖 import.meta.url（转换后非 file 协议）。
const CSS = readFileSync(join(process.cwd(), 'src', 'style.css'), 'utf8')

const DARK_MARKER = 'Garden Design System - Dark'
const darkStart = CSS.indexOf(DARK_MARKER)
if (darkStart === -1) {
  throw new Error('style.css 缺少 Night Garden（dark）段落标记')
}
/** light 段：文件开头到 dark 标记；dark 段：dark 标记到文件尾。 */
const lightCss = CSS.slice(0, darkStart)
const darkCss = CSS.slice(darkStart)

/** 取某个段里第一个 `--name: value;` 的值（值可跨行，如 color-mix）。 */
function token(source: string, name: string): string {
  const match = new RegExp(`--${name}:\\s*([^;]+);`).exec(source)
  if (!match) {
    throw new Error(`style.css 中找不到令牌 --${name}`)
  }
  return match[1].replace(/\s+/g, ' ').trim()
}

/** 注释不是运行时颜色（与 color-contract 扫描器同一规则），比对前先剥离。 */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/(^|[^:\\/'"])\/\/[^\n]*/g, '$1')
}

function luminance(hex: string): number {
  const channels = [1, 3, 5]
    .map((offset) => Number.parseInt(hex.slice(offset, offset + 2), 16) / 255)
    .map((value) => (value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4))
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}

/** WCAG 2.x 对比度。 */
function contrast(first: string, second: string): number {
  const high = Math.max(luminance(first), luminance(second))
  const low = Math.min(luminance(first), luminance(second))
  return (high + 0.05) / (low + 0.05)
}

/** 复刻 CSS 的 color-mix(in srgb, accent ratio%, base03)，用于校验状态标记色。 */
function mixSrgb(accent: string, surface: string, ratio: number): string {
  return (
    '#' +
    [1, 3, 5]
      .map((offset) => {
        const a = Number.parseInt(accent.slice(offset, offset + 2), 16)
        const b = Number.parseInt(surface.slice(offset, offset + 2), 16)
        return Math.round(a * ratio + b * (1 - ratio))
          .toString(16)
          .padStart(2, '0')
      })
      .join('')
  )
}

describe('设计契约 · 调色板与 style.css 一致', () => {
  it('C1 运行时调色板的 16 个值逐字等于 style.css 的原始刻度', () => {
    const cssValues = new Map(
      [...CSS.matchAll(/--solarized-([a-z0-9]+):\s*(#[0-9a-f]{6})/gi)].map(([, key, value]) => [
        key,
        value.toLowerCase()
      ])
    )
    expect(Object.keys(SOLARIZED_PALETTE)).toHaveLength(16)
    for (const [key, value] of Object.entries(SOLARIZED_PALETTE)) {
      expect(value, `SOLARIZED_PALETTE.${key} 必须等于 style.css 的 --solarized-${key}`).toBe(
        cssValues.get(key)
      )
    }
  })

  it('C2 style.css 只允许 canonical 原始色与已登记的派生色', () => {
    // 16 个 canonical 原始刻度 + 5 个由设计系统自身拥有的派生字面量
    // （ink 文字、卡片纸色、天蓝填充、苔绿、赭石）。新增字面量必须在这里登记。
    const allowed = new Set([
      ...Object.values(SOLARIZED_PALETTE),
      '#19220e',
      '#3f683b',
      '#6e5b28',
      '#92b3cf',
      '#f7f6f0'
    ])
    const actual = new Set(
      [...stripComments(CSS).matchAll(/#[0-9a-f]{6}/gi)].map(([value]) => value.toLowerCase())
    )
    expect([...actual].sort()).toEqual([...allowed].sort())
  })
})

describe('设计契约 · 明暗语义映射', () => {
  it('C3 Light 段把语义令牌映射到 Garden 调色板', () => {
    expect(token(lightCss, 'background')).toBe('var(--solarized-base3)')
    expect(token(lightCss, 'foreground')).toBe('#19220e')
    expect(token(lightCss, 'foreground-strong')).toBe('#19220e')
    expect(token(lightCss, 'foreground-muted')).toBe('var(--solarized-base01)')
    expect(token(lightCss, 'card')).toBe('var(--garden-card)')
    expect(token(lightCss, 'primary')).toBe('var(--solarized-base01)')
    expect(token(lightCss, 'primary-foreground')).toBe('var(--garden-card)')
    expect(token(lightCss, 'primary-control')).toBe('var(--solarized-base01)')
    expect(token(lightCss, 'primary-control-foreground')).toBe('var(--garden-card)')
    expect(token(lightCss, 'secondary-foreground')).toBe('#1c2412')
    expect(token(lightCss, 'muted-foreground')).toBe('var(--solarized-base01)')
    expect(token(lightCss, 'accent-foreground')).toBe('#1c2412')
    expect(token(lightCss, 'sidebar-foreground')).toBe('var(--solarized-base03)')
    expect(token(lightCss, 'sidebar-accent-foreground')).toBe('var(--solarized-base01)')
    expect(token(lightCss, 'border-control')).toBe('var(--solarized-base00)')
    expect(token(lightCss, 'ring')).toBe('var(--solarized-base00)')
    // 交互发光跟随唯一的 focus ring，不引入第二个强调色。
    expect(token(lightCss, 'accent-glow')).toMatch(/var\(--ring\)/)
  })

  it('C4 Dark 段把控件反转为"羊皮纸压墨底"', () => {
    expect(token(darkCss, 'background')).toBe('var(--solarized-base03)')
    expect(token(darkCss, 'foreground')).toBe('var(--solarized-base2)')
    expect(token(darkCss, 'foreground-strong')).toBe('var(--solarized-base3)')
    expect(token(darkCss, 'muted-foreground')).toBe('var(--solarized-base1)')
    expect(token(darkCss, 'primary')).toBe('var(--solarized-base3)')
    expect(token(darkCss, 'primary-foreground')).toBe('#1c2412')
    expect(token(darkCss, 'primary-control')).toBe('var(--solarized-base3)')
    expect(token(darkCss, 'primary-control-foreground')).toBe('#1c2412')
    expect(token(darkCss, 'sidebar-accent-foreground')).toBe('var(--solarized-base1)')
    expect(token(darkCss, 'border-control')).toBe('var(--solarized-base0)')
    expect(token(darkCss, 'ring')).toBe('var(--solarized-base0)')
    expect(token(darkCss, 'accent-glow')).toMatch(/var\(--ring\)/)
  })

  it('C5 两态下 border-control 都不与 primary / surface-highlight 撞色', () => {
    for (const [label, source] of [
      ['Light', lightCss],
      ['Dark', darkCss]
    ] as const) {
      expect(token(source, 'border-control'), `${label} 边框不得等于 primary`).not.toBe(
        token(source, 'primary')
      )
      expect(
        token(source, 'border-control'),
        `${label} 边框不得等于 surface-highlight`
      ).not.toBe(token(source, 'surface-highlight'))
    }
  })
})

describe('设计契约 · 可读性（WCAG AA）', () => {
  it('C6 关键文字配对达到 4.5:1', () => {
    const pairs: Array<[string, string, string]> = [
      ['Light 正文/卡片', '#19220e', '#f7f6f0'],
      ['Light 正文/画布', '#19220e', '#e3e1d1'],
      ['Light 强文字/高亮面', '#19220e', '#eae8d8'],
      ['Light 次要文字', '#1c2412', '#eae8d8'],
      ['Light 侧边栏', '#1c2412', '#eae8d8'],
      ['Light 侧边栏强调', '#545c45', '#e3e1d1'],
      ['Dark 正文', '#eae8d8', '#1c2412'],
      ['Dark 抬升面', '#eae8d8', '#26301b'],
      ['Dark 侧边栏强调', '#eae8d8', '#1c2412']
    ]
    for (const [label, foreground, background] of pairs) {
      expect(contrast(foreground, background), `${label} 需达到 4.5:1`).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('C7 控件与辅助文字达到 3:1', () => {
    expect(contrast('#6a7259', '#e3e1d1')).toBeGreaterThanOrEqual(3)
    expect(contrast('#f7f6f0', '#1c2412')).toBeGreaterThanOrEqual(4.5)
    expect(contrast('#1c2412', '#f7f6f0')).toBeGreaterThanOrEqual(4.5)
    expect(contrast('#838f81', '#1c2412')).toBeGreaterThanOrEqual(3)
    expect(contrast('#f7f6f0', '#545c45'), '浅色主控件文字需达 3:1').toBeGreaterThanOrEqual(3)
    expect(contrast('#1c2412', '#e3e1d1'), '深色主控件文字需达 3:1').toBeGreaterThanOrEqual(3)
  })

  it('C8 状态标记色（混墨后）在两态小字下都可读', () => {
    // 这些比例必须与 style.css 的 --status-*-mark 定义一致。
    const markRatios: Array<[string, number]> = [
      ['#588e67', 0.6],
      ['#9c7a14', 0.65],
      ['#8f4822', 0.7],
      ['#4e7d64', 0.6],
      ['#6c71c4', 0.7]
    ]
    for (const [accent, ratio] of markRatios) {
      const mark = mixSrgb(accent, '#1c2412', ratio)
      expect(contrast(mark, '#e3e1d1'), `浅色状态标记 ${accent} 需达 4.5:1`).toBeGreaterThanOrEqual(4.5)
    }
    const darkRatios: Array<[string, number]> = [
      ['#588e67', 0.7],
      ['#9c7a14', 0.7],
      ['#8f4822', 0.55],
      ['#4e7d64', 0.7],
      ['#6c71c4', 0.7]
    ]
    for (const [accent, ratio] of darkRatios) {
      const mark = mixSrgb(accent, '#eae8d8', ratio)
      expect(contrast(mark, '#26301b'), `深色状态标记 ${accent} 需达 4.5:1`).toBeGreaterThanOrEqual(4.5)
    }
  })
})

describe('设计契约 · 几何与语言档位', () => {
  it('C9 圆角阶梯由 --radius 推导，控件 8px / 卡片 20px', () => {
    expect(token(CSS, 'radius')).toBe('0.5rem')
    expect(token(CSS, 'radius-sm')).toBe('calc(var(--radius) - 4px)')
    expect(token(CSS, 'radius-md')).toBe('calc(var(--radius) - 2px)')
    expect(token(CSS, 'radius-lg')).toBe('var(--radius)')
    expect(token(CSS, 'radius-xl')).toBe('calc(var(--radius) + 12px)')
    expect(token(CSS, 'uc-component-control-radius')).toBe('var(--radius-md)')
    expect(token(CSS, 'uc-component-card-radius')).toBe('var(--radius-lg)')
  })

  it('C10 zh-CN 与 en-US 的度量档位各自成立', () => {
    const zh = CSS.slice(CSS.indexOf(':root[lang="zh-CN"]'))
    const en = CSS.slice(CSS.indexOf(':root[lang="en-US"]'))
    expect(token(zh, 'uc-layout-control-height')).toBe('2.5rem')
    expect(token(zh, 'uc-layout-page-gutter')).toBe('1rem')
    expect(token(en, 'uc-layout-control-height')).toBe('2.25rem')
    expect(token(en, 'uc-layout-page-gutter')).toBe('1.25rem')
  })

  it('C11 排版与布局消费者所需的骨架类仍然存在', () => {
    for (const selector of ['.uc-page-main', '.uc-page-container', '.uc-page-stack', '.terminal-card']) {
      expect(CSS, `缺少 ${selector}`).toContain(`${selector} {`)
    }
    // 主题只由 .dark 类驱动（applyThemeToDOM 切换它）
    expect(CSS).toMatch(/\.dark\s*\{/)
  })
})

describe('设计契约 · 状态与图表语义', () => {
  it('C12 状态色只由 5 个语义名驱动', () => {
    expect(token(lightCss, 'status-success')).toBe('var(--solarized-green)')
    expect(token(lightCss, 'status-warning')).toBe('var(--solarized-yellow)')
    expect(token(lightCss, 'status-error')).toBe('var(--solarized-red)')
    expect(token(lightCss, 'status-info')).toBe('var(--solarized-cyan)')
    expect(token(lightCss, 'status-special')).toBe('var(--solarized-violet)')
  })

  it('C13 图表角色齐全，系列色 8 个且顺序稳定', () => {
    const expectedSeries = [
      'var(--solarized-blue)',
      'var(--status-info-mark)',
      'var(--status-success-mark)',
      'var(--solarized-magenta)',
      'var(--solarized-orange)',
      'var(--status-special-mark)',
      'var(--status-warning-mark)',
      'var(--status-error-mark)'
    ]
    expectedSeries.forEach((value, index) => {
      expect(token(CSS, `chart-series-${index + 1}`)).toBe(value)
    })
    expect(token(CSS, 'chart-status-solved')).toBe('var(--status-success-mark)')
    expect(token(CSS, 'chart-status-attempted')).toBe('var(--status-warning-mark)')
    expect(token(CSS, 'chart-status-todo')).toBe('var(--status-info-mark)')
    expect(token(CSS, 'chart-grid-color')).toBe('var(--border-subtle)')
    expect(token(CSS, 'chart-tooltip-background')).toBe('var(--popover)')
    expect(token(CSS, 'chart-tooltip-border')).toBe('var(--border-control)')
  })
})
