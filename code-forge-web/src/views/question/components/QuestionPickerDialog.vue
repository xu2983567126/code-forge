<script setup lang="ts">
/**
 * 「添加题目」选择器：在题单详情页挑题目加进当前题单。
 *
 * 与 {@code BankPickerDialog} 正好反向 —— 那边是「固定题目、选题单」，这里是
 * 「固定题单、选题目」。两者最终都走 {@code bulkOperateQuestion}，只是传入维度不同。
 *
 * ⚠️ 已勾选集合只在本次会话内累计：翻页后旧页的勾选要留住，不能按「当前页」重置，
 * 否则用户翻一页就丢一批选择。
 */
import { ref, watch } from 'vue'
import { bulkOperateQuestion, listQuestionVoByPage } from '@generated'
import type { QuestionVo } from '@generated'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import { toast } from 'vue-sonner'

const props = defineProps<{
  open: boolean
  /** 目标题单 id（雪花，字符串） */
  questionBankId: string
}>()

const emit = defineEmits<{
  'update:open': [boolean]
  done: []
}>()

const keyword = ref('')
const questions = ref<QuestionVo[]>([])
const selected = ref<string[]>([])
const loading = ref(false)
const submitting = ref(false)
const total = ref(0)
const current = ref(1)

async function load(reset: boolean) {
  if (reset) current.value = 1
  loading.value = true
  try {
    const res = await listQuestionVoByPage({
      body: { current: current.value, pageSize: 20, title: keyword.value || undefined },
    })
    const data = res.data?.data
    questions.value = data?.records ?? []
    total.value = Number(data?.total) || 0
  } catch {
    toast.error('加载题目失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    keyword.value = ''
    selected.value = []
    load(true)
  }
)

function toggle(id: string) {
  selected.value = selected.value.includes(id)
    ? selected.value.filter((x) => x !== id)
    : [...selected.value, id]
}

async function submit() {
  if (!selected.value.length) return
  submitting.value = true
  try {
    const res = await bulkOperateQuestion({
      body: {
        questionBankId: props.questionBankId,
        questionIdList: selected.value,
        action: 'ADD',
      },
    })
    if (res.data?.code === 0) {
      toast.success(`已添加 ${res.data.data ?? selected.value.length} 道题目`)
      emit('update:open', false)
      emit('done')
    } else {
      toast.error(res.data?.message || '添加题目失败')
    }
  } catch {
    toast.error('添加题目失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="(v) => emit('update:open', v)">
    <DialogContent class="max-w-xl">
      <DialogHeader>
        <DialogTitle>添加题目到题单</DialogTitle>
      </DialogHeader>

      <div class="flex gap-2">
        <Input v-model="keyword" placeholder="搜索题目标题" @keyup.enter="load(true)" />
        <Button variant="outline" @click="load(true)">搜索</Button>
      </div>

      <div class="max-h-[45vh] overflow-y-auto">
        <p v-if="loading" class="py-8 text-center text-sm text-muted-foreground">加载中…</p>
        <p v-else-if="!questions.length" class="py-8 text-center text-sm text-muted-foreground">
          没有匹配的题目
        </p>
        <div v-else class="space-y-1">
          <button
            v-for="q in questions"
            :key="q.id"
            type="button"
            class="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left hover:bg-muted/50"
            @click="q.id && toggle(q.id)"
          >
            <span
              class="flex h-4 w-4 shrink-0 items-center justify-center rounded border border-border"
              :class="q.id && selected.includes(q.id) ? 'bg-primary' : ''"
            >
              <span
                v-if="q.id && selected.includes(q.id)"
                class="h-2 w-2 rounded-sm bg-primary-foreground"
              />
            </span>
            <span class="truncate text-sm">{{ q.title || '未命名题目' }}</span>
            <Badge v-if="q.difficulty" variant="secondary" class="shrink-0">
              {{ q.difficulty }}
            </Badge>
          </button>
        </div>
      </div>

      <div v-if="total > 20" class="flex items-center justify-between text-xs text-muted-foreground">
        <span>共 {{ total }} 条，已选 {{ selected.length }} 题</span>
        <div class="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            :disabled="current <= 1 || loading"
            @click="current--; load(false)"
          >
            上一页
          </Button>
          <Button
            variant="outline"
            size="sm"
            :disabled="current * 20 >= total || loading"
            @click="current++; load(false)"
          >
            下一页
          </Button>
        </div>
      </div>
      <p v-else class="text-xs text-muted-foreground">已选 {{ selected.length }} 题</p>

      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)">取消</Button>
        <Button :disabled="!selected.length || submitting" @click="submit">
          {{ submitting ? '添加中…' : `添加 ${selected.length} 道` }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
