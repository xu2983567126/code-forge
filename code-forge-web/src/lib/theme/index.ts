/**
 * 主题核心（L5）：三态模式 + 把结果写到 DOM。
 *
 * 视觉侧无需本模块参与 —— `style.css` 已提供 `@custom-variant dark (&:is(.dark *))`
 * 与 `.dark { ... }` 的整套语义 token 覆盖。本模块只负责一件事：决定 `<html>`
 * 上要不要挂 `.dark`，以及同步 `color-scheme`（让滚动条、原生表单控件跟随）。
 *
 * ⚠️ `index.html` 的 `<head>` 里有一份**等价判据的内联脚本**，用于在首屏样式生效前
 * 就把 class 写好（否则暗色用户刷新会闪一次白）。两处判据必须同步修改。
 */

/** 用户可选的主题模式。`system` 表示跟随操作系统。 */
export type ThemeMode = 'light' | 'dark' | 'system'

/** 真正落到 DOM 上的主题（`system` 已被解析掉）。 */
export type ResolvedTheme = 'light' | 'dark'

/** 存储键。与 `index.html` 内联脚本共用，改名需两处同步。 */
export const THEME_STORAGE_KEY = 'code-forge-theme'

const DARK_SCHEME_QUERY = '(prefers-color-scheme: dark)'

/** 校验任意输入是否为合法模式，用于防御 localStorage 里的脏值。 */
export function isThemeMode(value: unknown): value is ThemeMode {
  return value === 'light' || value === 'dark' || value === 'system'
}

/**
 * 读存储的模式。读不到或值非法时回落 `system`。
 *
 * 隐私模式或禁用 storage 时 `localStorage` 访问会抛异常，此时同样回落
 * `system` —— 主题停留在本次会话，不阻断渲染。
 */
export function readStoredTheme(): ThemeMode {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    return isThemeMode(raw) ? raw : 'system'
  } catch {
    return 'system'
  }
}

/** 系统是否偏好暗色。非浏览器环境（如单测）返回 false。 */
export function systemPrefersDark(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia(DARK_SCHEME_QUERY).matches
}

/** 把三态模式解析为实际生效的两态主题。 */
export function resolveTheme(mode: ThemeMode): ResolvedTheme {
  if (mode === 'system') return systemPrefersDark() ? 'dark' : 'light'
  return mode
}

/**
 * 将主题写入 `<html>`。
 *
 * `.dark` 类供 tailwind 的 dark 变体消费；`color-scheme` 让浏览器把滚动条、
 * 输入框光标等**原生控件**也切到对应明暗，避免暗色页面里出现亮色滚动条。
 */
export function applyThemeToDOM(theme: ResolvedTheme): void {
  const root = document.documentElement
  root.classList.toggle('dark', theme === 'dark')
  root.style.colorScheme = theme
}

/** 持久化模式。storage 不可用时静默失败，仅本次会话生效。 */
export function persistTheme(mode: ThemeMode): void {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, mode)
  } catch {
    /* storage 不可用：忽略 */
  }
}
