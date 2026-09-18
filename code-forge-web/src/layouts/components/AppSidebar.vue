<script setup lang="ts">
/**
 * 应用侧边栏。
 *
 * 由 UltiCode `apps/console/src/features/sider/AppSidebar.vue` 移植：
 * - 上游的「按路由上下文切换菜单数据」在本项目不需要（只有题库一个板块），
 *   直接使用单一 `appSidebarData`，因此不再依赖 useRoute 做菜单分发；
 * - 顶部 Header 从上游的 NavUser 改为品牌区（logo + 站名），用户区下移到 Footer。
 */
import { computed } from 'vue'
import type { SidebarProps } from '@/components/ui/sidebar'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar'
import { useUserStore } from '@/store/user'
import ACCESS_ENUM from '@/access/accessEnum'
import { appSidebarData } from '@/layouts/sidebar.data'
import NavUser from './NavUser.vue'
import SidebarNav from './SidebarNav.vue'

const props = withDefaults(defineProps<SidebarProps>(), {
  collapsible: 'icon',
})

const userStore = useUserStore()

const isLoggedIn = computed(
  () =>
    !!userStore.loginUser.role &&
    userStore.loginUser.role !== ACCESS_ENUM.NOT_LOGIN,
)
</script>

<template>
  <Sidebar v-bind="props" class="uc-sidebar-shell">
    <SidebarHeader class="uc-sidebar-header">
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton
            size="lg"
            as-child
            class="uc-sidebar-user-trigger"
            tooltip="CodeForge"
          >
            <RouterLink to="/">
              <img
                src="@/assets/logo.png"
                alt="CodeForge"
                class="uc-sidebar-avatar size-8 shrink-0 rounded-md object-contain"
              />
              <div
                class="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden"
              >
                <span class="truncate font-semibold">CodeForge</span>
                <span class="truncate text-xs text-muted-foreground">
                  在线判题系统
                </span>
              </div>
            </RouterLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>

    <SidebarContent class="uc-sidebar-content">
      <SidebarNav :sections="appSidebarData" />
    </SidebarContent>

    <SidebarFooter>
      <NavUser
        :username="userStore.loginUser.username"
        :role="userStore.loginUser.role"
        :is-authenticated="isLoggedIn"
      />
    </SidebarFooter>

    <SidebarRail />
  </Sidebar>
</template>
