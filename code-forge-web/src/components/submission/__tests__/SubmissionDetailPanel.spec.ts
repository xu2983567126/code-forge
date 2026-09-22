import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { getSubmissionVoById } from '@generated'
import SubmissionDetailPanel from '../SubmissionDetailPanel.vue'

vi.mock('@generated', () => ({ getSubmissionVoById: vi.fn() }))

// Monaco 在 jsdom 里会去拉 loader，这里只关心刷新策略，用桩替换编辑器
vi.mock('@/components/CodeEditor.vue', () => ({
  default: { template: '<div data-testid="code-editor" />' },
}))

const base = {
  id: '2101562819019362307',
  language: 'java',
  code: 'class Solution {}',
  createTime: '2026-09-21 10:00:00',
  judgeInfo: { message: 'ACCEPTED', time: 12, memory: 2048, caseResults: [] },
}

const ok = (data: unknown) => ({ data: { code: 0, data, message: 'ok' } })

describe('SubmissionDetailPanel 刷新策略', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('判题中按间隔轮询，进入终态后停止', async () => {
    // 第一次：判题中；第二次起：成功（终态）
    vi.mocked(getSubmissionVoById)
      .mockResolvedValueOnce(ok({ ...base, status: 1 }) as never)
      .mockResolvedValue(ok({ ...base, status: 2 }) as never)

    const wrapper = mount(SubmissionDetailPanel, {
      props: { submissionId: '2101562819019362307' },
    })
    await vi.advanceTimersByTimeAsync(0)
    await wrapper.vm.$nextTick()
    expect(getSubmissionVoById).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('判题中')

    // 推进两个轮询周期：第一个周期仍在判题中，应继续拉；拿到终态后不再拉
    await vi.advanceTimersByTimeAsync(2000)
    await wrapper.vm.$nextTick()
    expect(getSubmissionVoById).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('成功')

    await vi.advanceTimersByTimeAsync(6000)
    expect(getSubmissionVoById).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('终态提交不启动轮询', async () => {
    vi.mocked(getSubmissionVoById).mockResolvedValue(ok({ ...base, status: 2 }) as never)
    const wrapper = mount(SubmissionDetailPanel, {
      props: { submissionId: '2101562819019362307' },
    })
    await vi.advanceTimersByTimeAsync(0)
    await wrapper.vm.$nextTick()

    await vi.advanceTimersByTimeAsync(10000)
    expect(getSubmissionVoById).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('无权限时给出明确文案且不轮询', async () => {
    vi.mocked(getSubmissionVoById).mockResolvedValue({
      data: { code: 40101, data: null, message: '无权查看' },
    } as never)
    const wrapper = mount(SubmissionDetailPanel, {
      props: { submissionId: '2101562819019362307' },
    })
    await vi.advanceTimersByTimeAsync(0)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('无权查看该提交')
    await vi.advanceTimersByTimeAsync(10000)
    expect(getSubmissionVoById).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})
