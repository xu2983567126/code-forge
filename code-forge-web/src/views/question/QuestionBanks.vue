<template>
  <div class="uc-page-stack uc-list-page">
    <PageHeader
      class="shrink-0"
      title="题库专题"
      description="浏览与管理题单（专题）"
    >
      <template #actions>
        <Button @click="showCreate = !showCreate">创建题单</Button>
      </template>
    </PageHeader>

    <!-- 创建题单 -->
    <Card v-if="showCreate" class="shrink-0 py-0">
      <CardContent class="space-y-4 p-4">
        <FormField label="标题" required :error="createError">
          <Input v-model="createForm.title" placeholder="题单标题" />
        </FormField>
        <FormField label="描述">
          <Input v-model="createForm.description" placeholder="题单描述" />
        </FormField>
        <label class="flex items-center gap-2 text-sm text-muted-foreground">
          <input type="checkbox" v-model="createForm.isPublic" />
          公开题单
        </label>
        <div class="flex gap-2">
          <Button :disabled="creating" @click="handleCreate">提交</Button>
          <Button variant="outline" @click="showCreate = false">取消</Button>
        </div>
      </CardContent>
    </Card>

    <!-- 筛选面板 -->
    <Card class="shrink-0 py-0">
      <CardContent class="flex flex-wrap items-end gap-3 p-4">
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">搜索</label>
          <Input
            v-model="filters.searchText"
            placeholder="搜索标题 / 描述"
            class="w-[220px]"
          />
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">访问</label>
          <select v-model="filters.isPublic" class="uc-filter-select">
            <option value="">全部题单</option>
            <option value="1">公开</option>
            <option value="0">私有</option>
          </select>
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
        empty-label="暂无题单"
        empty-description="调整筛选条件，或先创建一个题单"
      >
        <template #cell-title="{ item }">
          <button
            class="text-left font-medium text-foreground-strong hover:underline"
            @click.stop="goDetail(item)"
          >
            {{ item.title || '未命名题单' }}
          </button>
        </template>

        <template #cell-description="{ item }">
          <span class="text-muted-foreground">{{ item.description || '-' }}</span>
        </template>

        <template #cell-isPublic="{ item }">
          <span
            class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
            :class="
              item.isPublic === 1
                ? 'bg-status-success-surface text-foreground-strong border-status-success-mark'
                : 'bg-surface-highlight text-muted-foreground border-border-control'
            "
          >
            {{ item.isPublic === 1 ? '公开' : '私有' }}
          </span>
        </template>

        <template #cell-count="{ item }">
          <span>{{ item.questionCount || 0 }} 题 / 已解 {{ item.solvedCount || 0 }}</span>
        </template>

        <template #cell-action="{ item }">
          <div class="flex gap-2">
            <Button size="sm" variant="outline" @click.stop="goDetail(item)">
              查看
            </Button>
            <Button size="sm" variant="outline" @click.stop="handleFork(item)">
              Fork
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  listQuestionBankVoByPage,
  addQuestionBank,
  forkQuestionBank,
} from '@generated'
import type { QuestionBankVo } from '@generated'
import DataTable from '@/components/common/data-table/DataTable.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/PageHeader.vue'
import FormField from '@/components/ui-custom/FormField.vue'
import { useInfiniteList } from '@/composables/useInfiniteList'
import { toast } from 'vue-sonner'

const router = useRouter()

const { items, filters, loading, loadingMore, total, hasMore, reload, loadMore, applyFilters, resetFilters } =
  useInfiniteList<QuestionBankVo>({
    api: listQuestionBankVoByPage,
    buildParams: (page, f) => ({
      current: page.current,
      pageSize: page.pageSize,
      searchText: f.searchText || undefined,
      isPublic: f.isPublic === '' ? undefined : Number(f.isPublic)
    }),
    defaultFilters: { searchText: '', isPublic: '' }
  })

watch([() => filters.searchText, () => filters.isPublic], () => applyFilters())

// 列宽约束见 Questions.vue 的 columns 注释：每列显式 px、th/td 共用。
const columns = [
  { key: 'id', header: 'ID', class: 'w-[190px]' },
  { key: 'title', header: '标题', class: 'w-[220px]' },
  { key: 'description', header: '描述', class: 'w-[280px]' },
  { key: 'isPublic', header: '访问', class: 'w-[90px]' },
  { key: 'count', header: '题量', class: 'w-[150px]' },
  { key: 'forkNum', header: 'Fork数', class: 'w-[100px]' },
  { key: 'action', header: '操作', class: 'w-[140px]' }
]

const goDetail = (record: QuestionBankVo) => {
  if (record.id == null) return
  router.push(`/banks/${record.id}`)
}

const handleFork = async (record: QuestionBankVo) => {
  if (record.id == null) return
  try {
    const res = await forkQuestionBank({ path: { id: record.id } })
    if (res.data?.code === 0) {
      toast.success('已 Fork 该题单')
      reload()
    } else {
      toast.error(res.data?.message || 'Fork 失败')
    }
  } catch {
    toast.error('Fork 失败')
  }
}

// 创建题单
const showCreate = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({ title: '', description: '', isPublic: true })

const handleCreate = async () => {
  createError.value = ''
  if (!createForm.title.trim()) {
    createError.value = '标题不能为空'
    return
  }
  creating.value = true
  try {
    const res = await addQuestionBank({
      body: {
        title: createForm.title,
        description: createForm.description,
        isPublic: createForm.isPublic ? 1 : 0
      }
    })
    if (res.data?.code === 0) {
      toast.success('题单创建成功')
      showCreate.value = false
      createForm.title = ''
      createForm.description = ''
      reload()
    } else {
      toast.error(res.data?.message || '创建失败')
    }
  } catch {
    toast.error('创建失败')
  } finally {
    creating.value = false
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

.uc-filter-select {
  height: 2.25rem;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--background);
  padding-inline: 0.75rem;
  font-size: 0.875rem;
}
</style>
