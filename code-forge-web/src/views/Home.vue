<template>
  <div class="uc-page-stack">
    <Card class="mx-auto w-full max-w-3xl text-center">
      <CardHeader>
        <CardTitle class="text-3xl font-bold">欢迎来到 CodeForge</CardTitle>
        <CardDescription class="text-base">
          一个基于 Spring Cloud 微服务 + Vue3 的在线判题系统
        </CardDescription>
      </CardHeader>
      <CardContent class="flex flex-col items-center gap-6">
        <img src="@/assets/logo.png" alt="logo" class="h-20 w-20" />
        <div class="flex flex-wrap justify-center gap-3">
          <RouterLink to="/questions">
            <Button>浏览题目</Button>
          </RouterLink>
          <RouterLink to="/user/login">
            <Button variant="outline">登录</Button>
          </RouterLink>
        </div>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
</script>
