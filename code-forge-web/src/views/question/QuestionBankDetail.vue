<template>
  <div class="uc-page-stack">
    <div v-if="loading" class="py-20 text-center text-muted-foreground">加载中…</div>

    <div v-else-if="bank" class="uc-page-stack">
      <Button variant="ghost" size="sm" @click="router.back()">← 返回</Button>

      <Card>
        <CardHeader>
          <div class="flex items-start justify-between gap-4">
            <div>
              <CardTitle class="text-2xl">{{ bank.title || '未命名题单' }}</CardTitle>
              <CardDescription class="mt-1">{{ bank.description || '暂无描述' }}</CardDescription>
            </div>
            <Badge :variant="bank.isPublic === 1 ? 'default' : 'secondary'">
              {{ bank.isPublic === 1 ? '公开' : '私有' }}
            </Badge>
          </div>
        </CardHeader>
        <CardContent class="flex flex-wrap items-center gap-8">
          <!-- 进度环 -->
          <div
            class="relative h-28 w-28 shrink-0 rounded-full"
            :style="{
              background: `conic-gradient(var(--chart-accent-primary) ${progressPct}%, var(--muted) 0)`
            }"
          >
            <div
              class="absolute inset-3 flex flex-col items-center justify-center rounded-full bg-card"
            >
              <span class="text-xl font-semibold">{{ progressPct }}%</span>
              <span class="text-xs text-muted-foreground">通过率</span>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-x-10 gap-y-3 text-sm sm:grid-cols-3">
            <div>
              <p class="text-muted-foreground">题目数</p>
              <p class="text-lg font-semibold">{{ bank.questionCount || 0 }}</p>
            </div>
            <div>
              <p class="text-muted-foreground">已解出</p>
              <p class="text-lg font-semibold">{{ bank.solvedCount || 0 }}</p>
            </div>
            <div>
              <p class="text-muted-foreground">Fork 数</p>
              <p class="text-lg font-semibold">{{ bank.forkNum || 0 }}</p>
            </div>
            <div>
              <p class="text-muted-foreground">创建者</p>
              <p class="font-semibold">{{ bank.userVO?.username || '-' }}</p>
            </div>
          </div>

          <Button class="ml-auto" :disabled="forking" @click="handleFork">Fork 此题单</Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle class="text-lg">题目列表（{{ bank.questionList?.length || 0 }}）</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="bank.questionList && bank.questionList.length" class="divide-y divide-border">
            <button
              v-for="q in bank.questionList"
              :key="q.id"
              class="flex w-full items-center justify-between gap-4 py-3 text-left hover:bg-muted/40"
              @click="goQuestion(q)"
            >
              <div class="min-w-0">
                <p class="truncate font-medium">{{ q.title || '未命名题目' }}</p>
                <div v-if="q.tags?.length" class="mt-1 flex flex-wrap gap-1">
                  <Badge v-for="t in q.tags" :key="t" variant="secondary">{{ t }}</Badge>
                </div>
              </div>
              <span class="shrink-0 text-sm text-muted-foreground">{{ q.difficulty || '-' }}</span>
            </button>
          </div>
          <p v-else class="py-8 text-center text-muted-foreground">该题单暂无题目</p>
        </CardContent>
      </Card>
    </div>

    <div v-else class="py-20 text-center text-muted-foreground">题单不存在或加载失败</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getQuestionBankVo, forkQuestionBank } from '@generated'
import type { QuestionBankVo, QuestionVo } from '@generated'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { toast } from 'vue-sonner'

const route = useRoute()
const router = useRouter()

const bank = ref<QuestionBankVo | null>(null)
const loading = ref(false)
const forking = ref(false)

const bankId = computed(() => Number(route.params.id))

const progressPct = computed(() => {
  const total = bank.value?.questionCount || 0
  const solved = bank.value?.solvedCount || 0
  if (!total) return 0
  return Math.round((solved / total) * 100)
})

const load = async () => {
  loading.value = true
  try {
    const res = await getQuestionBankVo({ path: { id: bankId.value } })
    if (res.data?.code === 0 && res.data.data) {
      bank.value = res.data.data
    } else {
      toast.error(res.data?.message || '加载题单失败')
    }
  } catch {
    toast.error('加载题单失败')
  } finally {
    loading.value = false
  }
}

const handleFork = async () => {
  forking.value = true
  try {
    const res = await forkQuestionBank({ path: { id: bankId.value } })
    if (res.data?.code === 0) {
      toast.success('已 Fork 该题单')
      load()
    } else {
      toast.error(res.data?.message || 'Fork 失败')
    }
  } catch {
    toast.error('Fork 失败')
  } finally {
    forking.value = false
  }
}

const goQuestion = (q: QuestionVo) => {
  if (q.id == null) return
  router.push(`/questions/${q.id}/view`)
}

onMounted(load)
</script>
