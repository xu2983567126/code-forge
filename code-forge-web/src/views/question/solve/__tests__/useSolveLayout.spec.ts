import { createApp, h, reactive } from 'vue'
import { describe, expect, it, vi, beforeEach } from 'vitest'

/** 路由侧的可变参数：模拟 vue-router 的 params 随导航变化。 */
const params = reactive<Record<string, string | undefined>>({ id: '2101562819019362306' })
const replace = vi.fn(async () => undefined)

vi.mock('vue-router', () => ({
  useRoute: () => ({ params, query: {}, name: '在线做题' }),
  useRouter: () => ({ replace }),
}))

import { useSolveLayout } from '../composables/useSolveLayout'

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

describe('useSolveLayout · tab 与路由同步', () => {
  beforeEach(() => {
    replace.mockClear()
    params.tab = undefined
  })

  it('初始 tab 缺省为描述；路由带 tab 时以路由为准', () => {
    params.tab = 'results'
    const { result, unmount } = withSetup(() => useSolveLayout())
    expect(result.mobileTab.value).toBe('results')
    unmount()
  })

  it('切换 tab 会写回路由（replace，不堆历史）', async () => {
    const { result, unmount } = withSetup(() => useSolveLayout())
    expect(result.mobileTab.value).toBe('description')

    result.mobileTab.value = 'submissions'
    await Promise.resolve()
    await Promise.resolve()

    expect(replace).toHaveBeenCalledTimes(1)
    expect(replace.mock.calls[0][0].params.tab).toBe('submissions')
    unmount()
  })

  it('路由变化会带动本地 tab（且不反向再写一次路由）', async () => {
    const { result, unmount } = withSetup(() => useSolveLayout())

    params.tab = 'code'
    await Promise.resolve()
    await Promise.resolve()

    expect(result.mobileTab.value).toBe('code')
    // 由路由驱动的更新不得再触发一次 replace —— 否则两个 watch 会成环
    expect(replace).not.toHaveBeenCalled()
    unmount()
  })

  it('非法 tab 参数回落到描述', () => {
    params.tab = 'not-a-tab'
    const { result, unmount } = withSetup(() => useSolveLayout())
    expect(result.mobileTab.value).toBe('description')
    unmount()
  })
})
