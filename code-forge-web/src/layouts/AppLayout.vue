<script setup lang="ts">
/**
 * 应用骨架（L0）。
 *
 * 对应 UltiCode `apps/console/src/features/sider/AppLayout.vue`，本地化差异：
 * - 去掉 vue-i18n、SearchBar（本项目无搜索后端）、NavigationMenu 组件，
 *   顶栏一级导航改用轻量 RouterLink（语义等价，少一层 reka 依赖）；
 * - 顶栏右侧为「主题切换器 + 登录态快捷区」（未登录显示登录/注册按钮，
 *   已登录显示用户名；完整用户菜单在侧边栏 Footer 的 NavUser）。
 *
 * 登录态回拉**不在这里做** —— 统一由路由守卫（src/access/index.ts）负责，
 * 避免同一份登录态被多处重复请求。
 * 页面内只需 `uc-page-stack`：容器（uc-page-main / uc-page-container）已在此提供。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Button } from '@/components/ui/button'
import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { useUserStore } from '@/store/user'
import ACCESS_ENUM from '@/access/accessEnum'
import AppSidebar from './components/AppSidebar.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

interface NavItem {
  label: string
  path: string
  exact?: boolean
  requiresAuth?: boolean
}

const route = useRoute()
const userStore = useUserStore()

const isLoggedIn = computed(
  () =>
    !!userStore.loginUser.role &&
    userStore.loginUser.role !== ACCESS_ENUM.NOT_LOGIN,
)

const navItems: NavItem[] = [
  { label: '题目', path: '/', exact: true },
  { label: '题库', path: '/banks' },
  { label: '提交', path: '/submissions', requiresAuth: true },
]

const visibleNav = computed(() =>
  navItems.filter((item) => !item.requiresAuth || isLoggedIn.value),
)

const isActiveNav = (item: NavItem) =>
  item.exact
    ? route.path === item.path
    : route.path === item.path || route.path.startsWith(item.path + '/')
</script>

<template>
  <SidebarProvider class="w-full" :style="{ '--sidebar-width': '220px' }">
    <AppSidebar />
    <SidebarInset class="min-w-0">
      <header
        class="sticky top-0 z-30 grid h-14 shrink-0 grid-cols-[1fr_auto_1fr] items-center gap-[var(--uc-layout-control-gap)] border-b border-border-subtle bg-surface-elevated px-[var(--uc-layout-panel-padding-inline)]"
      >
        <SidebarTrigger
          class="-ml-1 justify-self-start"
          title="折叠/展开侧边栏"
        />
        <nav
          class="hidden h-full items-center justify-self-center gap-1 md:flex"
        >
          <RouterLink
            v-for="item in visibleNav"
            :key="item.path"
            :to="item.path"
            class="flex h-9 items-center rounded-md px-3 text-sm font-medium text-foreground-muted transition-colors hover:bg-surface-highlight hover:text-foreground"
            :class="{
              'bg-surface-highlight font-semibold text-foreground':
                isActiveNav(item),
            }"
          >
            {{ item.label }}
          </RouterLink>
        </nav>
        <div class="flex items-center justify-self-end gap-2">
          <ThemeSwitcher />
          <template v-if="isLoggedIn">
            <span class="text-sm text-foreground-muted">
              {{ userStore.loginUser.username }}
            </span>
          </template>
          <template v-else>
            <RouterLink to="/user/login">
              <Button size="sm">登录</Button>
            </RouterLink>
            <RouterLink to="/user/register">
              <Button variant="outline" size="sm">注册</Button>
            </RouterLink>
          </template>
        </div>
      </header>
      <main class="uc-page-main">
        <div class="uc-page-container">
          <router-view />
        </div>
      </main>
    </SidebarInset>
  </SidebarProvider>
</template>
