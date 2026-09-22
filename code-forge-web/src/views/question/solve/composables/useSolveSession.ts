/**
 * 做题页 · 会话编排。
 *
 * 把题目状态（useSolveProblem）与运行/提交（useSolveRun）组装成一份上下文，经
 * `provide/inject` 交给各面板 —— 面板之间不再互相传 props，也不需要回到页面组件拿状态。
 *
 * 面板状态（左侧 `activeTab`、右侧 `testTab`、选中提交 `selectedSubmissionId`）也在这里，
 * 因为「提交成功后自动切到提交记录并选中新提交」这条闭环需要跨面板协调。
 */
import { inject, onMounted, ref, type InjectionKey } from "vue";
import { useSolveProblem } from "./useSolveProblem";
import { useSolveRun } from "./useSolveRun";

export type SolveTab = "description" | "submissions";
export type SolveTestTab = "cases" | "results";

function createSolveContext() {
  const problem = useSolveProblem();

  /** 左侧主面板：题目描述 / 提交记录 */
  const activeTab = ref<SolveTab>("description");
  /** 右侧下部：测试用例 / 测试结果 */
  const testTab = ref<SolveTestTab>("cases");
  /** 提交记录里选中的那条（提交成功后由回调写入，驱动详情加载） */
  const selectedSubmissionId = ref<string | null>(null);

  const run = useSolveRun({
    questionId: problem.questionId,
    code: problem.code,
    selectedLanguage: problem.selectedLanguage,
    cases: problem.cases,
    onRunFinished: () => {
      testTab.value = "results";
    },
    onSubmitted: (submissionId) => {
      selectedSubmissionId.value = submissionId;
      activeTab.value = "submissions";
      testTab.value = "results";
    },
  });

  function selectSubmission(id: string | null) {
    selectedSubmissionId.value = id;
  }

  function goToSubmissions() {
    activeTab.value = "submissions";
  }

  onMounted(() => {
    void problem.loadQuestionDetail();
  });

  return {
    ...problem,
    ...run,
    activeTab,
    testTab,
    selectedSubmissionId,
    selectSubmission,
    goToSubmissions,
  };
}

export type SolveContext = ReturnType<typeof createSolveContext>;

const SolveContextKey: InjectionKey<SolveContext> = Symbol("code-forge:solve-context");

/**
 * 页面组件调用一次，输出 installProviders 交给模板之外的 setup 阶段注册。
 * Vue 的 provide() 必须在组件 setup 内执行，所以这里只返回回调、不在 composable 内直接 provide。
 */
export function useSolveSession() {
  const ctx = createSolveContext();
  return {
    ...ctx,
    installProviders(provideFn: (key: unknown, value: unknown) => void) {
      provideFn(SolveContextKey, ctx);
    },
  };
}

/** 面板里取会话上下文。 */
export function useSolveContext(): SolveContext {
  const ctx = inject(SolveContextKey, null);
  if (!ctx) {
    throw new Error("useSolveContext 必须在做题页（useSolveSession）内部使用");
  }
  return ctx;
}
