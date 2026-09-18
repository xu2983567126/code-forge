<template>
  <div class="uc-page-stack">
    <div v-if="loading" class="py-20 text-center text-muted-foreground">加载中…</div>

    <div v-else-if="me" class="uc-page-stack">
      <div class="flex items-center gap-4">
        <div
          class="flex h-14 w-14 items-center justify-center rounded-full bg-primary/10 text-lg font-semibold text-primary"
        >
          {{ (me.username || '?').charAt(0).toUpperCase() }}
        </div>
        <div>
          <h1 class="text-xl font-semibold">{{ me.username || '未命名用户' }}</h1>
          <p class="text-sm text-muted-foreground">角色：{{ me.role || 'USER' }}</p>
        </div>
      </div>

      <div class="grid grid-cols-3 gap-4">
        <Card>
          <CardContent class="p-4">
            <p class="text-sm text-muted-foreground">统计周期（天）</p>
            <p class="text-2xl font-semibold">{{ heat?.days || 0 }}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-4">
            <p class="text-sm text-muted-foreground">活跃天数</p>
            <p class="text-2xl font-semibold">{{ heat?.activeDays || 0 }}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-4">
            <p class="text-sm text-muted-foreground">总提交</p>
            <p class="text-2xl font-semibold">{{ heat?.totalCount || 0 }}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle class="text-base">活跃热力图</CardTitle></CardHeader>
        <CardContent>
          <div v-if="heatCells.length" class="flex flex-wrap gap-1">
            <div
              v-for="(c, i) in heatCells"
              :key="i"
              class="h-4 w-4 rounded-sm"
              :style="{ backgroundColor: c.color }"
              :title="c.title"
            />
          </div>
          <p v-else class="text-sm text-muted-foreground">暂无活跃数据</p>
        </CardContent>
      </Card>
    </div>

    <div v-else class="py-20 text-center text-muted-foreground">加载失败，请先登录</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getLoginUser, getUserHeatmap } from '@generated'
import type { LoginUserVo, UserHeatmapDto } from '@generated'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { toast } from 'vue-sonner'

const me = ref<LoginUserVo | null>(null)
const heat = ref<UserHeatmapDto | null>(null)
const loading = ref(false)

const heatCells = computed(() => {
  const list = heat.value?.dailyCounts || []
  const max = Math.max(1, ...list.map((d) => d.count || 0))
  return list.map((d) => {
    const intensity = (d.count || 0) / max
    return {
      count: d.count || 0,
      title: `${d.date}: ${d.count || 0}`,
      color:
        intensity === 0
          ? 'var(--muted)'
          : `color-mix(in srgb, var(--chart-accent-primary) ${Math.round(intensity * 100)}%, var(--muted))`
    }
  })
})

const load = async () => {
  loading.value = true
  try {
    const meRes = await getLoginUser()
    if (meRes.data?.code === 0 && meRes.data.data) {
      me.value = meRes.data.data
      if (me.value.id != null) {
        const heatRes = await getUserHeatmap({ path: { id: me.value.id } })
        if (heatRes.data?.code === 0) heat.value = heatRes.data.data || null
      }
    } else {
      toast.error(meRes.data?.message || '获取用户信息失败')
    }
  } catch {
    toast.error('加载个人主页失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
