// Solarized 运行时调色板桥接（从 UltiCode packages/design-system/src/palette.ts 精简）。
// 非 CSS 渲染器（Monaco 等）需要具体色值，通过此模块获取；CSS 消费者以 style.css 为准。
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
