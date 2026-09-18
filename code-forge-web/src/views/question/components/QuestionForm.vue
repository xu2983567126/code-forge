<template>
  <div class="uc-page-stack">
    <div class="mx-auto w-full max-w-[900px] rounded-lg border bg-card p-8 shadow-sm">
      <h1 class="mb-8 text-center text-2xl font-semibold text-foreground-strong">{{ pageTitle }}</h1>

      <div class="mx-auto max-w-[800px] space-y-6">
        <FormField label="题目标题" required>
          <Input v-model="formData.title" placeholder="请输入题目标题" />
        </FormField>

        <FormField label="题目内容" required>
          <MarkdownEdit v-model="formData.content" />
        </FormField>

        <FormField label="题目答案" required>
          <MarkdownEdit v-model="formData.answer" />
        </FormField>

        <FormField label="标签">
          <TagInput v-model="formData.tags" />
        </FormField>

        <Card>
          <CardHeader>
            <CardTitle class="text-base">判题配置</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <FormField label="时间限制（毫秒）" required>
              <Input
                type="number"
                min="0"
                v-model.number="formData.judgeConfig.timeLimit"
                class="max-w-[400px]"
              />
            </FormField>
            <FormField label="内存限制（KB）" required>
              <Input
                type="number"
                min="0"
                v-model.number="formData.judgeConfig.memoryLimit"
                class="max-w-[400px]"
              />
            </FormField>
            <FormField label="堆栈限制（KB）" required>
              <Input
                type="number"
                min="0"
                v-model.number="formData.judgeConfig.stackLimit"
                class="max-w-[400px]"
              />
            </FormField>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle class="text-base">测试用例</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <div
              v-for="(judgeCaseItem, index) in formData.judgeCase"
              :key="index"
              class="flex items-end gap-3 rounded-md border p-4"
            >
              <FormField label="输入" class="flex-1">
                <Input v-model="judgeCaseItem.input" placeholder="输入" />
              </FormField>
              <FormField label="期望输出" class="flex-1">
                <Input v-model="judgeCaseItem.output" placeholder="期望输出" />
              </FormField>
              <Button variant="destructive" @click="removeJudgeCase(index)">删除</Button>
            </div>
            <Button variant="outline" class="w-[200px]" @click="addJudgeCase">
              + 添加测试用例
            </Button>
          </CardContent>
        </Card>

        <div class="flex justify-center gap-3 border-t pt-6">
          <Button :disabled="submitting" @click="handleSubmit">{{ submitBtnText }}</Button>
          <Button variant="outline" @click="handleReset">重置</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { toast } from 'vue-sonner'
import MarkdownEdit from '@/components/markdown/MarkdownEdit.vue'
import TagInput from '@/components/ui-custom/TagInput.vue'
import FormField from '@/components/ui-custom/FormField.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  createQuestion,
  updateQuestion,
  type QuestionCreateRequest,
  type QuestionUpdateRequest
} from '@generated'

// 表单内部使用「必填」结构，提交时再断言为生成出的（全可选）DTO，
// 避免 @generated 把字段全标 optional 导致模板里每一步都报 possibly undefined。
interface JudgeConfigForm {
  timeLimit: number
  memoryLimit: number
  stackLimit: number
}
interface JudgeCaseForm {
  input: string
  output: string
}
interface QuestionFormData {
  id: number
  title: string
  content: string
  answer: string
  tags: string[]
  judgeCase: JudgeCaseForm[]
  judgeConfig: JudgeConfigForm
}

// ---------- Props 定义 ----------
interface Props {
  mode: 'create' | 'edit' // 模式
  initialData?: Partial<QuestionCreateRequest> // 初始数据（编辑时由父组件传入）
  questionId?: number // 编辑时的题目ID
  title?: string // 页面标题，默认根据模式生成
  submitButtonText?: string // 提交按钮文字
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'create',
  initialData: () => ({}),
  questionId: 0,
  title: undefined,
  submitButtonText: undefined
})

