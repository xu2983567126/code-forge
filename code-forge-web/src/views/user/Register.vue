<template>
  <div
    class="flex min-h-[calc(100vh-4rem)] items-center justify-center bg-muted/40 p-4"
  >
    <Card class="w-full max-w-md shadow-lg">
      <CardHeader class="space-y-1 text-center">
        <CardTitle class="text-2xl font-bold">用户注册</CardTitle>
        <CardDescription>欢迎使用在线判题系统</CardDescription>
      </CardHeader>
      <CardContent>
        <form class="space-y-4" @submit.prevent="handleSubmit">
          <FormField label="账号" required html-for="account">
            <Input
              id="account"
              v-model="form.account"
              placeholder="请输入账号"
              autocomplete="username"
            />
          </FormField>
          <FormField label="密码" required html-for="password">
            <Input
              id="password"
              type="password"
              v-model="form.password"
              placeholder="请输入密码（不少于 8 位）"
              autocomplete="new-password"
            />
          </FormField>
          <FormField label="确认密码" required html-for="checkPassword">
            <Input
              id="checkPassword"
              type="password"
              v-model="form.checkPassword"
              placeholder="请再次输入密码"
              autocomplete="new-password"
            />
          </FormField>
          <Button type="submit" class="w-full" :disabled="loading">
            {{ loading ? '注册中…' : '注册' }}
          </Button>
        </form>
        <div class="mt-4 text-center text-sm text-muted-foreground">
          已有账号？
          <RouterLink to="/user/login" class="text-primary underline-offset-4 hover:underline">
            去登录
          </RouterLink>
        </div>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userRegister, type UserRegisterRequest } from '@generated'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import FormField from '@/components/ui-custom/FormField.vue'

const router = useRouter()
const loading = ref(false)

const form = reactive({
  account: '',
  password: '',
  checkPassword: ''
} as UserRegisterRequest)

const handleSubmit = async () => {
  if (!form.account?.trim() || !form.password?.trim() || !form.checkPassword?.trim()) {
    toast.error('请填写完整的注册信息')
    return
  }
  if (form.password !== form.checkPassword) {
    toast.error('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    const res = await userRegister({ body: form })
    if (res?.data?.code === 0) {
      toast.success('注册成功，请登录')
      router.push('/user/login')
    } else {
      toast.error('注册失败：' + (res?.data?.message || '未知错误'))
    }
  } catch (error) {
    console.error('注册请求失败:', error)
    toast.error('注册请求失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}
</script>
