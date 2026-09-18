<script setup lang="ts">
/**
 * 侧边栏导航渲染器。
 *
 * 由 UltiCode `apps/console/src/features/sider/SidebarNav.vue` 移植：
 * - 去掉 vue-i18n，标题直接用中文字面量；
 * - `@ulticode/sidebar-menu` 改为本地 `@/components/sidebar-menu`；
 * - 新增 `adminOnly` 维度的可见性过滤（上游只有 `requiresAuth`）。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronRight } from 'lucide-vue-next'
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem as UiSidebarMenuItem,
  SidebarMenuSub,
  useSidebar,
} from '@/components/ui/sidebar'
import { CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Badge } from '@/components/ui/badge'
import { useUserStore } from '@/store/user'
import ACCESS_ENUM from '@/access/accessEnum'
import {
  EXACT_ACTIVE_URLS,
  type SidebarItem,
  type SidebarSection,
} from '@/layouts/sidebar.data'
import {
  SidebarGroupCollapsible,
  SidebarMenuItem as SharedSidebarMenuItem,
  SidebarMenuSubItem as SharedSidebarMenuSubItem,
  SidebarParentItem,
} from '@/components/sidebar-menu'

const props = defineProps<{
  sections: SidebarSection[]
}>()

const route = useRoute()
const userStore = useUserStore()
const { state } = useSidebar()

const isLoggedIn = computed(
  () =>
    !!userStore.loginUser.role &&
    userStore.loginUser.role !== ACCESS_ENUM.NOT_LOGIN,
)
const isAdmin = computed(() => userStore.loginUser.role === ACCESS_ENUM.ADMIN)

const visibleSections = computed(() => {
  const authed = isLoggedIn.value
  const admin = isAdmin.value
  return props.sections
    .filter(
      (section) =>
        (!section.requiresAuth || authed) && (!section.adminOnly || admin),
    )
    .map((section) => ({
      ...section,
      items: section.items.filter(
        (item) =>
          (!item.requiresAuth || authed) && (!item.adminOnly || admin),
      ),
    }))
    .filter((section) => section.items.length > 0)
})

/** 单个 url 是否命中当前路径（精确还是前缀由 EXACT_ACTIVE_URLS 决定）。 */
const matchesPath = (url: string): boolean => {
  if (EXACT_ACTIVE_URLS.includes(url)) return route.path === url
  return route.path === url || route.path.startsWith(url + '/')
}

const isItemActive = (item: SidebarItem): boolean => {
  // 有子项的父项：自身或任一后代命中即视为激活。
  if (item.children && item.children.length > 0) {
    return item.children.some((child) => isItemActive(child))
  }
  const urls = [item.url, ...(item.activeUrls ?? [])].filter(
    (u): u is string => !!u,
  )
  if (urls.length === 0) return false
  return urls.some(matchesPath)
}

const getItemIconColorClass = (item: SidebarItem) => {
  if (!item.url && !item.children) return ''
  return isItemActive(item)
    ? 'text-[var(--primary)]'
    : 'text-[var(--foreground-muted)] group-hover:text-[var(--foreground)]'
}
</script>