// ---------- Emits 定义 ----------
const emit = defineEmits<{
  submitSuccess: [result: any] // 提交成功，传递返回结果
  submitError: [error: any] // 提交失败
  reset: [] // 重置表单
}>()

// ---------- 计算属性 ----------
const pageTitle = computed(() => props.title || (props.mode === 'create' ? '创建题目' : '编辑题目'))
const submitBtnText = computed(
  () => props.submitButtonText || (props.mode === 'create' ? '提交题目' : '更新题目')
)

// ---------- 表单数据 ----------
const getDefaultFormData = (): QuestionFormData => ({
  id: 0,
  title: '',
  content: '',
  tags: [],
  answer: '',
  judgeCase: [{ input: '', output: '' }],
  judgeConfig: { timeLimit: 0, memoryLimit: 0, stackLimit: 0 }
})

const formData = reactive<QuestionFormData>(getDefaultFormData())
const submitting = ref(false)

const handleReset = () => {
  Object.assign(formData, getDefaultFormData())
  emit('reset')
}

// ---------- 监听 initialData 变化，填充表单 ----------
watch(
  () => props.initialData,
  (newData) => {
    if (newData && Object.keys(newData).length > 0) {
      // 合并初始数据，保持判题配置数字类型
      const merged = { ...getDefaultFormData(), ...newData }
      if (merged.judgeConfig) {
        merged.judgeConfig = {
          timeLimit: Number(merged.judgeConfig.timeLimit) || 0,
          memoryLimit: Number(merged.judgeConfig.memoryLimit) || 0,
          stackLimit: Number(merged.judgeConfig.stackLimit) || 0
        }
      }
      Object.assign(formData, merged)
    } else if (props.mode === 'create') {
      // 创建模式且无 initialData，重置表单
      handleReset()
    }
  },
  { immediate: true, deep: true }
)

// ---------- 表单方法 ----------
const addJudgeCase = () => {
  formData.judgeCase.push({ input: '', output: '' })
}

const removeJudgeCase = (index: number) => {
  if (formData.judgeCase.length > 1) {
    formData.judgeCase.splice(index, 1)
  } else {
    toast.warning('至少需要保留一个测试用例')
  }
}

const validateForm = (): boolean => {
  if (!formData.title.trim()) {
    toast.error('请输入题目标题')
    return false
  }
  if (!formData.content.trim()) {
    toast.error('请输入题目内容')
    return false
  }
  if (!formData.answer.trim()) {
    toast.error('请输入题目答案')
    return false
  }
  if (formData.judgeConfig.timeLimit <= 0) {
    toast.error('请输入有效的时间限制')
    return false
  }
  if (formData.judgeConfig.memoryLimit <= 0) {
    toast.error('请输入有效的内存限制')
    return false
  }
  for (const [idx, item] of formData.judgeCase.entries()) {
    if (!item.input.trim() || !item.output.trim()) {
      toast.error(`请完善测试用例 ${idx + 1}`)
      return false
    }
  }
  return true
}

const handleSubmit = async () => {
  if (!validateForm()) return

  submitting.value = true
  try {
    const submitData: QuestionFormData = {
      ...formData,
      tags: Array.isArray(formData.tags) ? formData.tags : [],
      judgeCase: Array.isArray(formData.judgeCase) ? formData.judgeCase : [],
      judgeConfig: { ...formData.judgeConfig }
    }

    let result
    if (props.mode === 'edit') {
      if (!props.questionId) throw new Error('编辑模式缺少题目ID')
      result = await updateQuestion({
        path: { id: props.questionId },
        body: submitData as unknown as QuestionUpdateRequest
      })
    } else {
      result = await createQuestion({ body: submitData as unknown as QuestionCreateRequest })
      if (props.mode === 'create') handleReset() // 创建成功后自动重置
    }
    emit('submitSuccess', result)
  } catch (error) {
    const action = props.mode === 'edit' ? '更新' : '创建'
    toast.error(`题目${action}失败，请重试`)
    console.error(`题目${action}失败:`, error)
    emit('submitError', error)
  } finally {
    submitting.value = false
  }
}
</script>
