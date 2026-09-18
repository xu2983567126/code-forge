export const JUDGE_STATUS = {
  PENDING: { value: 0, text: '等待中', color: 'orange' },
  JUDGING: { value: 1, text: '判题中', color: 'blue' },
  SUCCESS: { value: 2, text: '成功', color: 'green' },
  FAILED: { value: 3, text: '失败', color: 'red' }
} as const

export const JUDGE_RESULT = {
  ACCEPTED: { text: 'Accepted', color: 'green' },
  WRONG_ANSWER: { text: 'Wrong Answer', color: 'red' },
  TIME_LIMIT_EXCEEDED: { text: 'Time Limit Exceeded', color: 'orange' },
  MEMORY_LIMIT_EXCEEDED: { text: 'Memory Limit Exceeded', color: 'orange' },
  RUNTIME_ERROR: { text: 'Runtime Error', color: 'red' },
  COMPILE_ERROR: { text: 'Compile Error', color: 'purple' },
  SYSTEM_ERROR: { text: 'System Error', color: 'gray' },
  PENDING: { text: 'Pending', color: 'blue' },
  RUNNING: { text: 'Running', color: 'blue' }
} as const

export const formatStatus = (status: number) => {
  const entry = Object.values(JUDGE_STATUS).find((item) => item.value === status)
  return entry || { text: '未知状态', color: 'gray' }
}

export const formatJudgeInfo = (judgeInfo: any) => {
  if (!judgeInfo) return null
  const { message, time, memory } = judgeInfo
  const resultEntry = Object.values(JUDGE_RESULT).find((item) => item.text === message)
  const messageColor = resultEntry?.color || 'blue'

  const parts: string[] = []
  if (message) parts.push(message)
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