<script setup lang="ts">
/**
 * 题目描述面板。
 *
 * 从原 `ViewQuestion.vue` 左侧抽出：标题 + 标签 + 判题条件 + 示例用例 + Markdown 正文。
 * 用例来自 VO 的脱敏样例 `examples`（前 N 条），由会话层解析成本地副本。
 */
import { Badge } from "@/components/ui/badge";
import MdViewer from "@/components/MdViewer.vue";
import { useSolveContext } from "../composables/useSolveSession";

const { questionDetail, cases } = useSolveContext();
</script>

<template>
  <div class="min-h-0 flex-1 overflow-y-auto p-6">
    <div class="mb-5 border-b border-border pb-4">
      <h2 class="text-2xl font-semibold">
        {{ questionDetail?.title || "无标题" }}
      </h2>
      <div
        v-if="questionDetail?.tags && questionDetail.tags.length"
        class="mt-2 flex flex-wrap gap-2"
      >
        <Badge
          v-for="tag in questionDetail.tags"
          :key="tag"
          variant="secondary"
        >
          {{ tag }}
        </Badge>
      </div>
    </div>

    <div class="mb-5 rounded-lg bg-muted p-4">
      <p class="mb-2 text-sm font-medium text-muted-foreground">判题条件</p>
      <div class="grid grid-cols-3 gap-4 text-sm">
        <div>
          <span class="text-muted-foreground">时间限制</span>
          <p>{{ questionDetail?.judgeConfig?.timeLimit || 1000 }}ms</p>
        </div>
        <div>
          <span class="text-muted-foreground">内存限制</span>
          <p>{{ questionDetail?.judgeConfig?.memoryLimit || 128 }}KB</p>
        </div>
        <div>
          <span class="text-muted-foreground">堆栈限制</span>
          <p>{{ questionDetail?.judgeConfig?.stackLimit || 128 }}KB</p>
        </div>
      </div>
    </div>

    <!-- 示例用例（左侧强调边条卡片，对照 UltiCode） -->
    <div v-if="cases.length" class="mb-5 space-y-3">
      <div
        v-for="(c, i) in cases"
        :key="i"
        class="rounded-md border border-l-[3px] border-l-primary bg-muted/40 p-4"
      >
        <p class="mb-2 text-sm font-semibold">示例 {{ i + 1 }}</p>
        <div class="space-y-1.5 text-sm">
          <p>
            <span class="mr-2 font-semibold text-muted-foreground">输入</span>
            <code class="rounded bg-background/60 px-1.5 py-0.5">{{ c.input }}</code>
          </p>
          <p>
            <span class="mr-2 font-semibold text-muted-foreground">预期输出</span>
            <code class="rounded bg-background/60 px-1.5 py-0.5">{{ c.expectedOutput }}</code>
          </p>
        </div>
      </div>
    </div>

    <div class="markdown-block min-h-[200px]">
      <MdViewer :content="questionDetail?.content || ''" />
    </div>
  </div>
</template>
