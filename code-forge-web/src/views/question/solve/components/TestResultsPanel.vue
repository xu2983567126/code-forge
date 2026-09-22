<script setup lang="ts">
/**
 * 测试结果面板：试运行的聚合结论、逐用例明细（输入/实际/期望并排）、耗时内存与错误诊断。
 *
 * 结论与逐用例 status 的文案/配色推导交给 `judgeResultEntry`；编译/系统错误的 stderr 走
 * `detail`。逐用例数据来自后端 JudgeInfo.caseResults —— 每个用例对象自带 input / output /
 * expectedOutput，前端不再按 index 与前端 cases 对齐（消除顺序耦合）。
 */
import { useSolveContext } from "../composables/useSolveSession";
import { judgeColorClass, judgeResultEntry } from "@/constants/judge";

const { running, runResult, caseResults, verdictText, verdictDetail, verdictClass } =
  useSolveContext();
</script>

<template>
  <div class="min-h-0 flex-1 overflow-y-auto p-4">
    <div v-if="!runResult && !running" class="text-sm text-muted-foreground">
      点击页头「运行代码」查看结果（F5）。
    </div>
    <div v-else-if="running" class="text-sm text-muted-foreground">
      沙箱运行中，请稍候…
    </div>
    <div v-else-if="runResult" class="space-y-3 text-sm">
      <p class="flex items-center gap-2">
        <span class="font-medium text-muted-foreground">判定</span>
        <span
          class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
          :class="verdictClass"
        >
          {{ verdictText }}
        </span>
      </p>
      <p
        v-if="verdictDetail"
        class="whitespace-pre-wrap text-xs text-status-error-mark"
      >
        {{ verdictDetail }}
      </p>

      <!-- 逐用例明细：与正式提交同源，后端已判定 status -->
      <div v-if="caseResults.length" class="space-y-3">
        <div
          v-for="(cr, i) in caseResults"
          :key="i"
          class="rounded-md border border-border p-3"
        >
          <div class="mb-2 flex items-center justify-between">
            <span class="text-xs font-medium text-muted-foreground">用例 {{ i + 1 }}</span>
            <span
              class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
              :class="
                judgeColorClass(cr.status ? judgeResultEntry(cr.status).color : 'blue')
              "
            >
              {{ cr.status ? judgeResultEntry(cr.status).text : "—" }}
            </span>
          </div>

          <p class="mb-1 text-xs font-medium text-muted-foreground">输入</p>
          <pre
            class="whitespace-pre-wrap rounded-md border border-border bg-muted/40 p-2 font-mono text-xs"
            >{{ cr.input ?? "（无）" }}</pre
          >

          <p class="mb-1 mt-2 text-xs font-medium text-muted-foreground">你的输出</p>
          <pre
            class="whitespace-pre-wrap rounded-md border border-border bg-muted/40 p-2 font-mono text-xs"
            >{{ cr.output ?? "（无输出）" }}</pre
          >

          <p class="mb-1 mt-2 text-xs font-medium text-muted-foreground">预期输出</p>
          <pre
            class="whitespace-pre-wrap rounded-md border border-border bg-muted/40 p-2 font-mono text-xs"
            >{{ cr.expectedOutput ?? "（无）" }}</pre
          >

          <p class="mt-2 text-xs text-muted-foreground">
            耗时 {{ cr.time ?? "-" }}ms · 内存 {{ cr.memory ?? "-" }}KB
          </p>
          <p
            v-if="cr.errorMessage"
            class="mt-1 whitespace-pre-wrap text-xs text-status-error-mark"
          >
            {{ cr.errorMessage }}
          </p>
        </div>
      </div>

      <!-- 编译/系统错误等无逐用例明细时，仅展示聚合结论 -->
      <p v-else class="text-xs text-muted-foreground">
        耗时 {{ runResult.time ?? "-" }}ms · 内存 {{ runResult.memory ?? "-" }}KB
      </p>
    </div>
  </div>
</template>
