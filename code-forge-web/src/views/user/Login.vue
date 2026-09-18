<template>
  <div
    class="flex min-h-[calc(100vh-4rem)] items-center justify-center bg-muted/40 p-4"
  >
    <Card class="w-full max-w-md shadow-lg">
      <CardHeader class="space-y-1 text-center">
        <CardTitle class="text-2xl font-bold">用户登录</CardTitle>
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
              placeholder="请输入密码"
              autocomplete="current-password"
            />
          </FormField>
          <Button type="submit" class="w-full" :disabled="loading">
            {{ loading ? '登录中…' : '登录' }}
          </Button>
        </form>
        <div class="mt-4 text-center text-sm text-muted-foreground">
          还没有账号？
          <RouterLink to="/user/register" class="text-primary underline-offset-4 hover:underline">
            立即注册
          </RouterLink>
        </div>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userLogin, type UserLoginRequest } from '@generated'
import { useUserStore } from '@/store/user'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import FormField from '@/components/ui-custom/FormField.vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  account: '',
  password: ''
} as UserLoginRequest)

const handleSubmit = async () => {
  if (!form.account?.trim() || !form.password?.trim()) {
    toast.error('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const res = await userLogin({ body: form })
    if (res?.data?.code === 0) {
      toast.success('登录成功')
      await userStore.getLoginUser()
      const redirect = router.currentRoute.value.query.redirect as string
      router.push(redirect || '/')
    } else {
      toast.error('登录失败：' + (res?.data?.message || '未知错误'))
    }
  } catch (error) {
    console.error('登录请求失败:', error)
    toast.error('登录请求失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}
</script>
