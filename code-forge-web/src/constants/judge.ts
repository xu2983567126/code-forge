export const JUDGE_STATUS = {
  PENDING: { value: 0, text: '等待中', color: 'orange' },
  JUDGING: { value: 1, text: '判题中', color: 'blue' },
  SUCCESS: { value: 2, text: '成功', color: 'green' },
  FAILED: { value: 3, text: '失败', color: 'red' }
} as const

// 判题结论唯一真相源 = 后端 VerdictEnum.code（ACCEPTED / WRONG_ANSWER / ...）。
// 之前存在「展示文案（Accepted / Wrong Answer）」与「落库 code」两套并行的枚举，
// 现已收敛为单枚举：JudgeInfo.message / 逐用例 status / submission.verdict 列三者同码。
// 故这里以 code 为键，text 为中文展示文案，color 为标签语义色。
export const JUDGE_RESULT: Record<string, { text: string; color: string }> = {
  ACCEPTED: { text: '通过', color: 'green' },
  WRONG_ANSWER: { text: '答案错误', color: 'red' },
  COMPILE_ERROR: { text: '编译错误', color: 'purple' },
  RUNTIME_ERROR: { text: '运行时错误', color: 'red' },
  TIME_LIMIT_EXCEEDED: { text: '运行超时', color: 'orange' },
  MEMORY_LIMIT_EXCEEDED: { text: '内存超限', color: 'orange' },
  PRESENTATION_ERROR: { text: '格式错误', color: 'orange' },
  DANGEROUS_OPERATION: { text: '危险操作', color: 'red' },
  SYSTEM_ERROR: { text: '系统错误', color: 'gray' }
}

export const formatStatus = (status: number) => {
  const entry = Object.values(JUDGE_STATUS).find((item) => item.value === status)
  return entry || { text: '未知状态', color: 'gray' }
}

export const NEUTRAL_RESULT = { text: '已执行', color: 'blue' } as const

/**
 * 把后端 verdict code（JudgeInfo.message / 逐用例 status / submission.verdict 列）映射到展示条目。
 * 三者同用 VerdictEnum.getCode()（ACCEPTED / WRONG_ANSWER / ...）。
 * 空值（试运行中性态）回落「已执行」；无法识别的 code 也回落中性，避免显示空白。
 */
export const judgeResultEntry = (code: string | null | undefined) => {
  if (!code) return NEUTRAL_RESULT
  return JUDGE_RESULT[code] ?? NEUTRAL_RESULT
}

export const formatJudgeInfo = (judgeInfo: any) => {
  if (!judgeInfo) return null
  const { message, time, memory } = judgeInfo
  const resultEntry = judgeResultEntry(message)
  const messageColor = resultEntry?.color || 'blue'

  const parts: string[] = []
  // 展示中文文案（已执行的「已执行」不重复拼进耗时内存行）
  if (resultEntry.text && resultEntry.text !== NEUTRAL_RESULT.text) {
    parts.push(resultEntry.text)
  }
  if (time) parts.push(`${time}ms`)
  if (memory) parts.push(`${memory}KB`)

  return {
    text: parts.length > 0 ? parts.join(' | ') : null,
    color: messageColor
  }
}

// 将 Arco 风格的颜色名映射为 shadcn Badge 的 tailwind 类
export function judgeColorClass(color: string): string {
  switch (color) {
    case 'green':
      return 'bg-status-success-surface text-foreground-strong border border-status-success-mark'
    case 'red':
      return 'bg-status-error-surface text-foreground-strong border border-status-error-mark'
    case 'orange':
      return 'bg-status-warning-surface text-foreground-strong border border-status-warning-mark'
    case 'blue':
    case 'purple':
      return 'bg-surface-highlight text-foreground-strong border border-border-control'
    case 'gray':
    default:
      return 'bg-surface-highlight text-muted-foreground border border-border-control'
  }
}