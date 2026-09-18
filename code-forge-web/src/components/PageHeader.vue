<script setup lang="ts">
/**
 * 页面标题区（L2）。
 *
 * 统一各页顶部「标题 + 描述 + 右侧操作」的结构与间距。
 * 间距统一走 `--uc-layout-control-gap`，与顶栏、侧边栏共用同一套度量。
 *
 * 用法：
 *   <PageHeader title="题目列表" description="浏览所有题目信息">
 *     <template #actions><Button>创建题目</Button></template>
 *   </PageHeader>
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div
    class="flex flex-wrap items-start justify-between gap-[var(--uc-layout-control-gap)]"
  >
    <div class="min-w-0">
      <h1 class="text-xl font-semibold text-foreground-strong">{{ title }}</h1>
      <p v-if="description" class="mt-1 text-sm text-muted-foreground">
        {{ description }}
      </p>
    </div>
    <div
      v-if="$slots.actions"
      class="flex shrink-0 flex-wrap items-center gap-2"
    >
      <slot name="actions" />
    </div>
  </div>
</template>
