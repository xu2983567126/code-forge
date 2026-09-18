<template>
  <div class="uc-page-stack">
    <PageHeader title="题库专题" description="浏览与管理题单（专题）">
      <template #actions>
        <Button @click="showCreate = !showCreate">创建题单</Button>
      </template>
    </PageHeader>

    <!-- 创建题单 -->
    <Card v-if="showCreate" class="mb-4">
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

    <BaseSearchBar @search="handleSearch" @reset="handleReset">
      <Input v-model="searchParams.searchText" placeholder="搜索标题 / 描述" class="w-[220px]" />
      <select
        v-model="publicFilter"
        class="h-9 rounded-md border border-input bg-background px-3 text-sm"
        @change="handleSearch"
      >
        <option value="">全部题单</option>
        <option value="1">公开</option>
        <option value="0">私有</option>
      </select>
    </BaseSearchBar>

    <TableCard
      :columns="columns"
      :table-data="tableData"
      :pagination="pagination"
      :loading="loading"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    >
      <template #title="{ record }">
        <button
          class="text-left font-medium text-foreground-strong hover:underline"
          @click="goDetail(record)"
        >
          {{ record.title || '未命名题单' }}
        </button>
      </template>

      <template #description="{ record }">
        <span class="line-clamp-2 text-muted-foreground">{{ record.description || '-' }}</span>
      </template>

      <template #isPublic="{ record }">
        <Badge :variant="record.isPublic === 1 ? 'default' : 'secondary'">
          {{ record.isPublic === 1 ? '公开' : '私有' }}
        </Badge>
      </template>

      <template #count="{ record }">
        <span>{{ record.questionCount || 0 }} 题 / 已解 {{ record.solvedCount || 0 }}</span>
      </template>

      <template #action="{ record }">
        <div class="flex gap-2">
          <Button size="sm" variant="outline" @click="goDetail(record)">查看</Button>
          <Button size="sm" variant="outline" @click="handleFork(record)">Fork</Button>
        </div>
      </template>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  listQuestionBankVoByPage,
  addQuestionBank,
  forkQuestionBank,
} from '@generated'
import type { QuestionBankVo } from '@generated'
import TableCard from './components/TableCard.vue'
import BaseSearchBar from './components/BaseSearchBar.vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/PageHeader.vue'
import FormField from '@/components/ui-custom/FormField.vue'
import { usePaginationList } from './composables/usePaginationList'
import { toast } from 'vue-sonner'

const router = useRouter()

const publicFilter = ref('')

const {
  loading,
  tableData,
  pagination,
  searchParams,
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange
} = usePaginationList<QuestionBankVo>({
  api: listQuestionBankVoByPage,
  buildParams: (p, s) => ({
    current: p.current,
    pageSize: p.pageSize,
    searchText: s.searchText || undefined,
    isPublic: publicFilter.value === '' ? undefined : Number(publicFilter.value)
  }),
  initSearchParams: { searchText: '' }
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '标题', slotName: 'title', ellipsis: true, width: 160 },
  { title: '描述', slotName: 'description', ellipsis: true, width: 240 },
  { title: '访问', slotName: 'isPublic', width: 90 },
  { title: '题量', slotName: 'count', width: 120 },
  { title: 'Fork数', dataIndex: 'forkNum', width: 80 },
  { title: '操作', slotName: 'action', width: 160 }
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
      handleSearch()
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
      handleSearch()
    } else {
      toast.error(res.data?.message || '创建失败')
    }
  } catch {
    toast.error('创建失败')
  } finally {
    creating.value = false
  }
}
</script>
