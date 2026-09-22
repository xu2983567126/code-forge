// 图表角色 → 具体色值的解析层（移植并本地化自 UltiCode packages/design-system/src/chartPalette.ts）。
//
// 为什么需要它：Unovis 这类渲染器多数时候能直接吃 `var(--chart-series-N)`（SVG 的
// fill 由浏览器解析），但需要**具体色值**的场合必须走这里 —— 自定义绘制、canvas
// 导出、按占比调透明度、颜色插值等。
//
// 角色与 src/style.css 的 --chart-* 令牌一一对应，fallback 取 SOLARIZED_PALETTE 里
// 语义最接近的 canonical 值（无 DOM 时用），保证渲染器画不出调色板外的野色。

import { readonly, ref, type Ref } from "vue";
import { readCssColor, SOLARIZED_PALETTE } from "./palette";

export interface ChartPalette {
  /** 分类系列色，顺序即 --chart-series-1..8，可直接作为 Unovis 的 colors 传入。 */
  series: readonly string[];
  statusSolved: string;
  statusAttempted: string;
  statusTodo: string;
  difficultyEasy: string;
  difficultyMedium: string;
  difficultyHard: string;
  /** 网格线（--chart-grid-color，实际值是低透明度的 border-subtle）。 */
  grid: string;
  /** 坐标轴文字/刻度。 */
  axis: string;
  /** 绘图区背景。 */
  background: string;
  /** 悬浮/高亮面板底色。 */
  surface: string;
  /** 主文字（标注、标题）。 */
  foreground: string;
  /** 次要文字。 */
  muted: string;
  tooltipBackground: string;
  tooltipBorder: string;
}

/**
 * 在当前主题下解析全部图表角色。
 *
 * 每个角色的 fallback 都取自 canonical 调色板，语义与 style.css 的令牌一致；
 * 当 --chart-series-* 指向 color-mix 派生值时（如 2/3/6/7/8），无 DOM 场景会
 * 拿到近似原始色而不是派生色 —— 这是刻意的降级，够测试与导出使用。
 */
export function resolveChartPalette(): ChartPalette {
  return {
    series: [
      readCssColor("--chart-series-1", SOLARIZED_PALETTE.blue),
      readCssColor("--chart-series-2", SOLARIZED_PALETTE.cyan),
      readCssColor("--chart-series-3", SOLARIZED_PALETTE.green),
      readCssColor("--chart-series-4", SOLARIZED_PALETTE.magenta),
      readCssColor("--chart-series-5", SOLARIZED_PALETTE.orange),
      readCssColor("--chart-series-6", SOLARIZED_PALETTE.violet),
      readCssColor("--chart-series-7", SOLARIZED_PALETTE.yellow),
      readCssColor("--chart-series-8", SOLARIZED_PALETTE.red),
    ],
    statusSolved: readCssColor("--chart-status-solved", SOLARIZED_PALETTE.green),
    statusAttempted: readCssColor("--chart-status-attempted", SOLARIZED_PALETTE.yellow),
    statusTodo: readCssColor("--chart-status-todo", SOLARIZED_PALETTE.cyan),
    difficultyEasy: readCssColor("--chart-difficulty-easy", SOLARIZED_PALETTE.green),
    difficultyMedium: readCssColor("--chart-difficulty-medium", SOLARIZED_PALETTE.yellow),
    difficultyHard: readCssColor("--chart-difficulty-hard", SOLARIZED_PALETTE.red),
    grid: readCssColor("--chart-grid-color", SOLARIZED_PALETTE.base1),
    axis: readCssColor("--foreground", SOLARIZED_PALETTE.base01),
    background: readCssColor("--chart-background", SOLARIZED_PALETTE.base3),
    surface: readCssColor("--surface-highlight", SOLARIZED_PALETTE.base2),
    foreground: readCssColor("--foreground-strong", SOLARIZED_PALETTE.base03),
    muted: readCssColor("--foreground-muted", SOLARIZED_PALETTE.base01),
    tooltipBackground: readCssColor("--chart-tooltip-background", SOLARIZED_PALETTE.base3),
    tooltipBorder: readCssColor("--chart-tooltip-border", SOLARIZED_PALETTE.base00),
  };
}

const palette = ref<ChartPalette>(resolveChartPalette());
let themeObserver: MutationObserver | null = null;

/**
 * 主题切换后重算调色板。观察 `<html class>` 而不是 import `useTheme()`：
 * 本模块属于设计系统层，让被依赖方反向依赖应用的 theme 模块会造成依赖倒置；
 * 而 `applyThemeToDOM` 是唯一的主题写入点，它必然切换 `.dark` 类，观察这个
 * 约定即可，且 observer 只注册一次、生命周期与页面同长，不受组件作用域影响。
 */
function ensureThemeObserver() {
  if (
    themeObserver ||
    typeof document === "undefined" ||
    typeof MutationObserver === "undefined"
  ) {
    return;
  }
  themeObserver = new MutationObserver(() => {
    palette.value = resolveChartPalette();
  });
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class"],
  });
}

/** 返回随主题变化自动重算的图表调色板（模块级单例，多个调用方共享同一份）。 */
export function useChartPalette(): Readonly<Ref<ChartPalette>> {
  palette.value = resolveChartPalette();
  ensureThemeObserver();
  return readonly(palette);
}
