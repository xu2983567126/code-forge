import { afterEach, describe, expect, it, vi } from 'vitest'
import { resolveChartPalette } from '../chartPalette'
import { SOLARIZED_PALETTE } from '../palette'

afterEach(() => {
  vi.unstubAllGlobals()
})

function withoutDom() {
  vi.stubGlobal('document', undefined)
  vi.stubGlobal('getComputedStyle', undefined)
}

function withComputedStyles(table: Record<string, string>) {
  vi.stubGlobal('document', { documentElement: {} })
  vi.stubGlobal('getComputedStyle', () => ({
    getPropertyValue: (name: string) => table[name] ?? ''
  }))
}

describe('chartPalette 角色解析', () => {
  it('K1 无 DOM 时每个角色回落到语义最接近的 canonical 值', () => {
    withoutDom()
    expect(resolveChartPalette()).toEqual({
      // --chart-series-N 依次对应 blue / info / success / magenta / orange / special / warning / error
      series: [
        SOLARIZED_PALETTE.blue,
        SOLARIZED_PALETTE.cyan,
        SOLARIZED_PALETTE.green,
        SOLARIZED_PALETTE.magenta,
        SOLARIZED_PALETTE.orange,
        SOLARIZED_PALETTE.violet,
        SOLARIZED_PALETTE.yellow,
        SOLARIZED_PALETTE.red
      ],
      statusSolved: SOLARIZED_PALETTE.green,
      statusAttempted: SOLARIZED_PALETTE.yellow,
      statusTodo: SOLARIZED_PALETTE.cyan,
      difficultyEasy: SOLARIZED_PALETTE.green,
      difficultyMedium: SOLARIZED_PALETTE.yellow,
      difficultyHard: SOLARIZED_PALETTE.red,
      grid: SOLARIZED_PALETTE.base1,
      axis: SOLARIZED_PALETTE.base01,
      background: SOLARIZED_PALETTE.base3,
      surface: SOLARIZED_PALETTE.base2,
      foreground: SOLARIZED_PALETTE.base03,
      muted: SOLARIZED_PALETTE.base01,
      tooltipBackground: SOLARIZED_PALETTE.base3,
      tooltipBorder: SOLARIZED_PALETTE.base00
    })
  })

  it('K2 系列色固定为 8 个，供 Unovis 循环取用', () => {
    withoutDom()
    const { series } = resolveChartPalette()
    expect(series).toHaveLength(8)
    expect(new Set(series).size).toBe(8)
  })

  it('K3 有 DOM 时优先取当前主题的计算值', () => {
    withComputedStyles({
      '--chart-series-1': '#46769b',
      '--chart-grid-color': 'rgba(84,92,69,0.18)',
      '--foreground-strong': '#19220e'
    })
    const palette = resolveChartPalette()
    expect(palette.series[0]).toBe('#46769b')
    expect(palette.grid).toBe('rgba(84,92,69,0.18)')
    expect(palette.foreground).toBe('#19220e')
    // 未在计算值表中的角色仍然回落
    expect(palette.series[1]).toBe(SOLARIZED_PALETTE.cyan)
  })
})
