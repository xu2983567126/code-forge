<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

// 根元素显式绑定 $attrs（下方 v-bind="$attrs"），让 `to` / 事件能到达渲染出的
// <a> / router-link；关闭自动透传避免重复应用。`class` 是声明的 prop，不是 attr。
defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    isActive?: boolean
    size?: 'sm' | 'md'
    class?: string
    badge?: string | number
    iconClass?: string
  }>(),
  { size: 'md' },
)

// 激活 + hover 由 [data-active] + `.uc-sidebar-sub-item` CSS 契约驱动，
// 与顶层 item 共用同一套契约。
const mergedClass = computed(() =>
  cn(
    'uc-sidebar-sub-item text-sidebar-foreground ring-sidebar-ring flex h-7 min-w-0 -translate-x-px items-center gap-2 overflow-hidden px-2 outline-hidden focus-visible:ring-2 disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50 [&>span:last-child]:truncate [&>svg]:size-4 [&>svg]:shrink-0',
    'group-data-[collapsible=icon]:hidden',
    'h-8 transition-all duration-200 rounded-md',
    props.class,
  ),
)
</script>

<template>
  <component
    :is="$attrs.to ? 'router-link' : 'a'"
    data-slot="sidebar-menu-sub-button"
    :data-size="size"
    :data-active="isActive ? 'true' : 'false'"
    :class="mergedClass"
    v-bind="$attrs"
  >
    <span v-if="$slots.icon" :class="cn('flex shrink-0 items-center', iconClass)">
      <slot name="icon" />
    </span>
    <slot />
    <span
      v-if="badge !== undefined && badge !== null"
      class="ml-auto inline-flex items-center rounded-full bg-[var(--border-subtle)]/60 px-2 py-0.5 text-xs font-medium tabular-nums text-foreground dark:text-[var(--foreground-muted)]"
    >
      {{ badge }}
    </span>
  </component>
</template>
