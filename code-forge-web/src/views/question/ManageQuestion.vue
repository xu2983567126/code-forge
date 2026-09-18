<template>
  <div class="uc-page-stack">
    <PageHeader title="题目管理" description="管理所有题目信息">
      <template #actions>
        <Button @click="handleCreateQuestion">创建题目</Button>
      </template>
    </PageHeader>

    <BaseSearchBar @search="handleSearch" @reset="handleReset">
      <Input v-model="searchParams.id" placeholder="请输入题号" class="w-[150px]" />
      <Input v-model="searchParams.title" placeholder="请输入标题关键词" class="w-[200px]" />
      <TagInput v-model="searchParams.tags" class="w-[210px]" />
    </BaseSearchBar>

    <TableCard
      :columns="columns"
      :table-data="tableData"
      :pagination="pagination"
      :loading="loading"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    >
      <template #tags="{ record }">
        <div v-if="record.tags && record.tags.length" class="flex flex-wrap gap-1">
          <Badge v-for="tag in record.tags" :key="tag" variant="secondary">{{ tag }}</Badge>
        </div>
        <span v-else class="italic text-muted-foreground">无标签</span>
      </template>

      <template #judgeCase="{ record }">
        <details v-if="record.judgeCase">
          <summary class="cursor-pointer text-xs text-link-foreground underline">
            查看用例
          </summary>
          <pre class="mt-1 max-w-[320px] rounded bg-surface-highlight p-2 text-xs">{{ JSON.stringify(record.judgeCase, null, 2) }}</pre>
        </details>
        <span v-else class="italic text-muted-foreground">无用例</span>
      </template>

      <template #judgeConfig="{ record }">
        <details v-if="record.judgeConfig">
          <summary class="cursor-pointer text-xs text-link-foreground underline">
            查看配置
          </summary>
          <pre class="mt-1 max-w-[320px] rounded bg-surface-highlight p-2 text-xs">{{ JSON.stringify(record.judgeConfig, null, 2) }}</pre>
        </details>
        <span v-else class="italic text-muted-foreground">无配置</span>
      </template>

      <template #acceptRate="{ record }">
        <span v-if="record.submitNum && record.submitNum > 0">
          {{ ((record.acceptedNum / record.submitNum) * 100).toFixed(1) }}%
        </span>
        <span v-else class="italic text-muted-foreground">0%</span>
      </template>

      <template #id="{ record }">
        <Button
          variant="link"
          size="sm"
          class="h-auto p-0"
          @click="handleQuestionClick(record.id)"
        >
          {{ record.id }}
        </Button>
      </template>

      <template #createTime="{ record }">
        <span v-if="record.createTime">{{ formatTime(record.createTime) }}</span>
        <span v-else class="italic text-muted-foreground">-</span>
      </template>

      <template #action="{ record }">
        <div class="flex gap-2">
          <Button variant="ghost" size="sm" @click="handleEdit(record)">修改</Button>
          <Button variant="destructive" size="sm" @click="handleDelete(record)">删除</Button>
        </div>
      </template>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { deleteQuestion, listQuestionVoByPage } from '@generated'
import type { QuestionVo } from '@generated'
import TableCard from './components/TableCard.vue'
import BaseSearchBar from './components/BaseSearchBar.vue'
import TagInput from '@/components/ui-custom/TagInput.vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import PageHeader from '@/components/PageHeader.vue'
import { Input } from '@/components/ui/input'
import { usePaginationList } from './composables/usePaginationList'
import { formatTime } from '@/utils/format'
import { toast } from 'vue-sonner'

const router = useRouter()

const {
  loading,
  tableData,
  pagination,
  searchParams,
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange,
  fetchData
} = usePaginationList<QuestionVo>({
  api: listQuestionVoByPage,
  buildParams: (p, s) => ({
    current: p.current,
    pageSize: p.pageSize,
    id: s.id || undefined,
    title: s.title || undefined,
    tags: s.tags?.length ? s.tags.join(',') : undefined
  }),
  initSearchParams: { id: '', title: '', tags: [] }
})

const columns = [
  { title: 'ID', slotName: 'id', width: 100 },
  { title: '标题', dataIndex: 'title', ellipsis: true, width: 100 },
  { title: '内容', dataIndex: 'content', ellipsis: true, width: 100 },
  { title: '标签', dataIndex: 'tags', slotName: 'tags', width: 100 },
  { title: '提交数', dataIndex: 'submitNum', width: 100 },
  { title: '通过数', dataIndex: 'acceptedNum', width: 100 },
  { title: '通过率', slotName: 'acceptRate', width: 100 },
  { title: '点赞数', dataIndex: 'thumbNum', width: 100 },
  { title: '收藏数', dataIndex: 'favourNum', width: 100 },
  { title: '测试用例', slotName: 'judgeCase', width: 120 },
  { title: '判题配置', slotName: 'judgeConfig', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', slotName: 'createTime', width: 180 },
  { title: '操作', slotName: 'action', width: 150 }
]

const handleQuestionClick = (questionId: number) => {
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
      fetchData()
    }
  } catch {
    toast.error('删除题目失败')
  }
}
</script>
