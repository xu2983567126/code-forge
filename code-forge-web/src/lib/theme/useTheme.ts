/**
 * 主题 composable（L5）。
 *
 * 状态放在**模块级**而非 `ref` 内部：主题是全局单例，多个组件各自持有一份
 * 会立刻不同步（顶栏切换器改了，别处图标不跟着变）。
 *
 * 初始化是懒执行的：首次 `useTheme()` 调用时才读存储、写 DOM。这样单测里
 * import 本模块不会触碰 `localStorage` / `matchMedia`。
 */
import { computed, readonly, ref, watch } from 'vue'
import {
  applyThemeToDOM,
  persistTheme,
  readStoredTheme,
  resolveTheme,
  systemPrefersDark,
  type ThemeMode
} from './index'

const mode = ref<ThemeMode>(readStoredTheme())
const systemDark = ref(systemPrefersDark())

let initialized = false

/** 把当前模式解析成实际主题并写到 `<html>`。 */
function syncDOM() {
  applyThemeToDOM(resolveTheme(mode.value))
}

function initialize() {
  if (initialized) return
  initialized = true

  syncDOM()

  // 只有 system 模式关心系统变化；两态模式下 systemDark 变了也不会影响解析结果，
  // 因此无需按模式挂/卸监听。
  if (typeof window !== 'undefined' && window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (event) => {
      systemDark.value = event.matches
    })
  }

  watch([mode, systemDark], syncDOM)
}

/** 三态循环顺序：浅色 → 深色 → 跟随系统。 */
const CYCLE_ORDER: ThemeMode[] = ['light', 'dark', 'system']

export function useTheme() {
  initialize()

  /** 实际生效的两态主题（`system` 会被解析）。 */
  const resolved = computed(() => resolveTheme(mode.value))

  /** 设置模式并持久化。 */
  function setMode(next: ThemeMode) {
    mode.value = next
    persistTheme(next)
  }

  /** 按 `light → dark → system` 循环，供单按钮切换使用。 */
  function cycleMode() {
    const next = CYCLE_ORDER[(CYCLE_ORDER.indexOf(mode.value) + 1) % CYCLE_ORDER.length]
    setMode(next)
  }

  return {
    mode: readonly(mode),
    resolved,
    setMode,
    cycleMode
  }
}
