<script setup lang="ts">
/**
 * 测试用例面板：用例切换 + 自定义输入 + 预期输出（只读）。
 *
 * 输入绑会话层的 `caseInput`（那段"不能 v-model 到 computed"的注释在 useSolveProblem
 * 里，务必保留 —— 否则复现「输入恒空 → 沙箱 Scanner 阻塞 → 超时」）。
 */
import { useSolveContext } from "../composables/useSolveSession";

const { cases, activeCase, caseInput, caseOutput } = useSolveContext();
</script>

<template>
  <div class="min-h-0 flex-1 overflow-y-auto p-4">
    <div v-if="cases.length" class="mb-3 flex flex-wrap items-center gap-2">
      <button
        v-for="(_, i) in cases"
        :key="i"
        type="button"
        :class="[
          'rounded-md border px-3 py-1 text-xs transition-colors',
          activeCase === i
            ? 'border-primary bg-primary/10 font-medium text-foreground'
            : 'text-muted-foreground hover:bg-muted/60',
        ]"
        @click="activeCase = i"
      >
        示例 {{ i + 1 }}
      </button>
    </div>
    <p v-else class="mb-3 text-sm text-muted-foreground">
      本题没有预置用例，可直接在下方输入测试。
    </p>

    <label class="mb-1 block text-xs font-medium text-muted-foreground">
      输入（运行时作为标准输入）
    </label>
    <textarea
      v-model="caseInput"
      rows="4"
      class="uc-test-textarea"
      placeholder="标准输入"
    ></textarea>

    <label class="mb-1 mt-3 block text-xs font-medium text-muted-foreground">
      预期输出（可编辑，留空表示仅运行不判对错）
    </label>
    <textarea
      v-model="caseOutput"
      rows="2"
      class="uc-test-textarea"
      placeholder="（无）"
    ></textarea>
  </div>
</template>

<style scoped>
/**
 * `.uc-test-textarea` 原本定义在 ViewQuestion 的作用域里，面板化后由本页自带。
 * 样式内容照搬，没有改动。
 */
.uc-test-textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--background);
  padding: 8px 10px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.5;
}
</style>
