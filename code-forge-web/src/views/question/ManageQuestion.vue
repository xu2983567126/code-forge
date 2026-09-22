<template>
  <div class="uc-page-stack uc-list-page">
    <PageHeader
      class="shrink-0"
      title="题目管理"
      description="管理所有题目信息"
    >
      <template #actions>
        <Button @click="handleCreateQuestion">创建题目</Button>
      </template>
    </PageHeader>

    <!-- 筛选面板 -->
    <Card class="shrink-0 py-0">
      <CardContent class="flex flex-wrap items-end gap-3 p-4">
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">题号</label>
          <Input v-model="filters.id" placeholder="请输入题号" class="w-[150px]" />
        </div>
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">标题</label>
          <Input v-model="filters.title" placeholder="标题关键词" class="w-[180px]" />
        </div>
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">标签</label>
          <TagInput v-model="filters.tags" class="w-[210px]" />
        </div>

        <Button variant="outline" @click="resetFilters">重置</Button>

        <span class="ml-auto text-sm text-muted-foreground">
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
        :row-height="52"
        empty-label="暂无题目"
        empty-description="调整筛选条件，或先创建一道题目"
      >
        <template #cell-id="{ item }">
          <Button
            variant="link"
            size="sm"
            class="h-auto p-0"
            @click.stop="handleQuestionClick(item.id)"
          >
            {{ item.id }}
          </Button>
        </template>

        <template #cell-tags="{ item }">
          <div v-if="item.tags && item.tags.length" class="flex gap-1">
            <Badge v-for="tag in item.tags.slice(0, 2)" :key="tag" variant="secondary">
              {{ tag }}
            </Badge>
            <span v-if="item.tags.length > 2" class="text-xs text-muted-foreground">
              +{{ item.tags.length - 2 }}
            </span>
          </div>
          <span v-else class="italic text-muted-foreground">无标签</span>
        </template>

        <template #cell-submit="{ item }">
          <span>{{ item.submitNum ?? 0 }} / {{ item.acceptedNum ?? 0 }}</span>
        </template>

        <template #cell-acceptRate="{ item }">
          <span v-if="item.submitNum && item.submitNum > 0">
            {{ (((item.acceptedNum ?? 0) / item.submitNum) * 100).toFixed(1) }}%
          </span>
          <span v-else class="italic text-muted-foreground">0%</span>
        </template>

        <template #cell-createTime="{ item }">
          <span v-if="item.createTime">{{ formatTime(item.createTime) }}</span>
          <span v-else class="italic text-muted-foreground">-</span>
        </template>

        <template #cell-action="{ item }">
          <div class="flex gap-1">
            <Button size="sm" variant="ghost" @click.stop="handleEdit(item)">编辑</Button>
            <Button size="sm" variant="ghost" @click.stop="handleDetail(item)">详情</Button>
            <Button
              size="sm"
              variant="ghost"
              class="text-destructive"
              @click.stop="handleDelete(item)"
            >
              删除
            </Button>
          </div>
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

    <!-- 题目详情弹窗：用例/配置原为 <details> 行内展开（行高可变，与虚拟滚动互斥），移到弹窗 -->
    <Dialog v-model:open="detailOpen">
      <DialogContent class="max-w-2xl">
        <DialogHeader>
          <DialogTitle>题目详情 — {{ detailRow?.title || detailRow?.id }}</DialogTitle>
        </DialogHeader>
        <div class="max-h-[60vh] space-y-4 overflow-y-auto text-sm">
          <div>
            <p class="mb-1 font-medium text-muted-foreground">判题配置</p>
            <pre class="rounded-md border border-border bg-muted/40 p-3 font-mono text-xs whitespace-pre-wrap">{{ detailJudgeConfig }}</pre>
          </div>
          <div>
            <p class="mb-1 font-medium text-muted-foreground">测试用例</p>
            <pre class="rounded-md border border-border bg-muted/40 p-3 font-mono text-xs whitespace-pre-wrap">{{ detailJudgeCase }}</pre>
          </div>
          <div>
            <p class="mb-1 font-medium text-muted-foreground">内容</p>
            <p class="whitespace-pre-wrap">{{ detailRow?.content || '（无）' }}</p>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { deleteQuestion, listQuestionVoByPage } from '@generated'
