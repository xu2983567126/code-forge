/**
 * 做题页 · 题目与代码状态。
 *
 * 从原 `ViewQuestion.vue` 下沉：题目加载、用例解析与编辑、编辑器预填、语言与模板。
 * 与「运行 / 提交」无关的部分都在这里（那部分在 useSolveRun）。
 */
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { toast } from "vue-sonner";
import { getQuestionVoById } from "@generated";
import type { QuestionVo } from "@generated";
import { LANGUAGE_OPTIONS } from "@/constants/language";
import { getDefaultTemplate } from "@/constants/codeTemplates";
import { deriveSolutionDriver } from "@/constants/solutionTemplate";

/**
 * 题级骨架 `codeTemplate` 由后端 QuestionVO 透出；核心代码模式驱动由前端按 codeTemplate 镜像现推
 * （deriveSolutionDriver），不再经后端传输。SDK 未重新生成，故在此做局部类型扩展。
 */
export type QuestionSolveVo = QuestionVo & { codeTemplate?: string };

export interface SolveCase {
  input: string;
  expectedOutput: string;
}

export function useSolveProblem() {
  const route = useRoute();

  // 题目 id 是 19 位雪花字符串，绝不能再 parseInt（JS Number 53 位精度会丢尾数位）
  const questionId = computed(() => route.params.id as string);

  const loading = ref(false);
  const questionDetail = ref<QuestionVo | null>(null);
  const code = ref("");
  const selectedLanguage = ref("java");

  /** 核心代码模式判定：由 codeTemplate 现推驱动（能推出即为核心模式），仅用于控制模板库入口等交互。 */
  const driverCode = computed(() =>
    deriveSolutionDriver((questionDetail.value as QuestionSolveVo | null)?.codeTemplate)
  );
  const isCoreMode = computed(() => !!(driverCode.value && driverCode.value.trim()));

  /**
   * 测试用例：VO 透出的是脱敏样例 `examples`（前 EXAMPLE_LIMIT 条），解析成本地可编辑副本
   * —— 运行时把当前用例的 input 作为标准输入传给 /submission/run。
   */
  const cases = ref<SolveCase[]>([]);
  const activeCase = ref(0);
  const activeCaseData = computed(
    () => cases.value[activeCase.value] ?? { input: "", expectedOutput: "" }
  );

  /**
   * 输入框必须绑独立状态、不能 v-model 到 `activeCaseData` 上 —— 它是 computed 返回的
   * 对象，`cases` 为空（VO 未透出用例）时每次都是临时字面量，用户敲的输入根本落不进
   * 状态，运行时 input 恒为空 → 沙箱里 Scanner 阻塞等输入 → 超时。
   * 切换用例 chip 时从用例载入；用户编辑时写回当前用例（两端单向同步，不会成环）。
   */
  const caseInput = ref("");
  /**
   * 期望输出也需绑独立状态（与 caseInput 同理）：它是用户**可编辑**的，
   * 运行时整批发后端判题。绑到 cases[activeCase].expectedOutput 的引用上，切换用例时载入、编辑时写回。
   */
  const caseOutput = ref("");
  watch(
    [cases, activeCase],
    () => {
      caseInput.value = cases.value[activeCase.value]?.input ?? caseInput.value;
      caseOutput.value = cases.value[activeCase.value]?.expectedOutput ?? caseOutput.value;
    },
    { immediate: true }
  );
  watch(caseInput, (v) => {
    const c = cases.value[activeCase.value];
    if (c) c.input = v;
  });
  watch(caseOutput, (v) => {
    const c = cases.value[activeCase.value];
    if (c) c.expectedOutput = v;
  });

  // 题目用例解析
  watch(
    () => questionDetail.value?.examples,
    (raw) => {
      try {
        const parsed =
          typeof raw === "string" ? JSON.parse(raw || "[]") : (raw ?? []);
        cases.value = Array.isArray(parsed)
          ? parsed.filter((c) => c && typeof c.input === "string")
          : [];
      } catch {
        cases.value = [];
      }
      activeCase.value = 0;
    },
    { immediate: true }
  );

  // 编辑器预填：题级 codeTemplate 优先；核心代码模式下用其作为用户方法骨架；
  // 否则回落到语言级默认模板。仅首次预填（做题页无草稿持久化，不会吃掉用户输入）。
  function prefillCode() {
    const q = questionDetail.value;
    if (!q || code.value.trim()) return;
    const solve = q as QuestionSolveVo;
    if (solve.codeTemplate && solve.codeTemplate.trim()) {
      code.value = solve.codeTemplate;
    } else if (!isCoreMode.value) {
      code.value = getDefaultTemplate("java")?.code || "";
    }
    // 核心代码模式且无题级模板：留空，等用户写 Solution
  }
  watch(() => questionDetail.value, prefillCode);

  /** 重置编辑器：回到题级骨架（或语言默认模板），撤掉用户的全部改动。 */
  function resetCode() {
    code.value = "";
    prefillCode();
    if (code.value.trim()) {
      toast.success("已恢复为初始代码");
    } else {
      toast.info("本题无预置骨架");
    }
  }

  // 从模板库插入：编辑器非空且与所选模板不同则确认覆盖
  function applyTemplate(tplCode: string) {
    if (code.value.trim() && code.value !== tplCode) {
      if (!window.confirm("替换当前编辑器内容？")) return;
    }
    code.value = tplCode;
  }

  function notifyLanguageChange() {
    const opt = LANGUAGE_OPTIONS.find((o) => o.value === selectedLanguage.value);
    toast.success(`已切换到 ${opt?.label || selectedLanguage.value}`);
  }

  async function loadQuestionDetail() {
    if (!questionId.value) {
      toast.error("题目ID不存在");
      return;
    }
    loading.value = true;
    try {
      const res = await getQuestionVoById({
        path: { id: questionId.value },
      });
      if (res.data && res.data.code === 0 && res.data.data) {
        questionDetail.value = res.data.data;
      } else {
        toast.error("获取题目详情失败");
      }
    } catch (error) {
      console.error("加载题目详情失败:", error);
      toast.error("加载题目详情失败");
    } finally {
      loading.value = false;
    }
  }

  async function refresh() {
    await loadQuestionDetail();
    toast.info("已刷新");
  }

  return {
    questionId,
    loading,
    questionDetail,
    code,
    selectedLanguage,
    cases,
    activeCase,
    activeCaseData,
    caseInput,
    caseOutput,
    isCoreMode,
    loadQuestionDetail,
    refresh,
    resetCode,
    applyTemplate,
    notifyLanguageChange,
  };
}
