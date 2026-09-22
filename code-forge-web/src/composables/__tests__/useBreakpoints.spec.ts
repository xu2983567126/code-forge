import { createApp, h } from 'vue'
import { describe, expect, it } from 'vitest'
import { useBreakpoints, BREAKPOINTS } from '../useBreakpoints'

/** onMounted/onUnmounted 需要组件实例，把 composable 放进一个空组件里跑。 */
function withSetup<T>(fn: () => T): { result: T; unmount: () => void } {
  let result!: T
  const app = createApp({
    setup() {
      result = fn()
      return () => h('div')
    },
  })
  app.mount(document.createElement('div'))
  return { result, unmount: () => app.unmount() }
}

describe('useBreakpoints', () => {
  it('按 Tailwind 的临界值分档（md 以下即移动端）', () => {
    const { result, unmount } = withSetup(() => useBreakpoints())
    // jsdom 默认视口 1024 → 桌面
    expect(result.isMobile.value).toBe(false)
    expect(result.isDesktop.value).toBe(true)
    expect(BREAKPOINTS.md).toBe(768)
    unmount()
  })

  it('resize 后断点跟随更新', async () => {
    const { result, unmount } = withSetup(() => useBreakpoints())
    expect(result.isMobile.value).toBe(false)

    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      value: 500,
    })
    window.dispatchEvent(new Event('resize'))
    await Promise.resolve()

    expect(result.windowWidth.value).toBe(500)
    expect(result.isMobile.value).toBe(true)
    unmount()
  })
})
