// Garden 运行时调色板桥接（移植并本地化自 UltiCode packages/design-system/src/palette.ts）。
//
// CSS 消费者以 src/style.css 为准；非 CSS 渲染器（Monaco、canvas 绘制、图表导出等）
// 拿不到 `var(--token)`，必须经由本模块取具体色值：
//   - SOLARIZED_PALETTE 承载 16 个原始刻度值（键名沿用历史的 solarized-* 命名，
//     值与 style.css 的 --solarized-* 逐字对应）
//   - readCssColor 在运行时解析当前主题下的 CSS 变量，无 DOM 时回落到 canonical 值
//
// 消费方不得自造第二份调色板 —— 这是唯一的运行时副本。

export const SOLARIZED_PALETTE = {
  base03: "#1c2412",
  base02: "#26301b",
  base01: "#545c45",
  base00: "#6a7259",
  base0: "#838f81",
  base1: "#a2afa9",
  base2: "#eae8d8",
  base3: "#e3e1d1",
  yellow: "#9c7a14",
  orange: "#b4622d",
  red: "#8f4822",
  magenta: "#a05c74",
  violet: "#6c71c4",
  blue: "#46769b",
  cyan: "#4e7d64",
  green: "#588e67",
} as const;

export type SolarizedPaletteKey = keyof typeof SOLARIZED_PALETTE;
export type SolarizedPaletteValue =
  (typeof SOLARIZED_PALETTE)[SolarizedPaletteKey];

const CANONICAL_VALUES = Object.fromEntries(
  Object.values(SOLARIZED_PALETTE).map((value) => [value, true]),
) as Record<SolarizedPaletteValue, true>;

function normalizeVariable(variable: string): string {
  const trimmed = variable.trim();
  return trimmed.startsWith("var(") ? trimmed.slice(4, -1) : trimmed;
}

/**
 * 把 CSS 自定义属性解析成具体色值，供非 CSS 渲染器使用。
 *
 * 参数同时接受 `"--token"` 与 `"var(--token)"` 两种写法。有 DOM 时优先取浏览器
 * 计算值（即当前主题的生效值）；无 DOM 或属性未定义时返回 fallback。
 *
 * fallback 必须是 {@link SOLARIZED_PALETTE} 里的 canonical 值（运行时校验），
 * 这样渲染器不可能画出调色板之外的野色。
 *
 * @param variable CSS 自定义属性名
 * @param fallback 无 DOM / 属性未定义时的回落值，必须取自 SOLARIZED_PALETTE
 * @throws {TypeError} fallback 不是 canonical 调色板值
 */
export function readCssColor(
  variable: string,
  fallback: SolarizedPaletteValue,
): string {
  if (!Object.prototype.hasOwnProperty.call(CANONICAL_VALUES, fallback)) {
    throw new TypeError(
      `readCssColor fallback must be a canonical Garden palette value, got "${fallback}"`,
    );
  }
  const runtime = globalThis as typeof globalThis & {
    document?: { documentElement: unknown };
    getComputedStyle?: (
      element: unknown,
    ) => { getPropertyValue(name: string): string };
  };
  if (!runtime.document || typeof runtime.getComputedStyle !== "function") {
    return fallback;
  }
  const resolved = runtime
    .getComputedStyle(runtime.document.documentElement)
    .getPropertyValue(normalizeVariable(variable))
    .trim();
  return resolved.length > 0 ? resolved : fallback;
}
