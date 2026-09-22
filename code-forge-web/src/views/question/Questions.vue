<template>
  <div class="uc-page-stack uc-list-page">
    <PageHeader
      class="shrink-0"
      title="题目列表"
      description="浏览所有题目信息"
    >
      <template #actions>
        <Button v-if="hasCreatePermission" @click="handleCreateQuestion">创建题目</Button>
      </template>
    </PageHeader>

    <!-- 筛选面板 -->
    <Card class="shrink-0 py-0">
      <CardContent class="flex flex-wrap items-end gap-3 p-4">
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">标题</label>
          <Input
            v-model="filters.title"
            placeholder="标题关键词"
            class="w-[200px]"
          />
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">难度</label>
          <select v-model="filters.difficulty" class="uc-filter-select">
            <option value="">不限</option>
            <option v-for="d in DIFFICULTIES" :key="d" :value="d">{{ d }}</option>
          </select>
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">标签</label>
          <TagInput v-model="filters.tags" class="w-[210px]" />
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">排序</label>
          <select v-model="sortKey" class="uc-filter-select">
            <option value="default">题号升序</option>
            <option value="newest">最新发布</option>
          </select>
        </div>

        <Button variant="outline" @click="resetFilters">重置</Button>

        <!-- 视图切换：表格适合筛选比对，卡片适合刷题浏览；偏好记在本地 -->
        <div class="ml-auto flex items-center gap-2">
          <span class="text-sm text-muted-foreground">
            {{ loading ? '加载中…' : `共 ${total} 条` }}
          </span>
          <template v-if="selectedIds.length">
            <Button size="sm" @click="pickerOpen = true">
              加入题单（{{ selectedIds.length }}）
            </Button>
            <Button variant="ghost" size="sm" @click="selectedIds = []">清空</Button>
          </template>
          <div class="flex overflow-hidden rounded-md border border-border">
            <button
              type="button"
              title="表格视图"
              :class="viewToggleClass(viewMode === 'table')"
              @click="setViewMode('table')"
            >
              <List class="size-4" />
            </button>
            <button
              type="button"
              title="卡片视图"
              :class="viewToggleClass(viewMode === 'card')"
              @click="setViewMode('card')"
            >
              <LayoutGrid class="size-4" />
            </button>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- 列表：表格视图 -->
    <Card v-if="viewMode === 'table'" class="flex min-h-0 flex-1 flex-col overflow-hidden py-0">
      <DataTable
        :data="items"
        :columns="columns"
        :virtualized="true"
        virtualized-height="100%"
        :row-height="48"
        empty-label="暂无题目"
        empty-description="调整筛选条件，或先创建一道题目"
      >
        <template #cell-difficulty="{ item }">
          <span
            v-if="item.difficulty"
            class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
            :class="difficultyClass(item.difficulty)"
          >
            {{ item.difficulty }}
          </span>
          <span v-else class="italic text-muted-foreground">-</span>
        </template>

        <template #cell-tags="{ item }">
          <div v-if="item.tags && item.tags.length" class="flex gap-1 overflow-hidden">
            <Badge v-for="tag in item.tags.slice(0, 2)" :key="tag" variant="secondary">
              {{ tag }}
            </Badge>
            <span v-if="item.tags.length > 2" class="text-xs text-muted-foreground">
              +{{ item.tags.length - 2 }}
            </span>
          </div>
          <span v-else class="italic text-muted-foreground">无标签</span>
        </template>

        <template #cell-acceptRate="{ item }">
          <span v-if="item.submitNum && item.submitNum > 0">
            {{ (((item.acceptedNum ?? 0) / item.submitNum) * 100).toFixed(1) }}%（{{
              item.acceptedNum || 0
            }}/{{ item.submitNum }}）
          </span>
          <span v-else class="italic text-muted-foreground">
            0%（{{ item.acceptedNum || 0 }}/{{ item.submitNum || 0 }}）
          </span>
        </template>

        <template #cell-createTime="{ item }">
          <span v-if="item.createTime">{{ formatTime(item.createTime) }}</span>
          <span v-else class="italic text-muted-foreground">-</span>
        </template>

        <template #cell-select="{ item }">
          <!-- click.stop：行点击是「进详情」，勾选不能顺带跳转 -->
          <button
            type="button"
            class="flex h-4 w-4 items-center justify-center rounded border border-border"
            :class="item.id && selectedIds.includes(item.id) ? 'bg-primary' : ''"
            @click.stop="toggleSelect(item.id)"
          >
            <span
              v-if="item.id && selectedIds.includes(item.id)"
              class="h-2 w-2 rounded-sm bg-primary-foreground"
            />
          </button>
        </template>

        <template #cell-action="{ item }">
          <Button size="sm" @click.stop="handleDoQuestion(item)">做题</Button>
        </template>

        <!-- 滚动容器内的「加载更多」哨兵：进入视口即追加下一页 -->
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

    <!-- 列表：卡片视图（刷题浏览形态，参考 UltiCode 的题目卡） -->
    <Card v-else class="flex min-h-0 flex-1 flex-col overflow-hidden py-0">
      <div class="min-h-0 flex-1 overflow-y-auto p-4">
        <div v-if="!items.length && !loading" class="py-16 text-center">
          <p class="font-medium">暂无题目</p>
          <p class="mt-1 text-sm text-muted-foreground">
            调整筛选条件，或先创建一道题目
          </p>
        </div>

        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <Card
            v-for="q in items"
            :key="q.id"
            class="cursor-pointer py-0 transition-colors hover:bg-muted/40"
            @click="handleDoQuestion(q)"
          >
            <CardContent class="space-y-3 p-4">
              <div class="flex items-start justify-between gap-2">
                <div class="min-w-0">
                  <p class="text-xs text-muted-foreground">#{{ q.id }}</p>
                  <p class="truncate font-medium">{{ q.title || '未命名题目' }}</p>
                </div>
                <span
                  v-if="q.difficulty"
                  class="inline-flex shrink-0 items-center rounded border px-2 py-0.5 text-xs"
                  :class="difficultyClass(q.difficulty)"
                >
                  {{ q.difficulty }}
                </span>
              </div>

              <div v-if="q.tags?.length" class="flex flex-wrap gap-1">
                <Badge v-for="t in q.tags.slice(0, 3)" :key="t" variant="secondary">
                  {{ t }}
                </Badge>
                <span v-if="q.tags.length > 3" class="text-xs text-muted-foreground">
                  +{{ q.tags.length - 3 }}
                </span>
              </div>

              <div>
                <div class="mb-1 flex items-center justify-between text-xs text-muted-foreground">
                  <span>通过率 {{ acceptRatePct(q) }}%</span>
                  <span>{{ q.acceptedNum || 0 }}/{{ q.submitNum || 0 }}</span>
                </div>
                <div class="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    class="h-full rounded-full bg-[var(--chart-accent-primary)]"
                    :style="{ width: acceptRatePct(q) + '%' }"
                  />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <!-- 卡片模式的哨兵（表格模式挂在 DataTable 的 footer 插槽里，两边同名 ref 不会同时挂载） -->
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
      </div>
    </Card>

    <!-- 批量加入题单：题目侧勾选 → 选题单。加入成功后清空勾选，避免用户重复提交同一批 -->
    <BankPickerDialog
      v-model:open="pickerOpen"
      :question-ids="selectedIds"
      @done="selectedIds = []"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { listQuestionVoByPage } from '@generated'
