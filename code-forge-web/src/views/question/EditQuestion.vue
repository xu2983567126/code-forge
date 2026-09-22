<template>
  <div v-if="loading" class="p-12 text-center text-muted-foreground">加载中...</div>
  <QuestionForm
    v-else
    mode="edit"
    :question-id="questionId"
    :initial-data="initialData"
    @submit-success="handleSuccess"
    @submit-error="handleError"
  />
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QuestionForm from './components/QuestionForm.vue'
import {
  getQuestionVoById,
  type JudgeCase,
  type QuestionCreateRequest,
  type QuestionVo
} from '@generated'
import { DEFAULT_LANGUAGE } from '@/constants/language'
import { toast } from 'vue-sonner'

// 编辑接口对作者/管理员返回的是 QuestionAdminVO（多出 answer / judgeCase / spjCode 等字段），
// 而 SDK 只声明了基类 QuestionVo，这里按运行时实际字段做一次收窄。
type QuestionAdminFields = Partial<QuestionVo> & {
  judgeCase?: JudgeCase[]
  spjCode?: string
  spjLanguage?: string
}

const route = useRoute()
const router = useRouter()

const questionId = ref<string>('')
const initialData = ref<Partial<QuestionCreateRequest>>({})
const loading = ref(true)

onMounted(async () => {
  const idParam = route.params.id
  if (!idParam) {
    toast.error('题目ID不存在')
    router.back()
    return
  }
  questionId.value = idParam as string

  try {
    const res = await getQuestionVoById({ path: { id: questionId.value } })
    if (res.data?.code === 0 && res.data.data) {
      const data = res.data.data as QuestionAdminFields
      initialData.value = {
        ...data,
        // 非作者只能拿到 examples（公开样例），此时用它兜底，避免编辑页用例区空白
        judgeCase: data.judgeCase?.length ? data.judgeCase : (data.examples ?? []),
        spjCode: data.spjCode ?? '',
        spjLanguage: data.spjLanguage ?? DEFAULT_LANGUAGE
      }
    } else {
      toast.error(res.data?.message || '加载题目数据失败')
      router.back()
    }
  } catch (error) {
    console.error('加载失败', error)
    toast.error('网络错误，请稍后重试')
    router.back()
  } finally {
    loading.value = false
  }
})

const handleSuccess = () => {
  toast.success('题目更新成功')
  router.push('/questions/manage')
}

const handleError = () => {
  // 可选：停留在当前页面，让用户修改后重试
}
</script>
