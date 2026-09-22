<script setup lang="ts">
/**
 * 「加入题单」选择器：把一批题目加进当前用户自己的一个或多个题单。
 *
 * 题单侧批量接口一次只认一个 {@code questionBankId}，多选时按题单逐个提交，
 * 单个题单失败不影响其余 —— 用户看到的是「加进了几个」，而不是整批回滚。
 *
 * ⚠️ 题目 id 全程保持字符串：雪花 id 19 位，转 number 会丢末位（后端按被舍入的
 * id 查不到题目）。这里的 {@code questionIds} 来自列表行的 {@code id}，不做任何 parseInt。
 */
import { ref, watch } from 'vue'
import { bulkOperateQuestion, listMyQuestionBankVoByPage } from '@generated'
import type { QuestionBankVo } from '@generated'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { toast } from 'vue-sonner'

const props = defineProps<{
  open: boolean
  questionIds: string[]
}>()

const emit = defineEmits<{
  'update:open': [boolean]
  done: []
}>()

const banks = ref<QuestionBankVo[]>([])
const selected = ref<string[]>([])
const loading = ref(false)
const submitting = ref(false)

async function loadBanks() {
  loading.value = true
  try {
    const res = await listMyQuestionBankVoByPage({ body: { current: 1, pageSize: 20 } })
    banks.value = res.data?.data?.records ?? []
  } catch {
    toast.error('加载我的题单失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    selected.value = []
    loadBanks()
  }
)

function toggle(id: string) {
  selected.value = selected.value.includes(id)
    ? selected.value.filter((x) => x !== id)
    : [...selected.value, id]
}

async function submit() {
  if (!selected.value.length || !props.questionIds.length) return
  submitting.value = true
  let ok = 0
  let firstError = ''
  for (const bankId of selected.value) {
    try {
      const res = await bulkOperateQuestion({
        body: {
          questionBankId: bankId,
          questionIdList: props.questionIds,
          action: 'ADD',
        },
      })
      if (res.data?.code === 0) {
        ok++
      } else if (!firstError) {
        firstError = res.data?.message || '加入题单失败'
      }
    } catch {
      if (!firstError) firstError = '加入题单失败'
    }
  }
  submitting.value = false
  if (ok) toast.success(`已加入 ${ok} 个题单`)
  if (firstError) toast.error(firstError)
  emit('update:open', false)
  emit('done')
}
</script>

<template>
  <Dialog :open="open" @update:open="(v) => emit('update:open', v)">
    <DialogContent class="max-w-lg">
      <DialogHeader>
        <DialogTitle>加入题单（{{ questionIds.length }} 题）</DialogTitle>
      </DialogHeader>

      <div class="max-h-[50vh] overflow-y-auto">
        <p v-if="loading" class="py-8 text-center text-sm text-muted-foreground">加载中…</p>
        <p v-else-if="!banks.length" class="py-8 text-center text-sm text-muted-foreground">
          你还没有题单，先到「题库专题」创建一个
        </p>
        <div v-else class="space-y-1">
          <button
            v-for="bank in banks"
            :key="bank.id"
            type="button"
            class="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left hover:bg-muted/50"
            @click="bank.id && toggle(bank.id)"
          >
            <span
              class="flex h-4 w-4 shrink-0 items-center justify-center rounded border border-border"
              :class="bank.id && selected.includes(bank.id) ? 'bg-primary' : ''"
            >
              <span
                v-if="bank.id && selected.includes(bank.id)"
                class="h-2 w-2 rounded-sm bg-primary-foreground"
              />
            </span>
            <span class="truncate text-sm">{{ bank.title || '未命名题单' }}</span>
            <span class="ml-auto shrink-0 text-xs text-muted-foreground">
              {{ bank.questionCount ?? 0 }} 题
            </span>
          </button>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)">取消</Button>
        <Button
          :disabled="!selected.length || submitting"
          @click="submit"
        >
          {{ submitting ? '加入中…' : `加入 ${selected.length} 个题单` }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
