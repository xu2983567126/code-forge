import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

// 合并 Tailwind 冲突类名：后写的赢（例如 cn('px-2', cond && 'px-4') → 'px-4'）。
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
