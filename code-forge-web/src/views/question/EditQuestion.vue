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
import { getQuestionVoById, type QuestionVo } from '@generated'
import { toast } from 'vue-sonner'

const route = useRoute()
const router = useRouter()

const questionId = ref<number>(0)
const initialData = ref<Partial<QuestionVo>>({})
const loading = ref(true)

onMounted(async () => {
  const idParam = route.params.id
  if (!idParam) {
    toast.error('题目ID不存在')
    router.back()
    return
  }
  questionId.value = parseInt(idParam as string)

  try {
    const res = await getQuestionVoById({ path: { id: questionId.value } })
    if (res.data?.code === 0 && res.data.data) {
      initialData.value = res.data.data
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
