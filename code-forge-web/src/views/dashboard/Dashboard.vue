<template>
  <div class="uc-page-stack">
    <PageHeader title="平台仪表板" description="全局用户 / 题目 / 提交统计" />

    <div v-if="loading" class="py-20 text-center text-muted-foreground">加载中…</div>

    <template v-else-if="data">
      <!-- 核心指标 -->
      <div class="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Card>
          <CardContent class="p-4">
            <p class="text-sm text-muted-foreground">用户总数</p>
            <p class="text-2xl font-semibold">{{ data.userStats?.totalCount || 0 }}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-4">
            <p class="text-sm text-muted-foreground">题目总数</p>
            <p class="text-2xl font-semibold">{{ data.questionStats?.totalCount || 0 }}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-4">
            <p class="text-sm text-muted-foreground">提交总数</p>
            <p class="text-2xl font-semibold">{{ data.submissionStats?.totalCount || 0 }}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-4">
            <p class="text-sm text-muted-foreground">通过率</p>
            <p class="text-2xl font-semibold">
              {{ (data.submissionStats?.acceptedRate || 0).toFixed(1) }}%
            </p>
          </CardContent>
        </Card>
      </div>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle class="text-base">题目难度分布</CardTitle></CardHeader>
          <CardContent>
            <div v-if="distEntries(data.questionStats?.difficultyDistribution).length">
              <div
                v-for="e in distEntries(data.questionStats?.difficultyDistribution)"
                :key="e.key"
                class="mb-2"
              >
                <div class="flex justify-between text-sm">
                  <span>{{ e.key || '未知' }}</span>
                  <span class="text-muted-foreground">{{ e.val }}</span>
                </div>
                <div class="h-2 w-full rounded bg-muted">
                  <div
                    class="h-2 rounded bg-chart-accent-primary"
                    :style="{ width: e.pct + '%' }"
                  />
                </div>
              </div>
            </div>
            <p v-else class="text-sm text-muted-foreground">暂无数据</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle class="text-base">提交判题结果分布</CardTitle></CardHeader>
          <CardContent>
            <div v-if="distEntries(data.submissionStats?.verdictDistribution).length">
              <div
                v-for="e in distEntries(data.submissionStats?.verdictDistribution)"
                :key="e.key"
                class="mb-2"
              >
                <div class="flex justify-between text-sm">
                  <span>{{ e.key || '未知' }}</span>
                  <span class="text-muted-foreground">{{ e.val }}</span>
                </div>
                <div class="h-2 w-full rounded bg-muted">
                  <div
                    class="h-2 rounded bg-chart-accent-primary"
                    :style="{ width: e.pct + '%' }"
                  />
                </div>
              </div>
            </div>
            <p v-else class="text-sm text-muted-foreground">暂无数据</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle class="text-base">提交语言分布</CardTitle></CardHeader>
          <CardContent>
            <div v-if="distEntries(data.submissionStats?.languageDistribution).length">
              <div
                v-for="e in distEntries(data.submissionStats?.languageDistribution)"
                :key="e.key"
                class="mb-2"
              >
                <div class="flex justify-between text-sm">
                  <span>{{ e.key || '未知' }}</span>
                  <span class="text-muted-foreground">{{ e.val }}</span>
                </div>
                <div class="h-2 w-full rounded bg-muted">
                  <div
                    class="h-2 rounded bg-chart-accent-primary"
                    :style="{ width: e.pct + '%' }"
                  />
                </div>
              </div>
            </div>
            <p v-else class="text-sm text-muted-foreground">暂无数据</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle class="text-base">近 14 日提交趋势</CardTitle></CardHeader>
          <CardContent>
            <div
              v-if="trend.length"
              class="flex h-32 items-end gap-1"
            >
              <div
                v-for="d in trend"
                :key="d.date"
                class="flex-1 rounded-t bg-chart-accent-primary/70"
                :style="{ height: d.height }"
                :title="`${d.date}: ${d.count}`"
              />
            </div>
            <p v-else class="text-sm text-muted-foreground">暂无数据</p>
          </CardContent>
        </Card>
      </div>
    </template>

    <div v-else class="py-20 text-center text-muted-foreground">数据加载失败</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboardStats } from '@generated'
import type { DashboardStatsVo } from '@generated'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import PageHeader from '@/components/PageHeader.vue'
import { toast } from 'vue-sonner'

const data = ref<DashboardStatsVo | null>(null)
const loading = ref(false)

const distEntries = (
  dist?: Record<string, number>
): Array<{ key: string; val: number; pct: number }> => {
  if (!dist) return []
  const entries = Object.entries(dist)
  const max = Math.max(1, ...entries.map(([, v]) => v))
  return entries.map(([k, v]) => ({
    key: k,
    val: v,
    pct: Math.round((v / max) * 100)
  }))
}

const trend = computed(() => {
  const list = data.value?.submissionStats?.recentDailyTrend || []
  const max = Math.max(1, ...list.map((d) => d.count || 0))
  return list.map((d) => ({
    date: d.date || '',
    count: d.count || 0,
    height: `${Math.max(6, ((d.count || 0) / max) * 100)}%`
  }))
})

const load = async () => {
  loading.value = true
  try {
    const res = await getDashboardStats()
    if (res.data?.code === 0 && res.data.data) {
      data.value = res.data.data
    } else {
      toast.error(res.data?.message || '加载仪表板失败')
    }
  } catch {
    toast.error('加载仪表板失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
