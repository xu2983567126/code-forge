<template>
  <div class="uc-page-stack uc-solve-page">
    <!--
      三段式页头（对照 UltiCode ProblemDetailView 的 header）：
      左=出题（返回 + 题目标题 + 难度）｜中=主操作（运行 / 提交）｜右=辅助入口。
      两侧 `flex-1` 让中段真正居中（中段不参与伸缩）。
    -->
    <header
      class="flex h-12 w-full shrink-0 items-center justify-between gap-2 rounded-md border border-border bg-background px-2.5"
    >
      <div class="relative z-10 flex h-full min-w-0 flex-1 items-center overflow-hidden">
        <SolveHeaderLeft />
      </div>
      <div class="flex shrink-0 items-center">
        <SolveHeaderCenter />
      </div>
      <div class="relative z-10 flex h-full flex-1 items-center justify-end gap-1">
        <SolveHeaderControls />
      </div>
    </header>

    <div class="flex min-h-0 flex-1 flex-col">
      <div
        v-if="loading"
        class="flex flex-1 items-center justify-center text-muted-foreground"
      >
        题目加载中…
      </div>

      <!-- 窄屏：一次看一个区块（tab 与路由 :tab? 同步） -->
      <MobileSolveLayout
        v-else-if="questionDetail && isMobile"
        v-model:tab="mobileTab"
      />

      <ResizablePanelGroup
        v-else-if="questionDetail"
        direction="horizontal"
        auto-save-id="code-forge:solve-split"
        class="h-full w-full overflow-hidden rounded-md border border-border"
      >
        <!-- 左侧：题目描述 / 提交记录（提交成功后自动切到后者） -->
        <ResizablePanel
          :default-size="45"
          :min-size="25"
          class="flex min-w-0 flex-col"
        >
          <Tabs
            :model-value="activeTab"
            class="flex min-h-0 flex-1 flex-col"
            @update:model-value="onTabChange"
          >
            <div
              class="flex shrink-0 items-center border-b border-border px-4 py-1.5"
            >
              <TabsList class="h-auto gap-1 bg-transparent p-0">
                <TabsTrigger
                  value="description"
                  class="rounded-md px-3 py-1.5 text-sm data-[state=active]:bg-muted data-[state=active]:font-medium data-[state=active]:text-foreground"
                >
                  题目描述
                </TabsTrigger>
                <TabsTrigger
                  value="submissions"
                  class="rounded-md px-3 py-1.5 text-sm data-[state=active]:bg-muted data-[state=active]:font-medium data-[state=active]:text-foreground"
                >
                  提交记录
                </TabsTrigger>
              </TabsList>
            </div>

            <TabsContent value="description" class="mt-0 flex min-h-0 flex-1 flex-col">
              <DescriptionPanel />
            </TabsContent>
            <TabsContent value="submissions" class="mt-0 flex min-h-0 flex-1 flex-col">
              <SubmissionsPanel />
            </TabsContent>
          </Tabs>
        </ResizablePanel>

        <ResizableHandle with-handle />

        <!-- 右侧：代码 + 测试区域 -->
        <ResizablePanel :default-size="55" :min-size="30" class="flex min-w-0 flex-col">
          <CodePanel />

          <!-- 测试区域：测试用例 / 测试结果 -->
          <Tabs
            :model-value="testTab"
            class="flex min-h-0 flex-1 flex-col border-t border-border"
            @update:model-value="onTestTabChange"
          >
            <div
              class="flex shrink-0 items-center gap-1 border-b border-border px-4 py-1.5"
            >
              <TabsList class="h-auto gap-1 bg-transparent p-0">
                <TabsTrigger
                  value="cases"
                  class="rounded-md px-3 py-1.5 text-sm data-[state=active]:bg-muted data-[state=active]:font-medium data-[state=active]:text-foreground"
                >
                  测试用例
                </TabsTrigger>
                <TabsTrigger
                  value="results"
                  class="rounded-md px-3 py-1.5 text-sm data-[state=active]:bg-muted data-[state=active]:font-medium data-[state=active]:text-foreground"
                >
                  测试结果
                </TabsTrigger>
              </TabsList>
              <span v-if="running" class="ml-auto text-xs text-muted-foreground">
                沙箱运行中…
              </span>
            </div>

            <TabsContent value="cases" class="mt-0 flex min-h-0 flex-1 flex-col">
              <TestCasesPanel />
            </TabsContent>
            <TabsContent value="results" class="mt-0 flex min-h-0 flex-1 flex-col">
              <TestResultsPanel />
            </TabsContent>
          </Tabs>
        </ResizablePanel>
      </ResizablePanelGroup>

      <div
        v-else
        class="flex flex-1 items-center justify-center text-muted-foreground"
      >
        题目不存在或加载失败
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 做题页（P1 重构后）。
 *
 * 本文件只做三件事：调用会话 once、注册 provide、渲染布局。
 * 题目/运行/提交的状态与逻辑在 `solve/composables/`，各区块在 `solve/components/`。
 *
 * 布局策略（与上游的差异，见 docs/P1-做题页重构-方案.md 决策 B）：
 * 单一桌面布局（可拖拽分栏，宽度由 ResizablePanelGroup 持久化），不做多种布局预设；
 * 小屏退化沿用 CSS 覆盖，移动端专项布局在阶段 3 引入。
 */
import { provide } from "vue";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "@/components/ui/resizable";
import SolveHeaderLeft from "./solve/components/SolveHeaderLeft.vue";
import SolveHeaderCenter from "./solve/components/SolveHeaderCenter.vue";
import SolveHeaderControls from "./solve/components/SolveHeaderControls.vue";
import DescriptionPanel from "./solve/components/DescriptionPanel.vue";
import SubmissionsPanel from "./solve/components/SubmissionsPanel.vue";
import CodePanel from "./solve/components/CodePanel.vue";
import TestCasesPanel from "./solve/components/TestCasesPanel.vue";
import TestResultsPanel from "./solve/components/TestResultsPanel.vue";
import MobileSolveLayout from "./solve/components/MobileSolveLayout.vue";
import {
  useSolveSession,
  type SolveTab,
  type SolveTestTab,
} from "./solve/composables/useSolveSession";
import { useSolveLayout } from "./solve/composables/useSolveLayout";

const { installProviders, loading, questionDetail, activeTab, testTab, running } =
  useSolveSession();
const { isMobile, mobileTab } = useSolveLayout();

installProviders(provide);

/** Tabs 的 modelValue 是 string，会话里收窄成了联合类型，故在此收口。 */
function onTabChange(value: string | number | undefined) {
  activeTab.value = (value as SolveTab) ?? "description";
}

function onTestTabChange(value: string | number | undefined) {
  testTab.value = (value as SolveTestTab) ?? "cases";
}
</script>

<style scoped>
/**
 * 做题页定高：页头 shrink-0，分栏区 flex-1 min-h-0 由 flex 链决定高度，
 * 不再用 calc 硬算 —— 页头行高变化时自动跟随。
 * 小屏（<1024px）退化逻辑与 L3 一致：面板组转 column、隐藏把手；
 * reka 把 display/flex-direction/width 写成 inline style，覆盖必须 !important。
 */
.uc-solve-page {
  height: calc(100dvh - 3.5rem - var(--uc-layout-page-gutter) * 2 - 2px);
  min-height: 34rem;
}
</style>
