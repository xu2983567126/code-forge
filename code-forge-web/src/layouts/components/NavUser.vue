<script setup lang="ts">
/**
 * 侧边栏底部用户区。
 *
 * 由 UltiCode `apps/console/src/features/sider/NavUser.vue` 移植：
 * - 去掉 i18n / 通知中心 / 头像归一化（本项目用户信息只有 username + role）；
 * - `IconDotsVertical`（@tabler/icons-vue）→ lucide 的 `EllipsisVertical`，不新增依赖；
 * - 登出改为调本项目 SDK 的 `userLogout()` + Pinia store。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  EllipsisVertical,
  History,
  LayoutDashboard,
  LogIn,
  LogOut,
  User,
  UserPlus,
  Users,
} from 'lucide-vue-next'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'
import { useUserStore } from '@/store/user'
import ACCESS_ENUM from '@/access/accessEnum'
import { userLogout } from '@generated'
import { toast } from 'vue-sonner'

const props = defineProps<{
  username?: string
  role?: string
  isAuthenticated: boolean
}>()

const { isMobile } = useSidebar()
const router = useRouter()
const userStore = useUserStore()

const displayName = computed(() => props.username || '未登录')
const isAdmin = computed(() => props.role === ACCESS_ENUM.ADMIN)
/** 头像占位：取用户名前 2 个字符。 */
const initial = computed(() => (props.username || '游').slice(0, 2).toUpperCase())

const handleLogout = async () => {
  try {
    await userLogout()
  } catch (e) {
    console.error('登出失败', e)
  } finally {
    userStore.logout()
    toast.success('已退出登录')
    router.push('/user/login')
  }
}
</script>

<template>
  <SidebarMenu>
    <SidebarMenuItem>
      <!-- 未登录 -->
      <template v-if="!isAuthenticated">
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <SidebarMenuButton
              size="lg"
              class="uc-sidebar-user-trigger data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <Avatar class="uc-sidebar-avatar h-8 w-8 rounded-md">
                <AvatarFallback class="uc-sidebar-avatar-fallback rounded-md">
                  <User class="size-4" />
                </AvatarFallback>
              </Avatar>
              <div
                class="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden"
              >
                <span class="truncate font-medium">未登录</span>
                <span class="truncate text-xs text-muted-foreground">
                  登录后可提交代码
                </span>
              </div>
              <EllipsisVertical class="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            class="uc-sidebar-dropdown min-w-56"
            :side="isMobile ? 'bottom' : 'right'"
            align="end"
            :side-offset="4"
          >
            <DropdownMenuGroup>
              <RouterLink to="/user/login">
                <DropdownMenuItem class="cursor-pointer">
                  <LogIn class="mr-2 h-4 w-4" />
                  登录
                </DropdownMenuItem>
              </RouterLink>
              <RouterLink to="/user/register">
                <DropdownMenuItem class="cursor-pointer">
                  <UserPlus class="mr-2 h-4 w-4" />
                  注册
                </DropdownMenuItem>
              </RouterLink>
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </template>

      <!-- 已登录 -->
      <DropdownMenu v-else>
        <DropdownMenuTrigger as-child>
          <SidebarMenuButton
            size="lg"
            class="uc-sidebar-user-trigger data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
          >
            <Avatar class="uc-sidebar-avatar h-8 w-8 rounded-md">
              <AvatarFallback class="uc-sidebar-avatar-fallback rounded-md">
                {{ initial }}
              </AvatarFallback>
            </Avatar>
            <div
              class="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden"
            >
              <span class="truncate font-medium">{{ displayName }}</span>
              <span class="truncate text-xs text-muted-foreground">
                {{ isAdmin ? '管理员' : '普通用户' }}
              </span>
            </div>
            <EllipsisVertical class="ml-auto size-4" />
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          class="uc-sidebar-dropdown min-w-56"
          :side="isMobile ? 'bottom' : 'right'"
          align="end"
          :side-offset="4"
        >
          <DropdownMenuLabel class="p-0 font-normal">
            <div class="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
              <Avatar class="uc-sidebar-avatar h-8 w-8 rounded-md">
                <AvatarFallback class="uc-sidebar-avatar-fallback rounded-md">
                  {{ initial }}
                </AvatarFallback>
              </Avatar>
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-medium">{{ displayName }}</span>
                <span class="truncate text-xs text-muted-foreground">
                  {{ isAdmin ? '管理员' : '普通用户' }}
                </span>
              </div>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <RouterLink to="/dashboard/me">
              <DropdownMenuItem class="cursor-pointer">
                <User class="mr-2 h-4 w-4" />
                我的主页
              </DropdownMenuItem>
            </RouterLink>
            <RouterLink to="/submissions">
              <DropdownMenuItem class="cursor-pointer">
                <History class="mr-2 h-4 w-4" />
                提交记录
              </DropdownMenuItem>
            </RouterLink>
          </DropdownMenuGroup>
          <template v-if="isAdmin">
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <RouterLink to="/admin/users">
                <DropdownMenuItem class="cursor-pointer">
                  <Users class="mr-2 h-4 w-4" />
                  用户管理
                </DropdownMenuItem>
              </RouterLink>
              <RouterLink to="/admin/dashboard">
                <DropdownMenuItem class="cursor-pointer">
                  <LayoutDashboard class="mr-2 h-4 w-4" />
                  平台仪表板
                </DropdownMenuItem>
              </RouterLink>
            </DropdownMenuGroup>
          </template>
          <DropdownMenuSeparator />
          <DropdownMenuItem class="cursor-pointer text-destructive" @click="handleLogout">
            <LogOut class="mr-2 h-4 w-4" />
            退出登录
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
