/**
 * OpenAPI 客户端运行时配置（**生成目录之外**，重生成 SDK 不会失效）。
 *
 * <p>本文件由 `openapi-ts.config.ts` 的 `runtimeConfigPath` 接进生成物：
 * 生成的 `generated/client.gen.ts` 会 import 本文件并调用 `createClientConfig()`。
 * 配置在**生成期**被自动接回，因此 `pnpm run generate:api:force`（含 `--clean`）
 * 重生成后依然生效。</p>
 *
 * ⚠️ 不要改回「`client.setConfig(...)` + 某个入口 import 本文件」的写法：
 * 那种写法要求有人真的 import 才生效，一旦漏引或被 clean 波及就会**静默失效**，
 * 表现为跨源请求丢失 cookie。
 */
import type { CreateClientConfig } from '@generated/client.gen'

/**
 * 后端统一入口：微服务网关（8101）。
 *
 * - 开发：`.env.development` 里配相对 `/api` → 走 `vite.config.ts` 的代理转发到 8101。
 *   浏览器视角是**同源**请求，cookie 自动携带，不受 SameSite / CORS 约束。
 * - 生产：用 `VITE_API_BASE_URL` 指定**同源**网关地址（如 `https://oj.example.com/api`）。
 *
 * 默认值同样是相对路径 —— 万一环境变量缺失，宁可走同源代理，也不回退到绝对地址。
 */
const baseURL = import.meta.env.VITE_API_BASE_URL ?? '/api'

/**
 * 生成器调用的钩子：返回客户端初始配置。
 *
 * `withCredentials: true` 是跨源部署（前端域名 ≠ API 域名）的必要条件 ——
 * axios 默认 `false` 时浏览器**既不发送也不保存**跨源响应的 `Set-Cookie`。
 * 同源（上面的默认情形）本就不需要它，带上也无害。
 */
export const createClientConfig: CreateClientConfig = (config = {}) => ({
  ...config,
  baseURL,
  withCredentials: true,
  throwOnError: config.throwOnError ?? false
})
