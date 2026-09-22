#!/usr/bin/env node
/**
 * 合并 4 个微服务的 OpenAPI 文档为一份，供 @hey-api/openapi-ts 生成前端 SDK。
 *
 * ── 为什么需要这个脚本 ────────────────────────────────────────────────
 * 网关（8101）的 nextdoc4j 只「在 UI 里列出各服务 urls」，**不合并文档**：
 *
 *   GET /v3/api-docs                    → paths: {}（空文档，322 字节）
 *   GET /v3/api-docs/swagger-config     → 正确列出 4 个服务
 *   GET /api/{svc}/v3/api-docs          → 各服务自己的文档（有内容）
 *
 * 直接把 `input` 配成 4 个文档的数组也不行，因为各服务的 paths 是
 * **服务内相对路径**，存在大量同名冲突（实测 8 处）：
 *
 *   /update  /create  /delete  /get  /get/vo  /list/page  /list/page/vo  /inner/get/id
 *   （question 与 user 都有）
 *
 * 冲突会让生成的 SDK 函数互相覆盖。所以本脚本给每条 path 注入
 * **服务归属前缀**（`/update` → `/question/update`、`/user/update`），
 * 这正是历史上那份「网关真聚合文档」的形态 —— 也因此生成的函数名
 * （updateQuestion / updateUser）与 `url`（/question/update）都与既有产物一致。
 *
 * ── 与 baseURL 的关系（重要）──────────────────────────────────────────
 * `src/config/api-client.ts` 运行时用 `client.setConfig({ baseURL })` 把
 * baseURL 覆盖为 `http://localhost:8101/api`（可由 VITE_API_BASE_URL 覆盖）。
 * 合并后的 url 形如 `/question/update`，与 baseURL 拼出
 * `http://localhost:8101/api/question/update` —— 正是网关的正确入口。
 *
 * ⚠️ 所以 url 必须保持「服务名前缀 + 无 /api」的形式。
 *    若改成带 `/api/...` 就会拼出重复的 /api/api/...。
 *
 * ── 用法 ──────────────────────────────────────────────────────────────
 *   node scripts/merge-openapi.mjs            # 抓取远程并写出 openapi-merged.json
 *   node scripts/merge-openapi.mjs --check    # 只做冲突体检，不写文件
 *
 * 前置：后端 5 个服务（含网关 8101）必须已启动。
 * 产物 `openapi-merged.json` 放在工程根，被 openapi-ts.config.ts 引用。
 */

import { writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(__dirname, '..')
const OUT_FILE = resolve(ROOT, 'openapi-merged.json')

const GATEWAY = 'http://localhost:8101'

/**
 * 服务清单：文档地址 → 注入的前缀。
 *
 * 前缀必须与网关的路由断言一致（网关把 /api/{svc}/** 转发给对应服务），
 * 这样 baseURL(`.../api`) + url(`/question/...`) 才能命中。
 *
 * judge 服务不生成（只有 /inner/do 一个内部接口，前端不直接调用），
 * 用 include: false 排除。抓取失败时不阻塞其余服务（见 main 的容错处理）。
 */
const SERVICES = [
  { name: 'question', prefix: '/question', include: true },
  { name: 'user', prefix: '/user', include: true },
  { name: 'submission', prefix: '/submission', include: true },
  { name: 'judge', prefix: '/judge', include: false },
]

const argv = process.argv.slice(2)
const CHECK_ONLY = argv.includes('--check')

/** 拉取单个服务的 OpenAPI 文档。 */
async function fetchDoc(svc) {
  const url = `${GATEWAY}/api/${svc.name}/v3/api-docs`
  const res = await fetch(url)
  if (!res.ok) {
    throw new Error(`抓取 ${svc.name} 失败：HTTP ${res.status} ${res.statusText}（${url}）`)
  }
  return res.json()
}

/**
 * 规范化 operationId，去掉 springdoc 自动加的「重载去重后缀」。
 *
 * ── 背景 ──────────────────────────────────────────────────────────────
 * 后端 springdoc 遇到同名方法的重载时，会给后续的加 `_1`、`_2` 后缀。
 * 本项目的实例：
 *
 *   QuestionController.getId()      → operationId `getQuestionById`
 *   QuestionController.getVOById()  → operationId `getQuestionVOById`
 *   QuestionController.getById()    → operationId `getQuestionById_1`   ← 注意 _1
 *   UserController.getLoginUser()   → operationId `getLoginUser`
 *   UserController.getLoginUserInner() → operationId `getLoginUser_1`   ← 注意 _1
 *
 * 这些 `_1` 尾巴会一路传到前端函数名（`getQuestionById1`、`getLoginUser1`），
 * 语义混乱且极易踩坑（调用方会误以为有两个版本）。
 *
 * 之所以原来「撞不上」，是因为旧文档把 `/inner/**` 也算进来凑成了一对；
 * 一旦按下文过滤掉内部接口，剩下的那个反而成了带后缀的孤儿。
 *
 * 处理：剥掉末尾的 `_数字`。
 *
 * ⚠️ 若剥离后与其它 operationId 重名，会打印告警 —— 那时需要人工确认，
 *    不能盲目去重（否则两个接口会共用一个函数名）。
 */
function normalizeOperationId(opId, used, where, warnings) {
  if (typeof opId !== 'string') return opId
  const stripped = opId.replace(/_\d+$/, '')
  if (stripped === opId) return opId
  if (used.has(stripped)) {
    warnings.push(`${opId} → ${stripped} 已被占用（${where}），保留原名以避免冲突`)
    return opId
  }
  return stripped
}

/**
 * 判断是否为「服务间内部接口」，这类路径不应进入前端 SDK。
 *
 * 约定：各服务的 `/inner/**` 是 Feign 相互调用的入口（如
 * `/inner/get/id`、`/inner/get/login`、`/inner/submission/update`），
 * 前端永远不会直接请求它们。
 */
function isInternalPath(p) {
  return /^\/inner(\/|$)/.test(p)
}

/** 给 paths 注入前缀，同时收集冲突信息。 */
function mergePaths(merged, paths, svc, collisions, skipped) {
  for (const [p, item] of Object.entries(paths || {})) {
    if (isInternalPath(p)) {
      skipped.push(`${svc.name}${p}`)
      continue
    }
    const full = svc.prefix + (p === '/' ? '' : p)
    if (merged[full]) {
      collisions.push({ path: full, a: merged[full].__svc, b: svc.name })
      continue
    }
    merged[full] = { __svc: svc.name, ...item }
  }
}

/**
 * 判断两份 schema 是否「结构上」一致。
 *
 * 忽略 description / title / example / deprecated / xml 等纯文档性字段，
 * 避免不同服务给同一共享 schema（如 BaseResponse、Page）写了不同注释就误报冲突；
 * 但若字段、类型、嵌套结构真的不同，必须识别出来 —— 那是会产出错误类型的真冲突。
 */
function schemaStructurallyEqual(a, b) {
  const DOC_FIELDS = new Set(['description', 'title', 'example', 'deprecated', 'xml'])
  const clean = (o) => {
    if (Array.isArray(o)) return o.map(clean)
    if (o && typeof o === 'object') {
      const out = {}
      for (const [k, v] of Object.entries(o)) {
        if (DOC_FIELDS.has(k)) continue
        out[k] = clean(v)
      }
      return out
    }
    return o
  }
  return JSON.stringify(clean(a)) === JSON.stringify(clean(b))
}

/**
 * 合并所有组件的 schema。同名 schema 若「结构」不同会被记录，并在末尾升级为硬错误，
 * 避免静默保留先到定义、把错误类型产进前端 SDK。
 */
function mergeComponent(merged, src, kind, svc, schemaConflicts) {
  for (const [key, val] of Object.entries(src || {})) {
    if (merged[key] === undefined) {
      merged[key] = val
    } else if (!schemaStructurallyEqual(merged[key], val)) {
      schemaConflicts.push({ kind, key, svc })
    }
  }
}

/** 收集所有 tags。 */
function mergeTags(acc, tags, svc) {
  for (const t of tags || []) {
    const name = typeof t === 'string' ? t : t.name
    if (name && !acc.some((x) => x.name === name)) {
      acc.push(typeof t === 'string' ? { name: t } : t)
    }
  }
}

/**
 * 把「主键类」的 int64 字段由 number 改成 string。
 *
 * ── 背景 ──────────────────────────────────────────────────────────────
 * 后端主键是雪花 Long（19 位），`JsonConfig` 已统一序列化为字符串以规避
 * JS 的 53 位精度天花板。但 OpenAPI 把 `int64` 映射为 TS `number`，导致生成
 * 的 SDK 类型（`id` / `questionId` / `userId` …）是 number，前端一 `parseInt`
 * 就丢精度 → 后端按被舍入后的 id 查不到题目。
 *
 * 这里在合并阶段把**命名像 id 的** int64 字段就地改写成 `string`，使生成的
 * SDK 类型与后端「id 即字符串」的契约对齐，从源头消灭这一类 bug。
 *
 * 只 targeting 名称像 id 的字段（`id` 或以 `Id` 结尾），避免误伤其它
 * int64（如分页 `total` 仍保持 number，由后端单独处理）。
 */
function isIdKey(name) {
  if (typeof name !== 'string') return false
  const n = name.toLowerCase()
  if (n === 'id' || n === 'ids') return true
  // 驼峰里的独立 Id 段：userId / questionId / questionIdList / idList。
  // ⚠️ 只认「Id」这个驼峰段（前接小写、后接大写或收尾），不认小写 id ——
  // 否则 index / valid / idle 这类词会被误判成主键。
  if (/(^|[a-z])Id([A-Z]|$)/.test(name)) return true
  // 复数收尾：bankIds / questionIds（s 小写，上面那条接不住）
  if (/Ids$/.test(name)) return true
  return false
}

/** 把子树里所有 int64（含 array.items / 嵌套对象）就地改写成 string。 */
function convertAllInt64InSubtree(node) {
  if (node === null || typeof node !== 'object') return
  if (Array.isArray(node)) {
    for (const item of node) convertAllInt64InSubtree(item)
    return
  }
  if (node.type === 'integer' && /^(u?int64)$/.test(node.format || '')) {
    node.type = 'string'
    delete node.format
  }
  for (const v of Object.values(node)) convertAllInt64InSubtree(v)
}

function normalizeInt64ToString(node, key) {
  if (node === null || typeof node !== 'object') return
  if (Array.isArray(node)) {
    for (const item of node) normalizeInt64ToString(item, key)
    return
  }
  // 参数对象（path / query）：name 像 id 且 schema 是 int64
  if (
    node.in &&
    node.name &&
    isIdKey(node.name) &&
    node.schema &&
    node.schema.type === 'integer' &&
    /^(u?int64)$/.test(node.schema.format || '')
  ) {
    node.schema.type = 'string'
    delete node.schema.format
  }
  for (const [k, v] of Object.entries(node)) {
    if (v && typeof v === 'object') {
      // 名称像 id：整棵子树（含 array items / 嵌套）里的 int64 都转 string，
      // 覆盖 idList / ids 这类集合字段
      if (isIdKey(k)) convertAllInt64InSubtree(v)
      else normalizeInt64ToString(v, k)
    }
  }
}

async function main() {
  const all = []
  for (const svc of SERVICES) {
    try {
      const doc = await fetchDoc(svc)
      all.push({ svc, doc })
      console.log(
        `  抓取 ${svc.name.padEnd(11)} ${String(Object.keys(doc.paths || {}).length).padStart(3)} paths` +
          (svc.include ? '' : '  (不并入产物)')
      )
    } catch (err) {
      if (svc.include) throw err // 必需服务抓取失败：直接终止
      // 非必需服务（如 judge）抓取失败：跳过，不阻塞其余服务的 SDK 生成
      console.log(`  ⚠️ ${svc.name} 抓取失败（${err.message}），已跳过（不并入产物）`)
    }
  }

  // ── 冲突体检（在包含 judge 的全集上做，暴露潜在问题）──────────────
  const probe = {}
  const rawCollisions = []
  for (const { svc, doc } of all) {
    for (const p of Object.keys(doc.paths || {})) {
      if (isInternalPath(p)) continue // /inner/** 会被排除，不参与冲突判断
      const full = svc.prefix + (p === '/' ? '' : p)
      if (probe[full]) {
        rawCollisions.push(`${full}  (${probe[full]} ↔ ${svc.name})`)
      } else {
        probe[full] = svc.name
      }
    }
  }

  const paths = {}
  const collisions = []
  const skipped = []
  const schemas = {}
  const schemaConflicts = []
  const tags = []
  let title = null

  for (const { svc, doc } of all) {
    if (!svc.include) continue
    mergePaths(paths, doc.paths, svc, collisions, skipped)
    mergeComponent(schemas, doc.components?.schemas, 'schemas', svc.name, schemaConflicts)
    mergeComponent(schemas, doc.components?.securitySchemes, 'securitySchemes', svc.name, schemaConflicts)
    mergeTags(tags, doc.tags, svc.name)
    title = title || doc.info?.title
  }

  // 主键类 int64（id / *Id）→ string，对齐后端字符串序列化（见 normalizeInt64ToString）
  normalizeInt64ToString(paths)
  normalizeInt64ToString(schemas)

  // 清掉注入时带的内部标记
  for (const item of Object.values(paths)) delete item.__svc

  const pathCount = Object.keys(paths).length

  // ── operationId 规范化：剥掉 springdoc 的重载后缀 `_1` ──────────────
  // 必须在「排除 /inner/**」之后做：正是这些内部接口的移除，才让幸存的
  // 那个 operationId 成了带 `_1` 尾巴的孤儿。
  const usedOpIds = new Set()
  for (const item of Object.values(paths)) {
    for (const m of ['get', 'post', 'put', 'delete', 'patch']) {
      const op = item[m]
      if (op && typeof op.operationId === 'string') usedOpIds.add(op.operationId)
    }
  }
  const opIdWarnings = []
  const renamed = []
  for (const [p, item] of Object.entries(paths)) {
    for (const m of ['get', 'post', 'put', 'delete', 'patch']) {
      const op = item[m]
      if (!op || typeof op.operationId !== 'string') continue
      const before = op.operationId
      usedOpIds.delete(before)
      const after = normalizeOperationId(before, usedOpIds, `${m.toUpperCase()} ${p}`, opIdWarnings)
      op.operationId = after
      usedOpIds.add(after)
      if (after !== before) renamed.push(`${before} → ${after}   (${m.toUpperCase()} ${p})`)
    }
  }

  console.log()
  console.log(`  合并后 paths: ${pathCount}   schemas: ${Object.keys(schemas).length}`)

  if (skipped.length) {
    console.log()
    console.log(`  已排除 ${skipped.length} 个服务间内部接口（/inner/**，前端不调用）：`)
    for (const s of skipped) console.log('    - ' + s)
  }

  if (renamed.length) {
    console.log()
    console.log(`  已规范化 ${renamed.length} 个 operationId（去掉 springdoc 重载后缀）：`)
    for (const r of renamed) console.log('    - ' + r)
  }

  if (opIdWarnings.length) {
    console.log()
    console.log('  ⚠️ operationId 规范化受阻：')
    for (const w of opIdWarnings) console.log('    - ' + w)
  }

  if (rawCollisions.length) {
    console.log()
    console.log('  注入前缀后仍冲突（需手工排查）：')
    for (const c of rawCollisions) console.log('    - ' + c)
  }
  if (collisions.length) {
    console.log()
    console.log('  ⚠️ 并入产物时有冲突（条目被跳过）：')
    for (const c of collisions) console.log(`    - ${c.path}  (${c.a} ↔ ${c.b})`)
  }
  if (schemaConflicts.length) {
    console.log()
    console.log('  ⚠️ 同名 schema 结构不一致（已保留先到的定义）：')
    for (const c of schemaConflicts) console.log(`    - ${c.kind}.${c.key}  (来自 ${c.svc})`)
    throw new Error(
      `发现 ${schemaConflicts.length} 处同名 schema 结构冲突，已终止生成以避免把错误类型产进前端 SDK。` +
        `冲突项：${schemaConflicts.map((c) => c.kind + '.' + c.key).join('、')}。` +
        `请对齐各服务的 schema 定义，或为该 schema 加服务前缀命名空间后再合并。`
    )
  }

  if (CHECK_ONLY) {
    console.log()
    console.log('  --check 模式：未写文件。')
    return
  }

  if (!pathCount) {
    throw new Error('合并结果为空 —— 请确认后端服务已启动、网关可访问。')
  }

  const mergedDoc = {
    openapi: '3.0.1',
    info: {
      title: 'My OJ 微服务合并文档（由 scripts/merge-openapi.mjs 生成）',
      description:
        '由 4 个微服务的 OpenAPI 文档合并而来，path 已注入服务归属前缀。' +
        '请勿手工编辑 —— 修改请改脚本或后端注解后重新运行生成。',
      version: '1.0.0',
    },
    // 一个占位 server：真正的 baseURL 由运行时 client.setConfig 覆盖。
    servers: [{ url: GATEWAY + '/api' }],
    paths,
    ...(Object.keys(schemas).length ? { components: { schemas } } : {}),
    ...(tags.length ? { tags } : {}),
  }

  writeFileSync(OUT_FILE, JSON.stringify(mergedDoc, null, 2), 'utf8')
  console.log()
  console.log(`  已写出 ${OUT_FILE}`)
}

main().catch((err) => {
  console.error()
  console.error('  ✖ ' + err.message)
  process.exit(1)
})
