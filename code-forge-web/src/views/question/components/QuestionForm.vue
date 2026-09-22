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
            <CardDescription>输出比对模式决定「用户输出算不算和标准答案一致」</CardDescription>
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
            <FormField label="输出比对模式" required>
              <select
                v-model="formData.judgeConfig.compareMode"
                class="h-9 w-full max-w-[400px] rounded-md border border-input bg-background px-3 text-sm"
              >
                <option v-for="mode in COMPARE_MODE_OPTIONS" :key="mode.value" :value="mode.value">
                  {{ mode.label }}
                </option>
              </select>
              <p class="text-xs text-muted-foreground">{{ currentCompareModeHint }}</p>
            </FormField>
          </CardContent>
        </Card>

        <Card v-if="isSpjMode">
          <CardHeader>
            <div class="flex items-start justify-between gap-3">
              <div class="space-y-1">
                <CardTitle class="text-base">特判程序</CardTitle>
                <CardDescription>
                  逐用例调用一次：标准输入 = 测试输入，argv[0] = 标准答案，argv[1] = 用户输出
                </CardDescription>
              </div>
              <Button variant="outline" size="sm" @click="fillSpjTemplate">填充模板</Button>
            </div>
          </CardHeader>
          <CardContent class="space-y-4">
            <FormField label="特判语言" required>
              <select
                v-model="formData.spjLanguage"
                class="h-9 w-full max-w-[400px] rounded-md border border-input bg-background px-3 text-sm"
              >
                <option v-for="opt in LANGUAGE_OPTIONS" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </FormField>

            <FormField label="特判代码" required>
              <div class="h-[320px] w-full overflow-hidden rounded-md border border-border">
                <CodeEditor
                  v-model="formData.spjCode"
                  :language="formData.spjLanguage"
                  :theme="monacoTheme"
                  :minimap="false"
                />
              </div>
            </FormField>

            <div class="space-y-1 rounded-md border bg-muted/40 p-3 text-xs text-muted-foreground">
              <p>
                退出码约定：<span
                  v-for="(item, index) in SPJ_EXIT_CODES"
                  :key="item.code"
                  class="whitespace-nowrap"
                >
                  {{ item.code }} = {{ item.text }}<span v-if="index < SPJ_EXIT_CODES.length - 1">，</span>
                </span>，其它 = 系统错误。
              </p>
              <p>特判程序自身超时或崩溃记系统错误，判为题目配置问题，不计到用户头上。</p>
              <p>留空特判代码时，判题会回落标准比对。</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle class="text-base">Solution 模板（核心代码模式）</CardTitle>
            <CardDescription>
              用户只需实现 Solution 中的方法；驱动代码由后端按签名自动生成。首版仅支持基础类型、一维数组与
              List（X 为基础类型）。
            </CardDescription>
          </CardHeader>
          <CardContent class="space-y-4">
            <FormField label="方法名" required>
              <Input v-model="formData.solutionSignature.methodName" placeholder="如 twoSum" />
            </FormField>
            <FormField label="返回类型" required>
              <select
                v-model="formData.solutionSignature.returnType"
                class="h-9 w-full max-w-[400px] rounded-md border border-input bg-background px-3 text-sm"
              >
                <option v-for="t in SOLUTION_TYPE_OPTIONS" :key="t" :value="t">{{ t }}</option>
              </select>
            </FormField>

            <div class="space-y-3">
              <div class="text-sm font-medium">参数列表</div>
              <div
                v-for="(param, index) in formData.solutionSignature.params"
                :key="index"
                class="flex items-end gap-3 rounded-md border p-3"
              >
                <FormField label="类型" class="w-[220px]">
                  <select
                    v-model="param.type"
                    class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                  >
                    <option v-for="t in SOLUTION_TYPE_OPTIONS" :key="t" :value="t">{{ t }}</option>
                  </select>
                </FormField>
                <FormField label="名称" class="flex-1">
                  <Input v-model="param.name" placeholder="如 nums" />
                </FormField>
                <Button variant="destructive" @click="removeSolutionParam(index)">删除</Button>
              </div>
              <Button variant="outline" class="w-[200px]" @click="addSolutionParam">+ 添加参数</Button>
            </div>

            <FormField label="骨架预览（自动生成，提交时作为题级 codeTemplate）">
              <pre
                class="h-[140px] w-full overflow-auto rounded-md border border-border bg-muted/40 p-3 text-xs"
                >{{ solutionCodeTemplate }}</pre
              >
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
                <Input v-model="judgeCaseItem.expectedOutput" placeholder="期望输出" />
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
import CodeEditor from '@/components/CodeEditor.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { LANGUAGE_OPTIONS, DEFAULT_LANGUAGE } from '@/constants/language'
import {
  COMPARE_MODE_OPTIONS,
  DEFAULT_COMPARE_MODE,
  SPJ_EXIT_CODES,
  SPJ_TEMPLATES
} from '@/constants/spj'
import {
  SOLUTION_TYPE_OPTIONS,
  buildSolutionSkeleton,
  parseSolutionSkeleton,
  type SolutionSignature
} from '@/constants/solutionTemplate'
import { useTheme } from '@/lib/theme/useTheme'
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
  compareMode: string
}
interface JudgeCaseForm {
  input: string
  expectedOutput: string
}
interface QuestionFormData {
  id: string
  title: string
  content: string
  answer: string
  tags: string[]
  judgeCase: JudgeCaseForm[]
  judgeConfig: JudgeConfigForm
  spjCode: string
  spjLanguage: string
  solutionSignature: SolutionSignature
}

