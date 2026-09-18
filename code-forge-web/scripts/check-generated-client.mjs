#!/usr/bin/env node
/**
 * 生成后体检：保证「重新生成 SDK」不会把客户端配置丢掉。
 *
 * ── 为什么需要它 ────────────────────────────────────────────────────
 * `generated/` 是 `--clean` 会整个删掉的**产物**，所以任何修复都不能写在那里。
 * 客户端配置（baseURL + withCredentials）走的是 openapi-ts 的 `runtimeConfigPath`
 * 机制：生成器会把 `src/config/api-client.ts`（生成目录之外）接进
 * `generated/client.gen.ts`。本脚本在每次生成后断言这件事**真的发生了**。
 *
 * 一旦将来 openapi-ts 升级把该选项改掉/忽略掉，生成会在这里**直接失败**，
 * 而不是静默退回「烘焙跨源绝对地址 + 不带凭据」那种坏状态。
 *
 * 用法：node scripts/check-generated-client.mjs
 *   已挂在 package.json 的 generate:api / generate:api:force 末尾。
 */
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const ROOT = resolve(import.meta.dirname, '..')
const rel = (p) => p.replace(/\\/g, '/').replace(ROOT.replace(/\\/g, '/') + '/', '')

const problems = []

// ── 1. client.gen.ts 必须 import 我们的运行时配置，并调用 createClientConfig() ──
let clientGen = ''
try {
  clientGen = readFileSync(join(ROOT, 'generated', 'client.gen.ts'), 'utf8')
} catch {
  problems.push('generated/client.gen.ts 不存在 —— 生成物不完整，先跑一次 openapi-ts')
}

if (clientGen) {
  if (!/from\s+['"][^'"]*config\/api-client(\.ts)?['"]/.test(clientGen)) {
    problems.push(
      'generated/client.gen.ts 没有 import src/config/api-client —— ' +
        'openapi-ts.config.ts 的 runtimeConfigPath 未生效，客户端会退回默认配置'
    )
  }
  if (!/createClientConfig\s*\(/.test(clientGen)) {
    problems.push('generated/client.gen.ts 没有调用 createClientConfig() —— runtimeConfigPath 未生效')
  }
  // baseUrl 必须为 false：否则 servers[0] 的绝对地址（http://localhost:8101/api）会被烘焙成默认值
  if (/createConfig[^;]*baseURL\s*:\s*['"]/.test(clientGen)) {
    problems.push(
      'generated/client.gen.ts 里 createConfig 带了写死的 baseURL —— ' +
        '应让 openapi-ts.config.ts 的 baseUrl 保持 false，交给运行时配置决定'
    )
  }
}

// ── 2. 生成器配置自身必须仍声明 runtimeConfigPath ──
const configSource = readFileSync(join(ROOT, 'openapi-ts.config.ts'), 'utf8')
if (!/runtimeConfigPath\s*:/.test(configSource)) {
  problems.push('openapi-ts.config.ts 里没有 runtimeConfigPath —— 下次生成就会丢配置')
}
if (!/baseUrl\s*:\s*false/.test(configSource)) {
  problems.push('openapi-ts.config.ts 里 baseUrl 不是 false —— servers[0] 会被烘焙进生成物')
}

// ── 3. 运行时配置文件本身必须导出 createClientConfig 且带 withCredentials ──
const runtimeConfig = readFileSync(join(ROOT, 'src', 'config', 'api-client.ts'), 'utf8')
if (!/export\s+const\s+createClientConfig/.test(runtimeConfig)) {
  problems.push('src/config/api-client.ts 没有导出 createClientConfig —— 生成器约定要求导出它')
}
if (!/withCredentials\s*:\s*true/.test(runtimeConfig)) {
  problems.push('src/config/api-client.ts 缺少 withCredentials: true —— 跨源部署时 cookie 会被丢弃')
}

if (problems.length) {
  console.error('\n✗ generated 客户端配置体检未通过：')
  for (const p of problems) console.error('  · ' + p)
  console.error('\n  修复方向：openapi-ts.config.ts 的 plugins 应保持')
  console.error("    { name: '@hey-api/client-axios', baseUrl: false, runtimeConfigPath: './src/config/api-client.ts' }\n")
  process.exit(1)
}

console.log(
  `✓ 客户端配置体检通过（${rel(join(ROOT, 'generated', 'client.gen.ts'))} 已接 runtimeConfigPath，无烘焙 baseURL）`
)
