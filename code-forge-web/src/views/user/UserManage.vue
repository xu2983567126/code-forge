<template>
  <div class="uc-page-stack uc-list-page">
    <PageHeader
      class="shrink-0"
      title="用户管理"
      description="封禁 / 解封用户，查看全局用户统计"
    />

    <!-- 统计卡 -->
    <div class="grid shrink-0 grid-cols-2 gap-4 sm:grid-cols-4">
      <Card v-for="s in statCards" :key="s.label" class="py-0">
        <CardContent class="p-4">
          <p class="text-sm text-muted-foreground">{{ s.label }}</p>
          <p class="text-2xl font-semibold">{{ s.value }}</p>
        </CardContent>
      </Card>
    </div>

    <!-- 筛选面板 -->
    <Card class="shrink-0 py-0">
      <CardContent class="flex flex-wrap items-end gap-3 p-4">
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">用户名</label>
          <Input v-model="filters.username" placeholder="搜索用户名" class="w-[200px]" />
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">角色</label>
          <select v-model="filters.role" class="uc-filter-select">
            <option value="">全部</option>
            <option v-for="r in ROLE_OPTIONS" :key="r" :value="r">{{ r }}</option>
          </select>
        </div>

        <Button
          variant="destructive"
          :disabled="selectedIds.length === 0"
          @click="handleBatchDelete"
        >
          批量删除（{{ selectedIds.length }}）
        </Button>
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
        empty-label="暂无用户"
        empty-description="调整筛选条件试试"
      >
        <template #cell-select="{ item }">
          <input
            type="checkbox"
            :checked="isSelected(item.id)"
            @click.stop
            @change="toggleSelect(item.id)"
          />
        </template>

        <template #cell-username="{ item }">
          <span class="font-medium">{{ item.username || '-' }}</span>
        </template>

        <template #cell-role="{ item }">
          <span
            class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
            :class="
              item.role === 'BAN'
                ? 'bg-status-error-surface text-foreground-strong border-status-error-mark'
                : 'bg-surface-highlight text-foreground-strong border-border-control'
            "
          >
            {{ item.role || 'USER' }}
          </span>
        </template>

        <template #cell-createTime="{ item }">
          <span v-if="item.createTime">{{ formatTime(item.createTime) }}</span>
          <span v-else class="italic text-muted-foreground">-</span>
        </template>

        <template #cell-action="{ item }">
          <div class="flex gap-2">
            <Button
              v-if="item.role !== 'BAN'"
              size="sm"
              variant="outline"
              @click.stop="handleBan(item)"
            >
              封禁
            </Button>
            <Button v-else size="sm" variant="outline" @click.stop="handleUnban(item)">
              解封
            </Button>
            <Button size="sm" variant="destructive" @click.stop="handleDelete(item)">
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import {
  listUserByPage,
  banUser,
  unbanUser,
  deleteUser,
  batchDeleteUser,
  getUserStats
} from '@generated'
import type { UserVo } from '@generated'
import DataTable from '@/components/common/data-table/DataTable.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/PageHeader.vue'
import { useInfiniteList } from '@/composables/useInfiniteList'
import { formatTime } from '@/utils/format'
import { toast } from 'vue-sonner'

/** 后端 `user.role` 的取值：USER / ADMIN / BAN（封禁即为该角色）。 */
const ROLE_OPTIONS = ['USER', 'ADMIN', 'BAN']

const selectedIds = ref<number[]>([])
const statCards = ref([
  { label: '总用户', value: 0 },
  { label: '管理员', value: 0 },
  { label: '已封禁', value: 0 },
  { label: '今日新增', value: 0 }
])

const { items, filters, loading, loadingMore, total, hasMore, reload, loadMore, applyFilters, resetFilters } =
  useInfiniteList<UserVo>({
    api: listUserByPage,
    buildParams: (page, f) => ({
      current: page.current,
      pageSize: page.pageSize,
      username: f.username || undefined,
      role: f.role || undefined
    }),
    defaultFilters: { username: '', role: '' }
  })

watch([() => filters.username, () => filters.role], () => applyFilters())

const columns = [
  { key: 'select', header: '', class: 'w-[50px]' },
  { key: 'id', header: 'ID', class: 'w-[200px]' },
  { key: 'username', header: '用户名', class: 'w-[180px]' },
  { key: 'role', header: '角色', class: 'w-[110px]' },
  { key: 'createTime', header: '注册时间', class: 'w-[190px]' },
  { key: 'action', header: '操作', class: 'w-[200px]' }
]

const isSelected = (id?: number) => (id != null ? selectedIds.value.includes(id) : false)

const toggleSelect = (id?: number) => {
  if (id == null) return
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

/** 增删改之后回到第一页重载 —— 原来的 handleSearch 语义。 */
const refreshList = () => {
  selectedIds.value = []
  return reload()
}

const handleBan = async (record: UserVo) => {
  if (record.id == null) return
  try {
    const res = await banUser({ path: { id: record.id } })
    if (res.data?.code === 0) {
      toast.success('已封禁')
      refreshList()
    } else toast.error(res.data?.message || '封禁失败')
  } catch {
    toast.error('封禁失败')
  }
}

const handleUnban = async (record: UserVo) => {
  if (record.id == null) return
  try {
    const res = await unbanUser({ path: { id: record.id } })
    if (res.data?.code === 0) {
      toast.success('已解封')
      refreshList()
    } else toast.error(res.data?.message || '解封失败')
  } catch {
    toast.error('解封失败')
  }
}

const handleDelete = async (record: UserVo) => {
  if (record.id == null) return
  try {
    const res = await deleteUser({ path: { id: record.id } })
    if (res.data?.code === 0) {
      toast.success('已删除')
      refreshList()
    } else toast.error(res.data?.message || '删除失败')
  } catch {
    toast.error('删除失败')
  }
}

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return
  try {
    const res = await batchDeleteUser({ body: { idList: selectedIds.value } })
    if (res.data?.code === 0) {
      toast.success(`已删除 ${selectedIds.value.length} 个用户`)
      refreshList()
    } else toast.error(res.data?.message || '批量删除失败')
  } catch {
    toast.error('批量删除失败')
  }
}

const loadStats = async () => {
  try {
    const res = await getUserStats()
    if (res.data?.code === 0 && res.data.data) {
      const d = res.data.data
      statCards.value = [
        { label: '总用户', value: d.totalCount || 0 },
        { label: '管理员', value: d.adminCount || 0 },
        { label: '已封禁', value: d.banCount || 0 },
        { label: '今日新增', value: d.todayNewCount || 0 }
      ]
    }
  } catch {
    /* 统计失败不阻断列表 */
  }
}

const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | undefined

onMounted(() => {
  reload()
  loadStats()
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
  min-height: 34rem;
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