import type { JudgeCase, QuestionVo } from '@generated'
import DataTable from '@/components/common/data-table/DataTable.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import PageHeader from '@/components/PageHeader.vue'
import TagInput from '@/components/ui-custom/TagInput.vue'
import { useInfiniteList } from '@/composables/useInfiniteList'
import { formatTime } from '@/utils/format'
import { toast } from 'vue-sonner'

const router = useRouter()

const { items, filters, loading, loadingMore, total, hasMore, reload, loadMore, applyFilters, resetFilters } =
  useInfiniteList<QuestionVo>({
    api: listQuestionVoByPage,
    buildParams: (page, f) => ({
      current: page.current,
      pageSize: page.pageSize,
      id: f.id || undefined,
      title: f.title || undefined,
      tags: f.tags?.length ? f.tags.join(',') : undefined
    }),
    defaultFilters: { id: '', title: '', tags: [] }
  })

watch(
  [() => filters.id, () => filters.title, () => filters.tags],
  () => applyFilters(),
  { deep: true }
)

// 列宽约束见 Questions.vue 的 columns 注释：每列显式 px、th/td 共用。
// 原则：总和尽量贴近容器宽（均摊差 = (容器-总和)/列数，列宽总和越小每列系统差越大）。
// 原 13 列裁到 7 列：内容/点赞/收藏信息量低，用例与配置移入「详情」弹窗。
const columns = [
  { key: 'id', header: 'ID', class: 'w-[210px]' },
  { key: 'title', header: '标题', class: 'w-[180px]' },
  { key: 'tags', header: '标签', class: 'w-[170px]' },
  { key: 'submit', header: '提交/通过', class: 'w-[130px]' },
  { key: 'acceptRate', header: '通过率', class: 'w-[110px]' },
  { key: 'createTime', header: '创建时间', class: 'w-[170px]' },
  { key: 'action', header: '操作', class: 'w-[185px]' }
]

/**
 * 详情弹窗的行数据：管理员/作者视角的响应实际是 QuestionAdminVO（运行时多态，
 * OpenAPI 文档未收录 answer/judgeCase），故在类型上显式补 judgeCase。
 */
type ManageRow = QuestionVo & { judgeCase?: Array<JudgeCase> }

const detailOpen = ref(false)
const detailRow = ref<ManageRow | null>(null)
const detailJudgeConfig = computed(() =>
  JSON.stringify(detailRow.value?.judgeConfig ?? null, null, 2)
)
/** 管理员/作者视角的详情响应才带 judgeCase（QuestionAdminVO），其他角色为空。 */
const detailJudgeCase = computed(() =>
  detailRow.value?.judgeCase
    ? JSON.stringify(detailRow.value.judgeCase, null, 2)
    : '（当前身份不可见或未配置）'
)

const handleDetail = (record: ManageRow) => {
  detailRow.value = record
  detailOpen.value = true
}

const handleQuestionClick = (questionId?: string) => {
  if (questionId) {
    window.open(`/questions/${questionId}/view`, '_blank')
  }
}

const handleCreateQuestion = () => {
  router.push('/questions/create')
}

const handleEdit = (record: QuestionVo) => {
  if (!record.id) {
    toast.error('题目ID不存在，无法编辑')
    return
  }
  router.push(`/questions/${record.id}/edit`)
}

const handleDelete = async (record: QuestionVo) => {
  if (!record.id) {
    toast.error('题目ID不存在，无法删除')
    return
  }
  try {
    const confirm = await new Promise<boolean>((resolve) => {
      const result = window.confirm(`确定要删除题目"${record.title}"吗？`)
      resolve(result)
    })
    if (confirm) {
      await deleteQuestion({ path: { id: record.id } })
      toast.success('删除成功')
      reload()
    }
  } catch {
    toast.error('删除题目失败')
  }
}

// 哨兵进入视口即追加下一页（列表在 DataTable 自己的滚动容器里，window 上没有滚动事件）
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | undefined

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
})

onUnmounted(() => {
  observer?.disconnect()
})
</script>

<style scoped>
.uc-list-page {
  height: calc(100dvh - 3.5rem - var(--uc-layout-page-gutter) * 2 - 2px);
  min-height: 30rem;
}
</style>
