// 消息提示适配层：新页面统一走 vue-sonner 的 toast，视觉与 Solarized 主题一致。
// 老 Arco 页面仍直接 import Arco 的 Message（共存期不受影响）。
import { toast } from "vue-sonner";

export const message = {
  error: (msg: string) => toast.error(msg),
  warning: (msg: string) => toast.warning(msg),
  success: (msg: string) => toast.success(msg),
  info: (msg: string) => toast.info(msg),
};
