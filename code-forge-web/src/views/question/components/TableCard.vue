<template>
  <Card class="overflow-hidden">
    <div class="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead
              v-for="col in columns"
              :key="colKey(col)"
              :style="colWidth(col)"
              class="whitespace-nowrap"
            >
              {{ col.title }}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="(row, ri) in tableData" :key="rowKey(row, ri)">
            <TableCell v-for="col in columns" :key="colKey(col)" class="align-top">
              <slot v-if="col.slotName" :name="col.slotName" :record="row" />
              <span v-else :title="col.ellipsis ? textOf(col, row) : undefined">{{ textOf(col, row) }}</span>
            </TableCell>
          </TableRow>
          <TableRow v-if="tableData.length === 0 && loading">
            <TableCell :colspan="columns.length" class="py-12 text-center text-muted-foreground">
              加载中…
            </TableCell>
          </TableRow>
          <TableRow v-else-if="tableData.length === 0">
            <TableCell :colspan="columns.length" class="py-12 text-center text-muted-foreground">
              暂无数据
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <div class="flex items-center justify-between gap-3 border-t px-4 py-3">
      <div class="text-sm text-muted-foreground">共 {{ pagination.total }} 条</div>
      <div class="flex items-center gap-2">
        <Button variant="outline" size="sm" :disabled="pagination.current <= 1" @click="goPrev">
          上一页
        </Button>
        <span class="text-sm">第 {{ pagination.current }} / {{ totalPages }} 页</span>
        <Button
          variant="outline"
          size="sm"
          :disabled="pagination.current >= totalPages"
          @click="goNext"
        >
          下一页
        </Button>
        <select
          :value="pagination.pageSize"
          class="h-8 rounded-md border bg-background px-2 text-sm"
          @change="onPageSizeChange"
        >
          <option :value="10">10 / 页</option>
          <option :value="20">20 / 页</option>
          <option :value="50">50 / 页</option>
        </select>
      </div>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'

interface Column {
  title: string
  dataIndex?: string
  slotName?: string
  width?: number | string
  ellipsis?: boolean
}

interface Pagination {
  current: number
  pageSize: number
  total: number
  showTotal?: boolean
  showPageSize?: boolean
  showJumper?: boolean
}

const props = withDefaults(
  defineProps<{
    columns: Column[]
    tableData?: any[]
    pagination: Pagination
    loading?: boolean
  }>(),
  {
    tableData: () => [],
    loading: false
  }
)

const emit = defineEmits<{
  pageChange: [page: number]
  pageSizeChange: [size: number]
}>()

const totalPages = computed(() =>
  Math.max(1, Math.ceil((props.pagination.total || 0) / (props.pagination.pageSize || 10)))
)

const colKey = (col: Column) => col.slotName || col.dataIndex || col.title
const rowKey = (row: any, i: number) => row?.id ?? i
const colWidth = (col: Column) =>
  col.width ? { width: typeof col.width === 'number' ? `${col.width}px` : col.width } : {}
const textOf = (col: Column, row: any) => {
  if (!col.dataIndex) return ''
  const v = row?.[col.dataIndex]
  return v === undefined || v === null ? '' : String(v)
}

const goPrev = () => {
  if (props.pagination.current > 1) emit('pageChange', props.pagination.current - 1)
}
const goNext = () => {
  if (props.pagination.current < totalPages.value) emit('pageChange', props.pagination.current + 1)
}
const onPageSizeChange = (e: Event) => {
  const size = Number((e.target as HTMLSelectElement).value)
  emit('pageSizeChange', size)
}
</script>
