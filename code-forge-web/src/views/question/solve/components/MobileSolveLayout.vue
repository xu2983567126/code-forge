<script setup lang="ts">
/**
 * 移动端做题布局（<768px）。
 *
 * 桌面端是「描述 + 代码 + 测试」同屏分栏；窄屏放不下，改为一次看一个区块，
 * 用 tab 切换（对照 UltiCode 的 MobileProblemLayout）。
 *
 * 两处与上游的差异：
 * - 上游还要按竞赛态增删 tab（隐藏题解），我方无竞赛域，tab 固定五项；
 * - 上游窄屏用 Select 下拉、≥sm 用横向 TabsList。我方移动端整体就是窄屏场景，
 *   统一用横向滚动的 TabsList（选项少、切换成本低），不留两套切换控件。
 *
 * 内容区只渲染当前 tab 对应的面板（用 v-if，不是 CSS 隐藏）—— 未激活的面板不做
 * 请求、不初始化 Monaco，窄屏下这一步直接省掉了大部分开销。
 */
import { History, FileText, Code2 } from "lucide-vue-next";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import DescriptionPanel from "./DescriptionPanel.vue";
import CodePanel from "./CodePanel.vue";
import TestCasesPanel from "./TestCasesPanel.vue";
import TestResultsPanel from "./TestResultsPanel.vue";
import SubmissionsPanel from "./SubmissionsPanel.vue";
import { useSolveContext } from "../composables/useSolveSession";
import type { SolveMobileTab } from "../composables/useSolveLayout";

const props = defineProps<{
  tab: SolveMobileTab;
}>();

const emit = defineEmits<{
  (e: "update:tab", value: SolveMobileTab): void;
}>();

const { activeTab, testTab } = useSolveContext();

/**
 * 移动端 tab 与桌面端的两个 tab 是同一份内容的两套视图，切换时把状态对齐：
 * - description / submissions → 左侧 activeTab
 * - cases / results → 右侧 testTab
 * 这样从窄屏拉宽回桌面时看到的是同一个位置，不会跳回默认值。
 */
function onChange(value: string | number | undefined) {
  const next = (value as SolveMobileTab) ?? "description";
  if (next === "description" || next === "submissions") {
    activeTab.value = next;
  }
  // 提交记录里已选中的那条保持选中（提交成功后会自动带着 id 切过来）
  if (next === "cases" || next === "results") {
    testTab.value = next;
  }
  emit("update:tab", next);
}
</script>

<template>
  <Tabs
    :model-value="props.tab"
    class="flex h-full min-h-0 flex-col"
    @update:model-value="onChange"
  >
    <div class="shrink-0 border-b border-border">
      <TabsList class="w-full justify-start gap-1 overflow-x-auto p-1">
        <TabsTrigger value="description" class="flex items-center gap-1.5 px-3 py-1.5 text-xs">
          <FileText class="h-3.5 w-3.5" />
          <span>题目</span>
        </TabsTrigger>
        <TabsTrigger value="code" class="flex items-center gap-1.5 px-3 py-1.5 text-xs">
          <Code2 class="h-3.5 w-3.5" />
          <span>代码</span>
        </TabsTrigger>
        <TabsTrigger value="cases" class="px-3 py-1.5 text-xs">测试用例</TabsTrigger>
        <TabsTrigger value="results" class="px-3 py-1.5 text-xs">测试结果</TabsTrigger>
        <TabsTrigger value="submissions" class="flex items-center gap-1.5 px-3 py-1.5 text-xs">
          <History class="h-3.5 w-3.5" />
          <span>提交记录</span>
        </TabsTrigger>
      </TabsList>
    </div>

    <!--
      必须是 flex column：CodePanel 用 `flex-[1.15]` 参与分配高度，父级不是 flex 容器时
      该 flex 属性失效，编辑器高度退化为 auto —— 而 Monaco 的宿主是 `h-full`，
      没有确定高度的祖先就画不出来（代码区全空白）。
    -->
    <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
      <DescriptionPanel v-if="props.tab === 'description'" />
      <CodePanel v-else-if="props.tab === 'code'" />
      <TestCasesPanel v-else-if="props.tab === 'cases'" />
      <TestResultsPanel v-else-if="props.tab === 'results'" />
      <SubmissionsPanel v-else-if="props.tab === 'submissions'" />
    </div>
  </Tabs>
</template>
