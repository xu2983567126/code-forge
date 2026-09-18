<template>
  <QuestionForm mode="create" @submit-success="handleSuccess" />
</template>

<script setup lang="ts">
import QuestionForm from './components/QuestionForm.vue'
import { toast } from 'vue-sonner'

const handleSuccess = () => {
  toast.success('题目创建成功，可继续创建下一个')
}
</script>
