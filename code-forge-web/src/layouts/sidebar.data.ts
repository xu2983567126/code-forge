/**
 * 侧边栏菜单数据。
 *
 * 由 UltiCode `apps/console/src/features/sider/sidebar.data.ts` 移植并本地化：
 * - 去掉 i18n key，直接使用中文字面量；
 * - 菜单按本项目实际路由重排为 4 组；
 * - 新增 `adminOnly` 维度（上游只有 `requiresAuth`）。
 *
 * ⚠️ 菜单是**白名单显式声明**，刻意**不**从 `router/routes.ts` 反向推导：
 *    「遍历 routes + `hideInMenu` 反向标注」的写法要求任何新增内部路由都记得补标注，
 *    漏一次就会把不该出现的页面暴露到菜单里。
 */
import type { Component } from 'vue'
import {
  BookOpen,
  PlusCircle,
  Settings2,
  Library,
  User,
  History,
  Users,
  LayoutDashboard,
} from 'lucide-vue-next'

export interface SidebarItem {
  title: string
  url?: string
  /**
   * 额外视为「激活」的路径。
   * 用于多个路由指向同一页面的情况，例如 `/` 与 `/questions` 都渲染题目列表，
   * 用户在 `/questions` 时应让「浏览题目」保持高亮。
   */
  activeUrls?: string[]
  icon?: Component
  /** 行末徽标（数量/文本）。 */
  badge?: string
  badgeVariant?: 'default' | 'secondary' | 'destructive' | 'outline'
  children?: SidebarItem[]
  /** 需要登录可见。 */
  requiresAuth?: boolean
  /** 仅管理员可见。 */
  adminOnly?: boolean
}

export interface SidebarSection {
  name: string
  items: SidebarItem[]
  collapsible?: boolean
  requiresAuth?: boolean
  adminOnly?: boolean
}

/**
 * 需要「精确相等」才算激活的 url。
 * - `/` 若不精确匹配，会前缀命中所有路径，首页菜单永远高亮；
 * - `/questions` 同理：前缀匹配会命中 `/questions/create`、`/questions/manage`
 *   这些独立菜单项，造成两项同时高亮。
 * （`/banks` 有意不列入：`/banks/{id}` 详情页应让「题库专题」保持高亮。）
 */
export const EXACT_ACTIVE_URLS: string[] = ['/', '/questions']

export const appSidebarData: SidebarSection[] = [
  {
    name: '题目',
    items: [
      {
        title: '浏览题目',
        url: '/',
        icon: BookOpen,
        // `/` 与 `/questions` 渲染同一页面，两条路径都应保持高亮。
        activeUrls: ['/questions'],
      },
      {
        title: '创建题目',
        url: '/questions/create',
        icon: PlusCircle,
        requiresAuth: true,
      },
      {
        title: '管理题目',
        url: '/questions/manage',
        icon: Settings2,
        adminOnly: true,
      },
    ],
  },
  {
    name: '题库',
    items: [{ title: '题库专题', url: '/banks', icon: Library }],
  },
  {
    name: '我的',
    requiresAuth: true,
    items: [
      { title: '我的主页', url: '/dashboard/me', icon: User },
      { title: '提交记录', url: '/submissions', icon: History },
    ],
  },
  {
    name: '管理',
    adminOnly: true,
    items: [
      { title: '用户管理', url: '/admin/users', icon: Users },
      { title: '平台仪表板', url: '/admin/dashboard', icon: LayoutDashboard },
    ],
  },
]