// ---------- Props 定义 ----------
interface Props {
  mode: 'create' | 'edit' // 模式
  initialData?: Partial<QuestionCreateRequest> // 初始数据（编辑时由父组件传入）
  questionId?: string // 编辑时的题目ID
  title?: string // 页面标题，默认根据模式生成
  submitButtonText?: string // 提交按钮文字
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'create',
  initialData: () => ({}),
  questionId: '',
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

const isSpjMode = computed(() => formData.judgeConfig.compareMode === 'SPJ')
const currentCompareModeHint = computed(
  () =>
    COMPARE_MODE_OPTIONS.find((item) => item.value === formData.judgeConfig.compareMode)?.hint ?? ''
)
// 编辑器跟随站点主题，避免浅色页面里嵌一块深色代码区
const { resolved } = useTheme()
const monacoTheme = computed<'vs-dark' | 'vs-light'>(() =>
  resolved.value === 'dark' ? 'vs-dark' : 'vs-light'
)

// 由签名实时生成骨架（镜像后端 CodeTemplateGenerator.renderSkeleton），作为题级 codeTemplate 发送
const solutionCodeTemplate = computed(() => buildSolutionSkeleton(formData.solutionSignature))

// ---------- 表单数据 ----------
const getDefaultFormData = (): QuestionFormData => ({
  id: '',
  title: '',
  content: '',
  tags: [],
  answer: '',
  judgeCase: [{ input: '', expectedOutput: '' }],
  judgeConfig: { timeLimit: 0, memoryLimit: 0, stackLimit: 0, compareMode: DEFAULT_COMPARE_MODE },
  spjCode: '',
  spjLanguage: DEFAULT_LANGUAGE,
  solutionSignature: { methodName: '', returnType: 'int', params: [] }
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
          stackLimit: Number(merged.judgeConfig.stackLimit) || 0,
          compareMode: merged.judgeConfig.compareMode || DEFAULT_COMPARE_MODE
        }
      }
      Object.assign(formData, merged)
      // 编辑时把题级 codeTemplate（Solution 骨架）反解析回签名表单，便于二次编辑
      const parsed = parseSolutionSkeleton((newData as any).codeTemplate)
      if (parsed) {
        formData.solutionSignature = parsed
      }
    } else if (props.mode === 'create') {
      // 创建模式且无 initialData，重置表单
      handleReset()
    }
  },
  { immediate: true, deep: true }
)

// ---------- 表单方法 ----------
const addJudgeCase = () => {
  formData.judgeCase.push({ input: '', expectedOutput: '' })
}

const removeJudgeCase = (index: number) => {
  if (formData.judgeCase.length > 1) {
    formData.judgeCase.splice(index, 1)
  } else {
    toast.warning('至少需要保留一个测试用例')
  }
}

const addSolutionParam = () => {
  formData.solutionSignature.params.push({ type: 'int', name: '' })
}

const removeSolutionParam = (index: number) => {
  formData.solutionSignature.params.splice(index, 1)
}

const fillSpjTemplate = () => {
  const template = SPJ_TEMPLATES[formData.spjLanguage] ?? ''
  if (!template) {
    toast.error('该语言暂无特判模板')
    return
  }
  if (formData.spjCode.trim() && !window.confirm('当前特判代码将被模板覆盖，是否继续？')) {
    return
  }
  formData.spjCode = template
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
  if (!formData.solutionSignature.methodName.trim()) {
    toast.error('请填写 Solution 方法名')
    return false
  }
  for (const [idx, p] of formData.solutionSignature.params.entries()) {
    if (!p.type.trim() || !p.name.trim()) {
      toast.error(`请完善参数 ${idx + 1} 的类型与名称`)
      return false
    }
  }
  for (const [idx, item] of formData.judgeCase.entries()) {
    if (!item.input.trim() || !item.expectedOutput.trim()) {
      toast.error(`请完善测试用例 ${idx + 1}`)
      return false
    }
  }
  if (isSpjMode.value) {
    if (!formData.spjCode.trim()) {
      toast.error('特判模式下请填写特判代码')
      return false
    }
    if (!formData.spjLanguage) {
      toast.error('请选择特判语言')
      return false
    }
  }
  return true
}

const handleSubmit = async () => {
  if (!validateForm()) return

  submitting.value = true
  try {
    // 提交体不含 solutionSignature（前端临时签名结构，非后端字段）；codeTemplate 由签名生成
    const body = {
      title: formData.title,
      content: formData.content,
      answer: formData.answer,
      tags: Array.isArray(formData.tags) ? formData.tags : [],
      judgeCase: Array.isArray(formData.judgeCase) ? formData.judgeCase : [],
      judgeConfig: { ...formData.judgeConfig },
      // 非特判模式清空特判字段：PATCH 语义下 null 不覆盖旧值，只有空串能真正清掉
      spjCode: isSpjMode.value ? formData.spjCode : '',
      spjLanguage: isSpjMode.value ? formData.spjLanguage : '',
      codeTemplate: solutionCodeTemplate.value
    }

    let result
    if (props.mode === 'edit') {
      if (!props.questionId) throw new Error('编辑模式缺少题目ID')
      result = await updateQuestion({
        path: { id: props.questionId },
        body: body as unknown as QuestionUpdateRequest
      })
    } else {
      result = await createQuestion({ body: body as unknown as QuestionCreateRequest })
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
