import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'
import { SOLARIZED_PALETTE } from '../palette'

// 契约：第一方源码只能通过设计令牌取色，不得内联颜色字面量。
// 扫描范围 = src/ 全体（排除测试、生成物、第三方目录）。
// 新增合法例外必须登记到 ALLOWED_FILES 并写明理由；不要为自己的文件放行。
// 路径基于 vitest 的运行根（项目根），不依赖 import.meta.url（转换后非 file 协议）；
// ALLOWED_FILES 的键是相对项目根的路径，与 findings 的键同基准。
const PROJECT_ROOT = process.cwd()
const SRC_ROOT = join(PROJECT_ROOT, 'src')

const SCAN_EXTENSIONS = /\.(ts|tsx|vue|js|mjs|jsx|glsl|css|svg)$/
const TEST_FILE = /\.(test|spec)\.[cm]?[jt]sx?$/
const EXCLUDED_SEGMENTS = ['node_modules', 'dist', 'coverage', '__tests__', 'vendor', 'generated']

const HEX_RE = /#[0-9a-fA-F]{3,4}\b|#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{8}\b/g
const NUM_HEX_RE = /\b0x[0-9a-fA-F]{6}\b/g
const DIRECT_COLOR_RE = /\b(?:rgb|rgba|hsl|hsla|oklch|oklab)\((?!var\()/g
const TAILWIND_COLOR_RE =
  /\b(?:text|bg|border|ring|fill|stroke|from|to|via|decoration)-(?:red|blue|green|yellow|orange|purple|violet|pink|cyan|teal|amber|emerald|indigo|slate|gray|grey|neutral|black|white|zinc|stone|lime|sky|rose|fuchsia)(?:-\d{2,3})?(?:\/\d{1,3})?\b/g
const LEGACY_THEME_TOKEN_RE =
  /(?:--(?:silver|accent-electric(?:-glow)?|terminal-(?:green|amber|red|cyan|purple|blue))(?:-\d+)?|\b(?:text|bg|border|ring|fill|stroke|decoration)-silver-\d+\b)/g

/** 注释与 URL 不是运行时颜色：先剥离，扫描器只看渲染器真能消费的字面量。 */
function stripComments(source: string): string {
  return source
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/(^|[^:\\/'"])\/\/[^\n]*/g, '$1')
}

/** 返回源码里所有越界的颜色字面量（去重、排序）。 */
function findColorLiterals(source: string): string[] {
  const stripped = stripComments(source)
  const flagged = new Set<string>()
  for (const match of stripped.matchAll(HEX_RE)) flagged.add(match[0].toLowerCase())
  for (const match of stripped.matchAll(NUM_HEX_RE)) flagged.add(match[0].toLowerCase())
  for (const match of stripped.matchAll(DIRECT_COLOR_RE)) flagged.add(match[0].toLowerCase())
  for (const match of stripped.matchAll(TAILWIND_COLOR_RE)) flagged.add(match[0].toLowerCase())
  for (const match of stripped.matchAll(LEGACY_THEME_TOKEN_RE)) flagged.add(match[0].toLowerCase())
  return [...flagged].sort()
}

/**
 * 显式例外。`literals: 'any'` 完全豁免（该文件不属于本主题）；列出字面量则钉死取值。
 * 所有未登记的文件都不允许出现任何颜色字面量。
 */
const ALLOWED_FILES: Record<string, { reason: string; literals: readonly string[] | 'any' }> = {
  'src/style.css': {
    reason: 'CSS 唯一权威：16 个 canonical 原始色 + 设计系统自有的 5 个派生色',
    literals: [
      ...Object.values(SOLARIZED_PALETTE),
      '#19220e',
      '#3f683b',
      '#6e5b28',
      '#92b3cf',
      '#f7f6f0'
    ]
  },
  'src/lib/design-system/palette.ts': {
    reason: 'canonical 运行时调色板桥接，16 个原始色的唯一 JS 副本',
    literals: Object.values(SOLARIZED_PALETTE)
  },
  'src/assets/vite.svg': {
    reason: 'Vite 官方 logo 资产，非本主题所有',
    literals: 'any'
  },
  'src/assets/vue.svg': {
    reason: 'Vue 官方 logo 资产，非本主题所有',
    literals: 'any'
  }
}

/** 已知欠账：先钉住再逐步迁移。当前为空 —— 不要往里加新条目。 */
const DEBT_BASELINE: Record<string, readonly string[]> = {}

function scanFirstPartySource(): Record<string, string[]> {
  const files: string[] = []
  const walk = (dir: string) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith('.')) continue
      const path = join(dir, entry.name)
      if (EXCLUDED_SEGMENTS.some((segment) => path.split(/[/\\]/).includes(segment))) continue
      if (entry.isDirectory()) walk(path)
      else if (SCAN_EXTENSIONS.test(entry.name) && !TEST_FILE.test(entry.name)) files.push(path)
    }
  }
  walk(SRC_ROOT)

  const findings: Record<string, string[]> = {}
  for (const file of files.sort()) {
    const key = relative(PROJECT_ROOT, file).split('\\').join('/')
    const exception = ALLOWED_FILES[key]
    if (exception?.literals === 'any') continue
    const flagged = findColorLiterals(readFileSync(file, 'utf8'))
    const allowed = new Set(exception?.literals ?? [])
    const violations = flagged.filter((literal) => !allowed.has(literal))
    if (violations.length) findings[key] = violations
  }
  return findings
}

describe('颜色契约扫描器自身的正确性', () => {
  it('S1 能抓到内联 hex（3/6/8 位）与 0x 形式的颜色', () => {
    expect(findColorLiterals('const c = "#ff0000"')).toContain('#ff0000')
    expect(findColorLiterals('const c = "#fff"')).toContain('#fff')
    expect(findColorLiterals('const c = "#ff000080"')).toContain('#ff000080')
    expect(findColorLiterals('const c = 0x1c2412')).toContain('0x1c2412')
  })

  it('S2 能抓到颜色函数与 Tailwind 调色板类', () => {
    expect(findColorLiterals('style="color: rgba(0,0,0,.5)"')).toContain('rgba(')
    expect(findColorLiterals('style="color: oklch(0.5 0 0)"')).toContain('oklch(')
    expect(findColorLiterals('class="text-red-600"')).toContain('text-red-600')
    expect(findColorLiterals('class="bg-slate-800/50"')).toContain('bg-slate-800/50')
  })

  it('S3 能抓到已废弃的历史主题令牌', () => {
    expect(findColorLiterals('class="text-silver-500"')).toContain('text-silver-500')
    expect(findColorLiterals('color: var(--terminal-green)')).toContain('--terminal-green')
  })

  it('S4 不误报令牌引用、注释与 URL', () => {
    expect(findColorLiterals('class="bg-primary text-foreground-strong"')).toEqual([])
    expect(findColorLiterals('color: var(--chart-series-1)')).toEqual([])
    expect(findColorLiterals('/* 旧值 #ff0000 已迁移 */')).toEqual([])
    expect(findColorLiterals('// 见 https://example.com/#fff')).toEqual([])
    expect(findColorLiterals('font-family: "Noto Sans SC"')).toEqual([])
  })
})

describe('颜色契约：第一方源码不得内联颜色', () => {
  it('S5 除登记例外外，src/ 下不存在任何颜色字面量', () => {
    const findings = scanFirstPartySource()
    const detail = Object.entries(findings)
      .map(([file, literals]) => `  ${file}: ${literals.join(', ')}`)
      .join('\n')
    expect(
      Object.keys(findings),
      `发现未登记的颜色字面量：\n${detail}\n` +
        '请改用设计令牌（bg-primary / var(--status-error) 等）；' +
        '确属外部品牌或数据色时登记到 ALLOWED_FILES 并写明理由。'
    ).toEqual([])
  })

  it('S6 登记文件不得携带超出登记范围的字面量', () => {
    for (const [file, entry] of Object.entries(ALLOWED_FILES)) {
      if (entry.literals === 'any') continue
      const source = readFileSync(join(PROJECT_ROOT, file), 'utf8')
      const extra = findColorLiterals(source).filter(
        (literal) => !entry.literals.includes(literal)
      )
      expect(extra, `${file}（${entry.reason}）出现了未登记的字面量`).toEqual([])
    }
  })

  it('S7 欠账基线只允许收缩', () => {
    for (const [file, pinned] of Object.entries(DEBT_BASELINE)) {
      const source = readFileSync(join(PROJECT_ROOT, file), 'utf8')
      const extra = findColorLiterals(source).filter((literal) => !pinned.includes(literal))
      expect(extra, `${file} 新增了未钉住的字面量，应改用令牌`).toEqual([])
    }
  })
})