<template>
  <div class="uc-sidebar-nav">
    <SidebarGroup
      v-for="section in visibleSections"
      :key="section.name"
      class="uc-sidebar-section"
    >
      <!-- 可折叠分组 -->
      <SidebarGroupCollapsible
        v-if="section.collapsible"
        v-slot="{ open: isOpen }"
        :default-open="true"
      >
        <CollapsibleTrigger class="uc-sidebar-section-trigger">
          <span>{{ section.name }}</span>
          <ChevronRight
            :class="[
              'uc-sidebar-section-chevron ml-auto',
              isOpen ? 'rotate-90' : '',
            ]"
          />
        </CollapsibleTrigger>
        <CollapsibleContent>
          <SidebarMenuSub
            v-if="state !== 'collapsed'"
            class="uc-sidebar-sub-list"
          >
            <SharedSidebarMenuSubItem
              v-for="item in section.items"
              :key="item.title"
              :is-active="isItemActive(item)"
              :to="item.url || '#'"
              class="flex items-center gap-2"
            >
              <component
                :is="item.icon"
                v-if="item.icon"
                :class="[
                  'h-3.5 w-3.5 shrink-0 transition-colors',
                  getItemIconColorClass(item),
                ]"
              />
              <span class="truncate text-xs">{{ item.title }}</span>
              <Badge
                v-if="item.badge"
                :variant="item.badgeVariant || 'default'"
                class="uc-sidebar-badge ml-auto"
              >
                {{ item.badge }}
              </Badge>
            </SharedSidebarMenuSubItem>
          </SidebarMenuSub>
          <SidebarMenu v-else>
            <UiSidebarMenuItem v-for="item in section.items" :key="item.title">
              <SidebarMenuButton
                :tooltip="item.title"
                :is-active="isItemActive(item)"
                as-child
                class="uc-sidebar-item group"
              >
                <router-link :to="item.url || '#'">
                  <component
                    :is="item.icon"
                    v-if="item.icon"
                    :class="['transition-colors', getItemIconColorClass(item)]"
                  />
                  <span>{{ item.title }}</span>
                </router-link>
              </SidebarMenuButton>
            </UiSidebarMenuItem>
          </SidebarMenu>
        </CollapsibleContent>
      </SidebarGroupCollapsible>

      <!-- 普通分组 -->
      <template v-else>
        <!-- 分组标题：上游 console 未渲染非折叠分组的名称，导致多组菜单
             平铺后失去层次。这里用契约自带的 .uc-sidebar-group-label 补上。 -->
        <SidebarGroupLabel
          v-if="state !== 'collapsed'"
          class="uc-sidebar-group-label mb-1"
        >
          {{ section.name }}
        </SidebarGroupLabel>
        <div v-if="state !== 'collapsed'" class="uc-sidebar-section-items">
          <template v-for="item in section.items" :key="item.title">
            <!-- 带子项的父项：链接 + 可折叠子项 -->
            <SidebarParentItem
              v-if="item.children && item.children.length > 0"
              :title="item.title"
              :url="item.url"
              :icon="item.icon"
              :icon-class="getItemIconColorClass(item)"
              :active="isItemActive(item)"
              :default-open="isItemActive(item)"
            >
              <div class="uc-sidebar-child-list">
                <SharedSidebarMenuSubItem
                  v-for="child in item.children"
                  :key="child.title"
                  :is-active="isItemActive(child)"
                  :to="child.url || '#'"
                  class="flex items-center gap-2"
                >
                  <component
                    :is="child.icon"
                    v-if="child.icon"
                    :class="[
                      'h-3.5 w-3.5 shrink-0 transition-colors',
                      getItemIconColorClass(child),
                    ]"
                  />
                  <span class="truncate text-xs">{{ child.title }}</span>
                </SharedSidebarMenuSubItem>
              </div>
            </SidebarParentItem>
            <!-- 普通项 -->
            <SharedSidebarMenuItem
              v-else
              :is-active="isItemActive(item)"
              :to="item.url || '#'"
            >
              <component
                :is="item.icon"
                v-if="item.icon"
                :class="[
                  'h-4 w-4 shrink-0 transition-colors',
                  getItemIconColorClass(item),
                ]"
              />
              <span class="truncate">{{ item.title }}</span>
              <Badge
                v-if="item.badge"
                :variant="item.badgeVariant || 'default'"
                class="uc-sidebar-badge ml-auto"
              >
                {{ item.badge }}
              </Badge>
            </SharedSidebarMenuItem>
          </template>
        </div>
        <!-- 折叠态（icon 模式）：退回原生菜单按钮 -->
        <SidebarMenu v-else class="mt-2">
          <UiSidebarMenuItem v-for="item in section.items" :key="item.title">
            <SidebarMenuButton
              :tooltip="item.title"
              :is-active="isItemActive(item)"
              as-child
              class="uc-sidebar-item group"
            >
              <router-link :to="item.url || '#'">
                <component
                  :is="item.icon"
                  v-if="item.icon"
                  :class="['transition-colors', getItemIconColorClass(item)]"
                />
                <span>{{ item.title }}</span>
              </router-link>
            </SidebarMenuButton>
          </UiSidebarMenuItem>
        </SidebarMenu>
      </template>
    </SidebarGroup>
  </div>
</template>
