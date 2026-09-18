<template>
  <router-view />
  <Toaster richColors position="top-center" />
</template>

<script setup lang="ts">
import { Toaster } from 'vue-sonner'

/**
 * 应用根组件。
 *
 * 布局由路由决定（见 `router/routes.ts`）：
 * - `/user/*` → UserLayout（居中卡片，无侧边栏）
 * - 其余       → AppLayout（侧边栏 + 顶栏 + 页面容器）
 *
 * 本组件只保留路由出口。
 *
 * 登录态回拉统一由路由守卫负责（`src/access/index.ts`），此处不重复请求。
 */
console.log('hello 欢迎来到我的oj系统')
</script>

<style>
#app {
  min-height: 100vh;
}
</style>
