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

          <div class="ml-auto flex items-center gap-2">
            <Button variant="outline" :disabled="favouriting" @click="toggleFavourite">
              {{ bank.isFavourited ? '取消收藏' : '收藏题单' }}
            </Button>
            <Button :disabled="forking" @click="handleFork">Fork 此题单</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div class="flex items-center justify-between gap-4">
            <CardTitle class="text-lg">题目列表（{{ total }}）</CardTitle>
            <!-- 增删入口只对题单本人开放：后端 checkBankAuth 是同一口径 -->
            <Button v-if="isOwner" size="sm" @click="pickerOpen = true">添加题目</Button>
          </div>
        </CardHeader>
        <CardContent>
          <div v-if="questionLoading" class="py-8 text-center text-sm text-muted-foreground">
            加载中…
          </div>
          <div v-else-if="items.length" class="divide-y divide-border">
            <div v-for="q in items" :key="q.id" class="flex items-center justify-between gap-4 py-3">
              <button
                class="flex min-w-0 flex-1 items-center gap-4 text-left hover:text-primary"
                @click="goQuestion(q)"
              >
                <span class="truncate font-medium">{{ q.title || '未命名题目' }}</span>
                <span v-if="q.difficulty" class="shrink-0 text-xs text-muted-foreground">
                  {{ q.difficulty }}
                </span>
              </button>
              <Button
                v-if="isOwner"
                variant="ghost"
                size="sm"
                class="shrink-0"
                @click="removeQuestion(q)"
              >
                移出题单
              </Button>
            </div>
          </div>
          <p v-else class="py-8 text-center text-muted-foreground">该题单暂无题目</p>

          <div v-if="hasMore" class="pt-4 text-center">
            <Button variant="outline" size="sm" :disabled="questionLoadingMore" @click="loadMore">
              {{ questionLoadingMore ? '加载中…' : '加载更多' }}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>

    <div v-else class="py-20 text-center text-muted-foreground">题单不存在或加载失败</div>

    <QuestionPickerDialog
      v-if="bank?.id"
      v-model:open="pickerOpen"
      :question-bank-id="bank.id"
      @done="onQuestionsChanged"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  favouriteBank,
  forkQuestionBank,
  getQuestionBankVo,
  pageQuestionsInBank,
  removeQuestionFromBank,
  unfavouriteBank,
} from '@generated'
import type { QuestionBankVo, QuestionVo } from '@generated'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { toast } from 'vue-sonner'
import { useUserStore } from '@/store/user'
import { useInfiniteList } from '@/composables/useInfiniteList'
import QuestionPickerDialog from './components/QuestionPickerDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const bank = ref<QuestionBankVo | null>(null)
const loading = ref(false)
const forking = ref(false)
const favouriting = ref(false)
const pickerOpen = ref(false)

/**
 * ⚠️ 题单 id 是雪花（19 位），转 Number 会丢末位导致查不到 —— path 参数保持字符串，
 * 与提交详情页同一套处理（详见 ViewQuestion / SubmissionDetail 的注释）。
 */
const bankId = computed(() => route.params.id as string)

const isOwner = computed(
  () =>
    !!bank.value?.userId &&
    !!userStore.loginUser.id &&
    bank.value.userId === userStore.loginUser.id
)

const progressPct = computed(() => {
  const total = bank.value?.questionCount || 0
  const solved = bank.value?.solvedCount || 0
  if (!total) return 0
  return Math.round((solved / total) * 100)
})

/**
 * 题单内题目走独立分页接口：题目数量没有上限，塞进详情 VO 会把响应撑爆，
 * 也让详情页失去翻页能力。与题目列表页共用 useInfiniteList（同一套竞态 / 防抖保护）。
 */
const {
  items,
  loading: questionLoading,
  loadingMore: questionLoadingMore,
  total,
  hasMore,
  reload: reloadQuestions,
  loadMore,
} = useInfiniteList<QuestionVo>({
  // useInfiniteList 只拼 body，path 上的题单 id 在这里补
  api: (params: any) => pageQuestionsInBank({ ...params, path: { questionBankId: bankId.value } }),
  buildParams: (page) => ({ current: page.current, pageSize: page.pageSize }),
  pageSize: 20,
})

const load = async () => {
  loading.value = true
  try {
    const res = await getQuestionBankVo({
      path: { id: bankId.value },
    })
    if (res.data?.code === 0 && res.data.data) {
      bank.value = res.data.data
      await reloadQuestions()
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
    const res = await forkQuestionBank({
      path: { id: bankId.value },
    })
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

const toggleFavourite = async () => {
  if (!bank.value?.id) return
  favouriting.value = true
  try {
    const res = bank.value.isFavourited
      ? await unfavouriteBank({ path: { bankId: bank.value.id } })
      : await favouriteBank({ path: { bankId: bank.value.id } })
    if (res.data?.code === 0) {
      const nowFavourited = !bank.value.isFavourited
      bank.value.isFavourited = nowFavourited
      toast.success(nowFavourited ? '已收藏题单' : '已取消收藏')
    } else {
      toast.error(res.data?.message || '操作失败')
    }
  } catch {
    toast.error('操作失败')
  } finally {
    favouriting.value = false
  }
}

const removeQuestion = async (q: QuestionVo) => {
  if (!bank.value?.id || !q.id) return
  try {
    const res = await removeQuestionFromBank({
      query: { questionBankId: bank.value.id, questionId: q.id },
    })
    if (res.data?.code === 0) {
      toast.success('已移出题单')
      await load()
    } else {
      toast.error(res.data?.message || '移出失败')
    }
  } catch {
    toast.error('移出失败')
  }
}

/** 添加题目后题目数变了，元信息与列表一起刷新 */
const onQuestionsChanged = async () => {
  await load()
}

const goQuestion = (q: QuestionVo) => {
  if (q.id == null) return
  router.push(`/questions/${q.id}/view`)
}

onMounted(load)
</script>
