<template>
  <div class="uc-page-stack min-h-[60vh] items-center justify-center text-center">
    <div class="rounded-full bg-destructive/10 p-4 text-destructive">
      <LockKeyhole class="size-8" />
    </div>
    <h1 class="text-2xl font-bold">无权限访问</h1>
    <p class="max-w-md text-muted-foreground">
      当前账号没有访问该页面的权限，请先登录或切换账号。
    </p>
    <div class="flex gap-3">
      <RouterLink to="/user/login">
        <Button>去登录</Button>
      </RouterLink>
      <RouterLink to="/questions">
        <Button variant="outline">返回题库</Button>
      </RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { LockKeyhole } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
</script>
