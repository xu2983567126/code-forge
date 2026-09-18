/**
 * 侧边栏共享视觉契约组件。
 *
 * 由 UltiCode `@ulticode/sidebar-menu` 移植（仅取已被实际消费的 4 个组件）。
 *
 * 激活视觉来自 `.uc-sidebar-*` CSS + `[data-active]` 属性，契约定义在
 * `src/styles/sidebar-menu.css`，由 `src/style.css` 在 tailwind 之后统一引入。
 *
 * 与本项目 `@/components/ui/sidebar` 的 `SidebarMenuItem` 等同名，因此
 * 消费处需重命名导入以避免冲突：
 *   import { SidebarMenuItem as SharedSidebarMenuItem } from '@/components/sidebar-menu'
 *
 * 未移植（上游标注 @beta、两个 app 均未接线）：SidebarIconButton、SidebarNavUser。
 */
export { default as SidebarMenuItem } from './SidebarMenuItem.vue'
export { default as SidebarMenuSubItem } from './SidebarMenuSubItem.vue'
export { default as SidebarGroupCollapsible } from './SidebarGroupCollapsible.vue'
export { default as SidebarParentItem } from './SidebarParentItem.vue'

export { isExactOrStartsWith } from './utils'
export type { SidebarItemActiveFn, SidebarUser } from './utils'
