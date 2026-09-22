<script setup lang="ts">
/**
 * 做题页 · 三段式页头左段：返回 + 题目标题 + 难度。
 *
 * 上游 `LayoutHeaderLeft` 在这里放 logo 与「题库」抽屉（它做题页是全屏，要自带导航）。
 * 我方做题页保留在应用布局内，所以左段只需给出路（返回题目列表）与当前题目标识。
 */
import { computed } from "vue";
import { useRouter } from "vue-router";
import { ArrowLeft } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { getDifficultyBadgeClass } from "@/lib/design-system/variants";
import { useSolveContext } from "../composables/useSolveSession";

const router = useRouter();
const { questionDetail, loading } = useSolveContext();

const title = computed(() => {
  if (questionDetail.value?.title) return questionDetail.value.title;
  return loading.value ? "题目加载中…" : "题目";
});
const difficulty = computed(() => questionDetail.value?.difficulty || "");
</script>

<template>
  <div class="flex min-w-0 items-center gap-2">
    <Button
      variant="ghost"
      size="icon"
      title="返回题目列表"
      @click="router.push('/questions')"
    >
      <ArrowLeft class="h-4 w-4" />
    </Button>
    <span class="h-4 w-px shrink-0 bg-border"></span>
    <h1 class="truncate text-sm font-semibold text-foreground-strong">
      {{ title }}
    </h1>
    <span
      v-if="difficulty"
      class="inline-flex shrink-0 items-center rounded border px-2 py-0.5 text-xxs"
      :class="getDifficultyBadgeClass(difficulty)"
    >
      {{ difficulty }}
    </span>
  </div>
</template>
