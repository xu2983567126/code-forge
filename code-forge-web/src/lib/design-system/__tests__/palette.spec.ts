import { afterEach, describe, expect, it, vi } from 'vitest'
import { readCssColor, SOLARIZED_PALETTE } from '../palette'

afterEach(() => {
  vi.unstubAllGlobals()
})

/** 模拟"无 DOM"：readCssColor 必须走 canonical fallback 分支。 */
function withoutDom() {
  vi.stubGlobal('document', undefined)
  vi.stubGlobal('getComputedStyle', undefined)
}

/** 模拟浏览器：documentElement 的计算值来自给定制表。 */
function withComputedStyles(table: Record<string, string>) {
  vi.stubGlobal('document', { documentElement: {} })
  vi.stubGlobal('getComputedStyle', () => ({
    getPropertyValue: (name: string) => table[name] ?? ''
  }))
}

describe('palette 运行时桥接', () => {
  it('P1 无 DOM 时原样返回 canonical 回落值', () => {
    withoutDom()
    expect(readCssColor('--background', SOLARIZED_PALETTE.base3)).toBe('#e3e1d1')
    expect(readCssColor('var(--background)', SOLARIZED_PALETTE.base03)).toBe('#1c2412')
  })

  it('P2 非 canonical 回落值是编程错误，必须拒绝', () => {
    withoutDom()
    expect(() => readCssColor('--background', '#ffffff' as never)).toThrow(TypeError)
  })

  it('P3 有 DOM 时优先取计算值，未定义的属性回落到 fallback', () => {
    withComputedStyles({
      '--background': '  #a2afa9 ',
      '--solarized-base03': '#1c2412'
    })
    expect(readCssColor('--background', SOLARIZED_PALETTE.base3)).toBe('#a2afa9')
    expect(readCssColor('var(--background)', SOLARIZED_PALETTE.base3)).toBe('#a2afa9')
    expect(readCssColor('--missing', SOLARIZED_PALETTE.base3)).toBe('#e3e1d1')
  })

  it('P4 调色板固定为 16 个 6 位十六进制值', () => {
    const entries = Object.entries(SOLARIZED_PALETTE)
    expect(entries).toHaveLength(16)
    for (const [key, value] of entries) {
      expect(value, `${key} 必须是 6 位十六进制`).toMatch(/^#[0-9a-f]{6}$/)
    }
    expect(new Set(entries.map(([, value]) => value)).size).toBe(16)
  })
})
