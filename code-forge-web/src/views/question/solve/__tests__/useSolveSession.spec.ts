import { h, createApp, nextTick } from 'vue'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getQuestionVoById, submit } from '@generated'
import { runJudge } from '@/api/runJudge'
import { useSolveSession } from '../composables/useSolveSession'

vi.mock('@generated', () => ({
  getQuestionVoById: vi.fn(),
  submit: vi.fn(),
}))

vi.mock('@/api/runJudge', () => ({
  runJudge: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '2101562819019362306' } }),
}))

const QUESTION = {
  id: '2101562819019362306',
  title: '两数之和',
  content: '给定数组，返回下标。',
  tags: ['数组', '哈希表'],
  difficulty: '简单',
  examples: '[{"input":"[2,7]","output":"9"},{"input":"[1,1]","output":"2"}]',
  codeTemplate:
    'class Solution {\n    public int add(int a, int b) {\n        return a + b;\n    }\n}',
  judgeConfig: { timeLimit: 1000, memoryLimit: 128000, stackLimit: 128000 },
}

/**
 * 在真实组件 setup 里跑 composable —— provide/inject 与 onMounted 都需要组件上下文。
 * 官方测试指南的做法：挂载一个空组件，把返回值带出来。
 */
function withSetup<T>(fn: () => T): { result: T; unmount: () => void } {
  let result!: T
  const app = createApp({
    setup() {
      result = fn()
      return () => h('div')
    },
  })
  const root = document.createElement('div')
  app.mount(root)
  return { result, unmount: () => app.unmount() }
}

const ok = <T>(data: T) => ({ data: { code: 0, data, message: 'ok' } })

describe('useSolveSession 行为契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getQuestionVoById).mockResolvedValue(ok(QUESTION) as never)
  })

  it('挂载后加载题目，并按题级骨架预填编辑器', async () => {
    const { result, unmount } = withSetup(() => useSolveSession())
    await nextTick()
    await nextTick()

    expect(result.questionDetail.value?.title).toBe('两数之和')
    expect(result.code.value).toContain('class Solution')
    // 题级模板能推出驱动 → 核心代码模式
    expect(result.isCoreMode.value).toBe(true)
    unmount()
  })

  it('解析脱敏样例为可编辑副本，且输入与用例双向同步', async () => {
    const { result, unmount } = withSetup(() => useSolveSession())
    await nextTick()
    await nextTick()

    expect(result.cases.value).toHaveLength(2)
    expect(result.caseInput.value).toBe('[2,7]')

    // 用户编辑输入框 → 写回当前用例（否则运行时 input 恒空 → 沙箱阻塞超时）
    result.caseInput.value = '[3,3]'
    await nextTick()
    expect(result.cases.value[0].input).toBe('[3,3]')

    // 切换到第二个用例 → 输入框跟着变
    result.activeCase.value = 1
    await nextTick()
    expect(result.caseInput.value).toBe('[1,1]')
    unmount()
  })

  it('运行后后端判定通过 → 显示 Accepted 并切到「测试结果」', async () => {
    // 手写 runJudge 直接返回 body.data（JudgeInfo 载荷），不套 {code,data,message} 信封
    vi.mocked(runJudge).mockResolvedValue(
      {
        message: 'ACCEPTED',
        time: 12,
        memory: 2048,
        caseResults: [{ status: 'ACCEPTED', time: 12, memory: 2048, output: '9' }],
      } as never
    )
    const { result, unmount } = withSetup(() => useSolveSession())
    await nextTick()
    await nextTick()

    expect(result.testTab.value).toBe('cases')
    await result.handleRun()
    expect(result.testTab.value).toBe('results')
    // 后端判定全部通过 → 聚合结论「通过」（不再由前端平行比对）
    expect(result.verdictText.value).toBe('通过')
    unmount()
  })

  it('运行后后端判定答案错误 → Wrong Answer', async () => {
    vi.mocked(runJudge).mockResolvedValue(
      {
        message: 'WRONG_ANSWER',
        caseResults: [{ status: 'WRONG_ANSWER', output: '8' }],
      } as never
    )
    const { result, unmount } = withSetup(() => useSolveSession())
    await nextTick()
    await nextTick()

    await result.handleRun()
    expect(result.verdictText.value).toBe('答案错误')
    unmount()
  })

  it('运行无期望输出（用户自填输入）→ 聚合结论中性「已执行」', async () => {
    vi.mocked(runJudge).mockResolvedValue(
      {
        // message 为 null：全用例无期望输出，后端按中性态处理
        message: null,
        caseResults: [{ status: null, output: 'x' }],
      } as never
    )
    const { result, unmount } = withSetup(() => useSolveSession())
    await nextTick()
    await nextTick()

    result.cases.value = [{ input: 'x', expectedOutput: '' }]
    await result.handleRun()
    expect(result.verdictText.value).toBe('已执行')
    unmount()
  })

  it('提交成功后切到提交记录并选中新提交（提交闭环）', async () => {
    // 接口返回的是新提交 id（雪花字符串）
    vi.mocked(submit).mockResolvedValue(ok('2101562819019362307') as never)
    const { result, unmount } = withSetup(() => useSolveSession())
    await nextTick()
    await nextTick()

    await result.handleSubmit()
    expect(result.activeTab.value).toBe('submissions')
    expect(result.selectedSubmissionId.value).toBe('2101562819019362307')
    unmount()
  })

  it('重置代码回到题级骨架', async () => {
    const { result, unmount } = withSetup(() => useSolveSession())
    await nextTick()
    await nextTick()

    const initial = result.code.value
    result.code.value = '// 用户改坏了'
    result.resetCode()
    expect(result.code.value).toBe(initial)
    unmount()
  })
})
