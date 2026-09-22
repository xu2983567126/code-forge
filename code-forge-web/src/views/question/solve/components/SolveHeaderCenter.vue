<script setup lang="ts">
/**
 * 做题页 · 三段式页头中段：运行 / 提交。
 *
 * 两个主操作居中（上游同构），并绑定快捷键：F5 运行、Ctrl/Cmd+Enter 提交。
 * 提示走 `title` 属性（上游用 HoverCard 展示快捷键，我方没有 i18n 与 HoverCard 使用场景，
 * 用原生 title 同样可达且更轻）。
 */
import { onMounted, onUnmounted } from "vue";
import { Play, CloudUpload } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { useSolveContext } from "../composables/useSolveSession";

const { running, submitting, handleRun, handleSubmit } = useSolveContext();

/**
 * 快捷键：F5 运行、Ctrl/Cmd+Enter 提交。
 * F5 默认是浏览器刷新，必须 preventDefault；编辑器内的按键也会冒泡到 window，
 * 因此这里只认这两个组合，其余放行。
 */
function onKeydown(event: KeyboardEvent) {
  if (event.key === "F5") {
    event.preventDefault();
    if (!running.value && !submitting.value) void handleRun();
    return;
  }
  if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
    event.preventDefault();
    if (!submitting.value) void handleSubmit();
  }
}

onMounted(() => window.addEventListener("keydown", onKeydown));
onUnmounted(() => window.removeEventListener("keydown", onKeydown));
</script>

<template>
  <div class="flex items-center gap-2">
    <Button
      variant="outline"
      size="sm"
      :disabled="running || submitting"
      title="运行代码（F5）"
      @click="handleRun"
    >
      <Play
        class="mr-1.5 size-4"
        :class="running ? 'animate-[spin_0.9s_linear]' : ''"
      />
      {{ running ? "运行中…" : "运行代码" }}
    </Button>
    <Button
      size="sm"
      :disabled="running || submitting"
      title="提交代码（Ctrl/Cmd + Enter）"
      @click="handleSubmit"
    >
      <CloudUpload class="mr-1.5 size-4" />
      {{ submitting ? "提交中…" : "提交代码" }}
    </Button>
  </div>
</template>
