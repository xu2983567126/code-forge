// submission-status 的本地类型桥接。
// 原 UltiCode 从 @ulticode/sandbox-types 引入 DFormVerdict、从 @ulticode/badge-config 引入 SemanticColor，
// 本项目把二者收敛到本地，避免跨包依赖。
import type { SemanticColor } from "@/lib/badge-config";

// 12 个 verdict（Title Case，与 UltiCode 保持一致）。新增 verdict 时 index.ts 的 6 张映射表会被 TS 强制补全。
export type DFormVerdict =
  | "Accepted"
  | "Wrong Answer"
  | "Time Limit Exceeded"
  | "Memory Limit Exceeded"
  | "Output Limit Exceeded"
  | "Runtime Error"
  | "Compile Error"
  | "Presentation Error"
  | "System Error"
  | "Sandbox Error"
  | "Judging"
  | "Pending";

export type { SemanticColor };
