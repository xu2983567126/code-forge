/**
 * sidebar-menu 共享工具。
 *
 * 由 UltiCode `@ulticode/sidebar-menu/src/utils.ts` 移植。
 * 差异：`cn` 直接复用本项目已有的 `@/lib/utils`（不再本地 vendored 一份）。
 */

export type SidebarItemActiveFn = (url?: string) => boolean

export interface SidebarUser {
  name: string
  email?: string
  avatar?: string
  role?: string
}

/**
 * 判断某个菜单 url 对当前路径是否为「激活」。
 *
 * @param currentPath 当前路由 path
 * @param url 菜单项 url
 * @param exactUrls 需要「精确相等」才算激活的 url 集合（例如首页 `/`，
 *                  否则 `/` 会前缀匹配到所有路径）
 */
export function isExactOrStartsWith(
  currentPath: string,
  url: string | undefined,
  exactUrls: string[] = [],
): boolean {
  if (!url) return false
  if (exactUrls.includes(url)) return currentPath === url
  return currentPath === url || currentPath.startsWith(url + '/')
}
