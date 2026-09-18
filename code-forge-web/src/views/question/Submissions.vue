<template>
  <div class="uc-page-stack uc-list-page">
    <PageHeader class="shrink-0" title="提交记录" description="浏览所有提交记录" />

    <!-- 筛选面板 -->
    <Card class="shrink-0 py-0">
      <CardContent class="flex flex-wrap items-end gap-3 p-4">
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">题号</label>
          <Input v-model="filters.questionId" placeholder="题号" class="w-[140px]" />
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">用户 ID</label>
          <Input v-model="filters.userId" placeholder="用户 ID" class="w-[140px]" />
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">语言</label>
          <select v-model="filters.language" class="uc-filter-select">
            <option v-for="opt in languageOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">判题状态</label>
          <select v-model="filters.status" class="uc-filter-select">
            <option value="">全部</option>
            <option v-for="s in statusOptions" :key="s.value" :value="String(s.value)">
              {{ s.text }}
            </option>
          </select>
        </div>

        <Button variant="outline" @click="resetFilters">重置</Button>

        <span class="ml-auto flex items-center gap-2 text-sm text-muted-foreground">
          <span v-if="hasPendingJudge" class="text-xs">有判题中的提交，每 10 秒自动刷新</span>
          {{ loading ? '加载中…' : `共 ${total} 条` }}
        </span>
      </CardContent>
    </Card>

    <!-- 列表 -->
    <Card class="flex min-h-0 flex-1 flex-col overflow-hidden py-0">
      <DataTable
        :data="items"
        :columns="columns"
        :virtualized="true"
        virtualized-height="100%"
        :row-height="48"
        empty-label="暂无提交记录"
        empty-description="调整筛选条件，或先去提交一次代码"
      >
        <template #cell-questionId="{ item }">
          <Button
            variant="link"
            size="sm"
            class="h-auto p-0"
            @click.stop="handleQuestionClick(item.questionId)"
          >
            {{ item.questionId }}
          </Button>
        </template>

        <template #cell-judgeInfo="{ item }">
          <span
            v-if="formatJudgeInfo(item.judgeInfo)?.text"
            class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
            :class="judgeColorClass(formatJudgeInfo(item.judgeInfo)!.color)"
          >
            {{ formatJudgeInfo(item.judgeInfo)!.text }}
          </span>
          <span v-else class="italic text-muted-foreground">无判题信息</span>
        </template>

        <template #cell-status="{ item }">
          <span
            class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
            :class="judgeColorClass(formatStatus(item.status ?? -1).color)"
          >
            {{ formatStatus(item.status ?? -1).text }}
          </span>
        </template>

        <template #cell-createTime="{ item }">
          <span v-if="item.createTime">{{ formatTime(item.createTime) }}</span>
          <span v-else class="italic text-muted-foreground">-</span>
        </template>

        <!-- 滚动容器内的「加载更多」哨兵 -->
        <template #footer>
          <div ref="sentinel" class="h-px w-full" />
          <div v-if="loadingMore" class="py-4 text-center text-sm text-muted-foreground">
            加载中…
          </div>
          <div
            v-else-if="items.length && !hasMore"
            class="py-4 text-center text-sm text-muted-foreground"
          >
            已加载全部 {{ total }} 条
          </div>
        </template>
      </DataTable>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { listSubmissionByPage } from '@generated'
import type { SubmissionVo } from '@generated'
import DataTable from '@/components/common/data-table/DataTable.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import PageHeader from '@/components/PageHeader.vue'
import { useInfiniteList } from '@/composables/useInfiniteList'
import { formatTime } from '@/utils/format'
import { JUDGE_STATUS, formatStatus, formatJudgeInfo, judgeColorClass } from '@/constants/judge'

const languageOptions = [
  { label: '所有语言', value: '' },
  { label: 'Java', value: 'java' },
  { label: 'C++', value: 'cpp' },
  { label: 'Go', value: 'go' },
  { label: 'Python', value: 'python' },
  { label: 'C', value: 'c' }
]

const statusOptions = Object.values(JUDGE_STATUS)

const { items, filters, loading, loadingMore, total, hasMore, reload, loadMore, applyFilters, resetFilters } =
  useInfiniteList<SubmissionVo>({
    api: listSubmissionByPage,
    buildParams: (page, f) => ({
      current: page.current,
      pageSize: page.pageSize,
      questionId: f.questionId || undefined,
      userId: f.userId || undefined,
      language: f.language || undefined,
      status: f.status === '' ? undefined : Number(f.status)
    }),
    defaultFilters: { questionId: '', userId: '', language: '', status: '' }
  })

watch(
  [() => filters.questionId, () => filters.userId, () => filters.language, () => filters.status],
  () => applyFilters()
)

const columns = [
  { key: 'id', header: '提交号', class: 'w-[210px]' },
  { key: 'language', header: '编程语言', class: 'w-[90px]' },
  { key: 'judgeInfo', header: '判题信息', class: 'w-[220px]' },
  { key: 'status', header: '判题状态', class: 'w-[110px]' },
  { key: 'questionId', header: '题号', class: 'w-[110px]' },
  { key: 'userId', header: '用户 ID', class: 'w-[190px]' },
  { key: 'createTime', header: '创建时间', class: 'w-[180px]' }
]

const handleQuestionClick = (questionId?: number) => {
  if (questionId) {
    window.open(`/questions/${questionId}/view`)
  }
}

/**
 * 自动刷新只在**确实有判题中的提交**时进行。
 * 无脑每 10 秒 reload 会在用户翻看历史时把列表强行拉回第一页 —— 而判题中的记录
 * 才是唯一"等着看结果"的场景。
 */
const hasPendingJudge = computed(() =>
  items.value.some((s) => s.status === JUDGE_STATUS.PENDING.value || s.status === JUDGE_STATUS.JUDGING.value)
)

const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | undefined
let autoRefreshInterval: number | null = null

onMounted(() => {
  reload()
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) loadMore()
    },
    { rootMargin: '300px' }
  )
  watch(
    sentinel,
    (el) => {
      if (el) observer?.observe(el)
    },
    { immediate: true }
  )

  autoRefreshInterval = window.setInterval(() => {
    if (hasPendingJudge.value) reload()
  }, 10000)
})

onUnmounted(() => {
  observer?.disconnect()
  if (autoRefreshInterval) {
    clearInterval(autoRefreshInterval)
    autoRefreshInterval = null
  }
})
</script>

<style scoped>
.uc-list-page {
  height: calc(100dvh - 3.5rem - var(--uc-layout-page-gutter) * 2 - 2px);
  min-height: 30rem;
}

.uc-filter-select {
  height: 2.25rem;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--background);
  padding-inline: 0.75rem;
  font-size: 0.875rem;
}
</style>
