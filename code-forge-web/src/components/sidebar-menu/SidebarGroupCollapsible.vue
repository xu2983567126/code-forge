<script setup lang="ts">
import type { Component } from 'vue'
import { CollapsibleRoot, type CollapsibleRootProps } from 'reka-ui'
import { cn } from '@/lib/utils'

// 仅非受控模式 —— 与 SidebarParentItem 同理：绑定 `:open` 会让 reka 把
// CollapsibleRoot 当作受控组件，而 `:open="undefined"` 会让它受控-关闭，
// 导致 CollapsibleContent 永不渲染（上游 fc266ce10 回归）。
// 因此只转发 `defaultOpen` / `disabled`；`as` / `asChild` 有意不转发
// （CollapsibleRoot 必须保持为根），prop 类型据此收窄。
type ForwardedCollapsibleProps = Pick<CollapsibleRootProps, 'defaultOpen' | 'disabled'>

const props = withDefaults(
  defineProps<
    ForwardedCollapsibleProps & {
      /** 可选的分组标题，渲染在 label 行。 */
      title?: string
      /** 标题的可选前置图标组件。 */
      icon?: Component
      /** 用强调色渲染 label（通过 [data-active]）。 */
      active?: boolean
      /** 附加到 label 行的 class。 */
      labelClass?: string
    }
  >(),
  { defaultOpen: true, active: false },
)
</script>

<template>
  <CollapsibleRoot
    v-slot="{ open }"
    :default-open="props.defaultOpen"
    :disabled="props.disabled"
    data-slot="collapsible"
    class="group/collapsible"
  >
    <div
      v-if="title"
      :data-active="active ? 'true' : 'false'"
      :class="cn('uc-sidebar-group-label flex items-center gap-1.5', labelClass)"
    >
      <component :is="icon" v-if="icon" class="size-3.5 shrink-0" />
      <span>{{ title }}</span>
    </div>
    <slot :open="open" />
  </CollapsibleRoot>
</template>
