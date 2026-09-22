/**
 * 做题页 · 运行与提交。
 *
 * 从原 `ViewQuestion.vue` 下沉：试运行（/submission/run-with-judge）、提交（/submission/submit）
 * 与试运行结论的文案/配色推导。
 *
 * 试运行语义（与正式提交同源、但不落库）：前端把**可编辑用例**（输入 + 期望输出）整批发后端，
 * 后端调判题服务 + 沙箱执行并判定结果，返回 JudgeInfo（聚合结论 + 逐用例明细）。
 * 对错判定**只在后端**发生 —— 前端不再做第二份平行比对（避免双份真相错位）。
 *
 * 提交成功后通过 `onSubmitted` 把新提交 id 交回编排层，由编排层切到「提交记录」并选中它
 * —— 这是「提交后能立刻看到判题结果」的闭环入口。
 */
import { computed, ref, type Ref } from "vue";
import { toast } from "vue-sonner";
import { submit } from "@generated";
import { startRunJudge, fetchRunResult } from "@/api/runJudge";
import type { JudgeInfo } from "@/types/judge";
import { judgeColorClass, judgeResultEntry } from "@/constants/judge";
import type { SolveCase } from "./useSolveProblem";

export interface SolveRunDeps {
  questionId: Ref<string>;
  code: Ref<string>;
  selectedLanguage: Ref<string>;
  /** 可编辑用例（输入 + 期望输出），运行时整批发后端判题 */
  cases: Ref<SolveCase[]>;
  /** 提交成功回调：参数是新提交的 id */
  onSubmitted?: (submissionId: string) => void;
  /** 试运行结束后切到「测试结果」 */
  onRunFinished?: () => void;
}

export function useSolveRun(deps: SolveRunDeps) {
  const running = ref(false);
  const submitting = ref(false);
  const runResult = ref<JudgeInfo | null>(null);
  /** 逐用例明细（后端 JudgeInfo.caseResults），供测试结果面板并排展示 输入/实际/期望 */
  const caseResults = computed(() => runResult.value?.caseResults ?? []);

  /**
   * 聚合结论文案：后端 JudgeInfo.message（Accepted / Wrong Answer / ...）；
   * 全用例无期望输出（中性态，message 为 null）→ 回落「已执行」。
   */
  const verdictText = computed(() => judgeResultEntry(runResult.value?.message).text);

  /** 结论配色：失败态红/橙、通过绿、中性蓝 */
  const verdictClass = computed(() =>
    judgeColorClass(judgeResultEntry(runResult.value?.message).color)
  );

  /** 编译 / 系统错误的原始诊断（编译器 stderr / 异常栈）。常规判定为空，不重复展示。 */
  const verdictDetail = computed(() => runResult.value?.detail ?? "");

  /** 运行代码：可编辑用例整批发后端，立即拿到 runId，再轮询拿到判题结论。 */
  async function handleRun() {
    if (!deps.code.value.trim()) {
      toast.error("请先编写有效的代码");
      return;
    }
    running.value = true;
    try {
      const runId = await startRunJudge({
        questionId: deps.questionId.value,
        code: deps.code.value,
        language: deps.selectedLanguage.value,
        cases: deps.cases.value.map((c) => ({
          input: c.input,
          // 空串视为「无期望输出」：后端按中性态处理（只执行、不判对错）
          expectedOutput: c.expectedOutput ? c.expectedOutput : undefined,
        })),
      });
      // 轮询结果：后端判题在独立线程池异步跑（~2s），前端按 runId 取，最多等 30s
      const info = await pollRunResult(runId);
      runResult.value = info;
      deps.onRunFinished?.();
    } catch (error: any) {
      console.error("运行失败:", error);
      toast.error(error?.message || "运行失败，请确认判题服务已启动");
    } finally {
      running.value = false;
    }
  }

  /**
   * 按 runId 轮询试运行结果，直到就绪或客户端超时。
   *
   * @param runId 发起运行时拿到的 id
   * @param timeoutMs 整体超时（默认 30s，短于后端结果缓存 TTL 60s，避免「轮询一个已过期 id」
   * @param intervalMs 轮询间隔
   */
  async function pollRunResult(runId: string, timeoutMs = 30000, intervalMs = 800): Promise<JudgeInfo> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const info = await fetchRunResult(runId);
      if (info !== null) {
        return info;
      }
      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }
    throw new Error("试运行超时，请重新运行");
  }

  /**
   * 提交代码。
   *
   * 接口返回新提交的 id（`BaseResponseLong.data`）。该字段在 SDK 里被标成 `number`
   * （字段名是 `data`，不匹配 merge-openapi 的「像 id」改写规则），但后端 `JsonConfig`
   * 已把 Long 序列化为字符串，所以运行时是字符串 —— 这里显式 `String()` 对齐类型并
   * 保证下游拿到的 id 一定是字符串，绝不做数值转换（雪花 id 超 53 位精度）。
   */
  async function handleSubmit() {
    if (!deps.code.value || deps.code.value.length === 0) {
      toast.error("请先编写有效的代码");
      return;
    }
    submitting.value = true;
    try {
      const result = await submit({
        body: {
          language: deps.selectedLanguage.value,
          code: deps.code.value,
          questionId: deps.questionId.value,
        },
      });
      if (result.data && result.data.code === 0) {
        toast.success("代码提交成功，正在判题");
        const newId = result.data.data;
        if (newId !== undefined && newId !== null && String(newId).length > 0) {
          deps.onSubmitted?.(String(newId));
        }
      } else {
        toast.error(result.data?.message || "提交失败");
      }
    } catch (error: any) {
      console.error("提交代码失败:", error);
      if (error.response?.data?.message) {
        toast.error(`提交失败: ${error.response.data.message}`);
      } else {
        toast.error("提交代码失败，请检查网络连接");
      }
    } finally {
      submitting.value = false;
    }
  }

  return {
    running,
    submitting,
    runResult,
    caseResults,
    verdictText,
    verdictDetail,
    verdictClass,
    handleRun,
    handleSubmit,
  };
}