import type { QuestionVo } from '@generated'
import ACCESS_ENUM from '@/access/accessEnum'
import DataTable from '@/components/common/data-table/DataTable.vue'
import TagInput from '@/components/ui-custom/TagInput.vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { List, LayoutGrid } from 'lucide-vue-next'
import PageHeader from '@/components/PageHeader.vue'
import { useInfiniteList } from '@/composables/useInfiniteList'
import { formatTime } from '@/utils/format'
import { toast } from 'vue-sonner'
import BankPickerDialog from './components/BankPickerDialog.vue'

const router = useRouter()
const userStore = useUserStore()

/** 后端 `question.difficulty` 白名单取值（Service 侧同样有校验）。 */
const DIFFICULTIES = ['简单', '中等', '困难']

/** 排序键 → 后端 sortField / sortOrder。`default` 不传，用后端默认顺序。 */
const SORT_MAP: Record<string, { sortField?: string; sortOrder?: string }> = {
  default: {},
  newest: { sortField: 'id', sortOrder: 'desc' }
}

/** 视图模式持久化：表格适合筛选比对，卡片适合刷题浏览。 */
const VIEW_KEY = 'code-forge-question-view'
const viewMode = ref<'table' | 'card'>(
  (localStorage.getItem(VIEW_KEY) as 'table' | 'card') || 'table'
)
const setViewMode = (mode: 'table' | 'card') => {
  viewMode.value = mode
  localStorage.setItem(VIEW_KEY, mode)
}
const viewToggleClass = (active: boolean) =>
  active
    ? 'flex items-center justify-center px-2 py-1.5 bg-muted text-foreground'
    : 'flex items-center justify-center px-2 py-1.5 text-muted-foreground hover:bg-muted/60'

