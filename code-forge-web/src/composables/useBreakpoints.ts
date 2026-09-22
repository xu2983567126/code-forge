import { computed, onMounted, onUnmounted, ref, readonly } from 'vue'

/**
 * 响应式断点（供需要在 JS 层切换布局的场景用）。
 *
 * 与 Tailwind 的断点数值保持一致（sm 640 / md 768 / lg 1024 / xl 1280 / 2xl 1536），
 * 这样 CSS 与 JS 不会出现两套临界值。
 *
 * 为什么需要它：做题页在窄屏要换成**另一套布局**（tab 切换面板），而不是靠 CSS 把
 * 分栏压成一列 —— 后者需要 `!important` 覆盖组件库写在 inline style 上的
 * `flex-direction`，既脆弱又无法改变"同时渲染全部面板"的事实。
 */
export const BREAKPOINTS = {
  xs: 0,
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  '2xl': 1536,
} as const

/** 单测/SSR 下 window 不可用时的兜底宽度（按桌面处理，避免首帧闪一下移动端布局）。 */
const FALLBACK_WIDTH = 1024

export function useBreakpoints() {
  const windowWidth = ref<number>(
    typeof window !== 'undefined' ? window.innerWidth : FALLBACK_WIDTH
  )

  function sync() {
    windowWidth.value = window.innerWidth
  }

  onMounted(() => window.addEventListener('resize', sync))
  onUnmounted(() => window.removeEventListener('resize', sync))

  /** 小于 md：走移动端专项布局。 */
  const isMobile = computed(() => windowWidth.value < BREAKPOINTS.md)
  const isTablet = computed(
    () => windowWidth.value >= BREAKPOINTS.md && windowWidth.value < BREAKPOINTS.lg
  )
  const isDesktop = computed(() => windowWidth.value >= BREAKPOINTS.lg)

  return {
    windowWidth: readonly(windowWidth),
    isMobile,
    isTablet,
    isDesktop,
  }
}
