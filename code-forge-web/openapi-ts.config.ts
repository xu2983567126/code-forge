import { defineConfig } from '@hey-api/openapi-ts';

/**
 * OpenAPI 类型生成配置。
 *
 * ── 为什么读「合并产物」而不是网关文档 ──────────────────────────────
 * 网关（8101）的 nextdoc4j 只「在 UI 里列出各服务 urls」，**不合并文档**。实测：
 *
 *   GET /v3/api-docs                   → paths: {}（空文档，347 字节）
 *   GET /v3/api-docs/swagger-config    → 正确列出 4 个服务
 *   GET /api/{svc}/v3/api-docs         → 各服务自己的文档（有内容）
 *
 * 直接指向单个服务也不行 —— 现有页面实际用到 user / question / submission
 * 三个服务的 SDK（store/user.ts、Login.vue、Register.vue、Submissions.vue 等），
 * 只读一个服务的文档会让其余 SDK 在 `--clean` 后全部消失。
 *
 * 而直接把 4 份文档配成数组更不行：各服务的 paths 是「服务内相对路径」，
 * 存在 8 处重名（/update、/get、/get/vo、/create、/delete、
 * /list/page、/list/page/vo、/inner/get/id），会互相覆盖。
 *
 * 因此由 `scripts/merge-openapi.mjs` 先抓取 4 份文档、给每条 path 注入
 * 服务归属前缀（/update → /question/update、/user/update），再合并为
 * `openapi-merged.json`，本配置读这份产物。
 *
 * ── 标准流程 ────────────────────────────────────────────────────────
 *   pnpm run generate:api:force   # = clean:api && merge:openapi && openapi-ts
 *   pnpm run generate:api         # 不清 generated，覆盖式重生成
 * （见 package.json 的 generate:api* 脚本；末尾会自动跑生成物体检）
 *
 * ── 与 baseURL 的关系（重要）────────────────────────────────────────
 * `openapi-merged.json` 带 `servers: [http://localhost:8101/api]`，生成器默认
 * （`baseUrl: true`）会把它**烘焙进** `generated/client.gen.ts`。那在浏览器里是
 * 一个**跨源绝对地址**：页面在 localhost:5173、请求打到 localhost:8101，
 * 而 axios 默认 `withCredentials:false` → 浏览器**既不发送也不保存**该响应下发的
 * `Set-Cookie` → 表现为「登录接口 200 成功，但紧接着取登录态报 NotLoginException」。
 *
 * 因此这里做两件事：
 *   1. `baseUrl: false` —— 不把第一个 server 烘焙成默认 baseURL；
 *   2. `runtimeConfigPath` —— 让生成产物 import 我们的
 *      `src/config/api-client.ts`（该文件**位于生成目录之外**），由它导出
 *      `createClientConfig()`：baseURL 取 `VITE_API_BASE_URL`，开发默认**相对**
 *      `/api`（走 vite.config.ts 的代理 → 同源，SameSite/CORS 全无关），
 *      并统一带上 `withCredentials: true` 兜底跨源场景。
 *
 * ⚠️ 关键约束：`generated/` 是 `--clean` 会整个删掉的**产物**，
 *    **任何修复/配置都不能写进那里**，否则重生成即失效。
 *    走 runtimeConfigPath 后，配置在生成期被自动接回，重生成不会丢。
 *    另有生成后体检 `scripts/check-generated-client.mjs` 兜底
 *    （若将来 openapi-ts 升级导致该选项失效，会在生成时直接报错而不是静默退回跨源）。
 *
 * ⚠️ 所以 url 必须保持「服务名前缀 + 无 /api」的形式。
 *    若改成带 `/api/...` 就会拼出重复的 /api/api/...；
 *    若某天网关真做了文档合并，把 input 换回
 *    `http://localhost:8101/v3/api-docs`，并把 VITE_API_BASE_URL 收回网关根即可。
 */
export default defineConfig({
  input: './openapi-merged.json',
  output: './generated',

  plugins: [
    {
      name: '@hey-api/client-axios',
      // 不要把 openapi-merged.json 的 servers[0]（绝对地址）烘焙成默认 baseURL
      baseUrl: false,
      // 官方机制：生成 client.gen.ts 时接上生成目录之外的运行时配置，
      // 该文件须导出 createClientConfig()。重生成（含 --clean）不会丢。
      runtimeConfigPath: './src/config/api-client.ts',
    },
  ],

  // 使用默认配置生成模型
  schemas: {
    export: 'interface',
    type: 'interface',
  },
});