const acceptRatePct = (q: QuestionVo) =>
  q.submitNum && q.submitNum > 0
    ? (((q.acceptedNum ?? 0) / q.submitNum) * 100).toFixed(1)
    : '0.0'

/**
 * 已勾选的题目 id（批量加入题单用）。
 *
 * ⚠️ 全程字符串：雪花 id 19 位，转 number 会丢末位，后端按被舍入的 id 查不到题目。
 * 列表是无限滚动的，勾选跨页累计 —— 翻页不清空，否则用户翻一页就丢一批选择。
 */
const selectedIds = ref<string[]>([])
const pickerOpen = ref(false)

const toggleSelect = (id?: string) => {
  if (!id) return
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((x) => x !== id)
    : [...selectedIds.value, id]
}

const sortKey = ref('default')

const { items, filters, loading, loadingMore, total, hasMore, reload, loadMore, applyFilters, resetFilters } =
  useInfiniteList<QuestionVo>({
    api: listQuestionVoByPage,
    buildParams: (page, f) => ({
      current: page.current,
      pageSize: page.pageSize,
      title: f.title || undefined,
      difficulty: f.difficulty || undefined,
      tags: f.tags?.length ? f.tags.join(',') : undefined,
      ...SORT_MAP[sortKey.value]
    }),
    defaultFilters: { title: '', difficulty: '', tags: [] }
  })

/**
 * 筛选条件变化 → 防抖重载。
 * 统一用 watch 收口，避免每个控件各接一个 `@input`，漏接一个就变成"点了没反应"。
 */
watch(
  [() => filters.title, () => filters.difficulty, () => filters.tags, sortKey],
  () => applyFilters(),
  { deep: true }
)

/**
 * 虚拟行的列宽约束：每一列都必须是**显式 px 宽度**且 th/td 共用（DataTable 会把
 * col.class 同时绑到表头和数据单元格）。绝对定位的行脱离表格布局，只要有一列
 * 宽度缺省或用百分比（基准与表头不一致），整行就会与表头错位。总和略小于容器，
 * 行尾留白即可，不要溢出。
 */
const columns = [
  // 勾选列：批量加入题单。宽度从「题号」列匀出来，保证列宽总和与加列前一致，
  // 否则虚拟行的 fixed 布局会把整行挤出容器。
  { key: 'select', header: '', class: 'w-[44px]' },
  { key: 'id', header: '题号', class: 'w-[166px]' },
  { key: 'title', header: '标题', class: 'w-[230px]' },
  { key: 'difficulty', header: '难度', class: 'w-[90px]' },
  { key: 'tags', header: '标签', class: 'w-[190px]' },
  { key: 'acceptRate', header: '通过率', class: 'w-[150px]' },
  { key: 'createTime', header: '创建时间', class: 'w-[180px]' },
  { key: 'action', header: '操作', class: 'w-[100px]' }
]

const hasCreatePermission = computed(
  () =>
    userStore.loginUser?.role === ACCESS_ENUM.ADMIN ||
    userStore.loginUser?.role === ACCESS_ENUM.USER
)

const difficultyClass = (difficulty: string) => {
  switch (difficulty) {
    case '简单':
      return 'bg-status-success-surface text-foreground-strong border-status-success-mark'
    case '困难':
      return 'bg-status-error-surface text-foreground-strong border-status-error-mark'
    case '中等':
    default:
      return 'bg-status-warning-surface text-foreground-strong border-status-warning-mark'
  }
}

const handleDoQuestion = (record: QuestionVo) => {
  if (!record.id) {
    toast.error('题目ID不存在，无法做题')
    return
  }
  router.push(`/questions/${record.id}/view`)
}

const handleCreateQuestion = () => {
  router.push('/questions/create')
}

// 哨兵进入视口即追加下一页。用 IntersectionObserver 而不是监听 scroll：
// 列表在 DataTable 自己的滚动容器里，window 上没有对应滚动事件可听。
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
  watch(sentinel, (el) => {
    if (el) observer?.observe(el)
  }, { immediate: true })
})

onUnmounted(() => {
  observer?.disconnect()
})
</script>

<style scoped>
/**
 * 列表页定高（L4）。
 *
 * 「虚拟滚动 + 无限加载」要求滚动容器有确定高度：DataTable 的 `virtualized-height="100%"`
 * 需要父链上有确切的 px 高度，否则 `100%` 解析为 `auto`，虚拟滚动失效。
 * 高度按视口反推（顶栏 + 页面 gutter），与做题页分栏同一套算法。
 */
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
