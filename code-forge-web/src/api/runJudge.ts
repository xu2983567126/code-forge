/**
 * 做题页「运行代码」接口（不落库、后端异步判题，前端轮询结果）。
 *
 * 流程：前端把**可编辑用例**（输入 + 期望输出）整批发后端 → 后端（judge-service 独立线程池）
 * 调沙箱执行并判题，POST 立即返回 runId；前端用 runId 轮询结果端点拿 JudgeInfo。
 *
 * 不进 generated SDK（避免重生成覆盖）；手写 fetch 复用相同的 baseURL / 凭证约定
 * （见 `src/config/api-client.ts`：相对 `/api` + 同源 cookie 携带）。
 */
import type { JudgeInfo } from "@/types/judge";

export interface RunCasePayload {
  input: string;
  /** 期望输出；为空表示用户自填 / 自定义用例，后端按中性态处理（只执行不判对错） */
  expectedOutput?: string;
}

export interface RunJudgePayload {
  questionId: string;
  code: string;
  language: string;
  cases: RunCasePayload[];
}

const baseURL = import.meta.env.VITE_API_BASE_URL ?? "/api";

/**
 * 发起一次试运行，立即返回 runId（真正判题在后端异步执行）。
 */
export async function startRunJudge(payload: RunJudgePayload): Promise<string> {
  const res = await fetch(`${baseURL}/submission/run-with-judge`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  let body: any;
  try {
    body = await res.json();
  } catch {
    throw new Error("运行失败，判题服务无响应");
  }
  if (body?.code !== 0 || !body?.data) {
    throw new Error(body?.message || `运行失败（HTTP ${res.status}）`);
  }
  return body.data as string;
}

/**
 * 轮询一次试运行结果。
 *
 * @returns JudgeInfo（已就绪）或 null（未就绪 / 已过期，前端应继续轮询或在客户端超时后判超时）
 */
export async function fetchRunResult(runId: string): Promise<JudgeInfo | null> {
  const res = await fetch(`${baseURL}/submission/run-with-judge/result/${runId}`, {
    method: "GET",
    credentials: "include",
  });
  let body: any;
  try {
    body = await res.json();
  } catch {
    throw new Error("试运行结果解析失败");
  }
  if (body?.code !== 0) {
    throw new Error(body?.message || `获取试运行结果失败（HTTP ${res.status}）`);
  }
  // data 为 null = 未就绪 / 已过期（结果缓存 TTL 默认 60s，尚未写出或不复存在）
  return (body?.data as JudgeInfo) ?? null;
}
