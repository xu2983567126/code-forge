<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

// 根元素显式绑定 $attrs；关闭自动透传，避免 attrs（to、事件等）被重复应用。
defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    isActive?: boolean
    as?: 'link' | 'button' | 'a'
    class?: string
    /** 行末的可选徽标（数量/文本）。 */
    badge?: string | number
    /** 徽标色调。 */
    badgeVariant?: 'default' | 'accent' | 'muted'
    /** 附加到前置 `#icon` 插槽容器上的 class。 */
    iconClass?: string
    /** 为 true 时渲染尾部折叠箭头并 emit `toggle`。 */
    showChevron?: boolean
  }>(),
  { as: 'link', badgeVariant: 'default', showChevron: false },
)

const emit = defineEmits<{
  toggle: []
}>()

const tag = computed(() => {
  if (props.as === 'link') return 'router-link'
  if (props.as === 'a') return 'a'
  return 'button'
})

const badgeClass = computed(() => {
  switch (props.badgeVariant) {
    case 'accent':
      return 'bg-[var(--primary)]/15 text-[var(--primary)]'
    case 'muted':
      return 'bg-[var(--border-subtle)]/50 text-[var(--foreground-strong)] dark:text-[var(--foreground-muted)]'
    default:
      return 'bg-[var(--border-subtle)]/60 text-foreground dark:text-[var(--foreground-muted)]'
  }
})

// 激活视觉由 [data-active] + `.uc-sidebar-item` CSS 契约驱动（单一事实来源），
// 组件本身不再手写 `border-l-4 border-[--primary]`。
const mergedClass = computed(() =>
  cn(
    'uc-sidebar-item group flex items-center gap-2.5 pl-2.5 pr-3 py-1.5 transition-all duration-200 select-none text-sm font-medium h-9 mx-1 rounded-md',
    props.class,
  ),
)

function onChevronClick(e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  emit('toggle')
}
</script>

<template>
  <component
    :is="tag"
    :class="mergedClass"
    :data-active="isActive ? 'true' : 'false'"
    v-bind="$attrs"
  >
    <span v-if="$slots.icon" :class="cn('flex shrink-0 items-center', iconClass)">
      <slot name="icon" />
    </span>
    <slot />
    <span
      v-if="badge !== undefined && badge !== null"
      :class="cn('ml-auto inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium tabular-nums', badgeClass)"
    >
      {{ badge }}
    </span>
    <button
      v-if="showChevron"
      type="button"
      class="ml-auto flex size-4 shrink-0 items-center text-[var(--foreground-muted)] hover:text-[var(--foreground)]"
      aria-label="toggle section"
      @click="onChevronClick"
    >
      <slot name="chevron">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="size-4 transition-transform duration-200"
        >
          <path d="m9 18 6-6-6-6" />
        </svg>
      </slot>
    </button>
  </component>
</template>
