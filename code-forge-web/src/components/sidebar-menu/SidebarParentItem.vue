<script setup lang="ts">
import type { Component } from 'vue'
import { CollapsibleRoot, CollapsibleTrigger, CollapsibleContent } from 'reka-ui'
import { cn } from '@/lib/utils'

/**
 * SidebarParentItem —— 既是链接、又是子项折叠容器的父行。
 *
 * - 模式 A（提供 `url`）：标题是 `router-link`（点击即跳转），另有独立的
 *   箭头按钮负责折叠。对应 console 的「父项 = 链接 + 可折叠子项」。
 * - 模式 B（无 `url`）：标题行本身即折叠触发器。对应 management 的
 *   「父项 = 纯分组（不跳转）」。
 *
 * 仅非受控模式 —— open 状态由 `defaultOpen` 在挂载时种入，之后交给 reka 内部
 * 管理。有意不提供 `v-model:open`：绑定 `:open="undefined"` 会让 reka 把
 * CollapsibleRoot 视作受控-关闭（见上游 fc266ce10）。若需「路由驱动自动展开」，
 * 请改用 `SidebarGroupCollapsible`（它会有条件地转发 `open`）。
 *
 * 安全：`url` 必须是可信的内部路由字符串，**不要**直接绑定用户输入
 * —— vue-router 不拦截 `javascript:` URL。
 */
const props = withDefaults(
  defineProps<{
    title: string
    url?: string
    /** 前置图标组件（console 用 lucide，management 用 tabler）。 */
    icon?: Component
    /** 附加到前置图标的 class（例如激活色）。 */
    iconClass?: string
    active?: boolean
    defaultOpen?: boolean
    class?: string
  }>(),
  { active: false, defaultOpen: true },
)

const rowBase =
  'uc-sidebar-item group flex flex-1 cursor-pointer items-center gap-2.5 rounded-md mx-1 h-9 pl-2.5 pr-3 py-1.5 text-sm font-medium transition-all duration-200 select-none'
</script>

<template>
  <CollapsibleRoot
    :default-open="defaultOpen"
    data-slot="collapsible"
    class="group/collapsible"
    v-slot="{ open: isOpen }"
  >
    <div :class="cn('group flex items-center', props.class)">
      <!-- 模式 A：标题负责跳转；箭头是独立的折叠触发器。 -->
      <template v-if="url">
        <component
          :is="'router-link'"
          :to="url"
          :data-active="active ? 'true' : 'false'"
          :class="rowBase"
        >
          <component :is="icon" v-if="icon" :class="cn('size-4 shrink-0 transition-colors', iconClass)" />
          <span class="flex-1 truncate">{{ title }}</span>
        </component>
        <CollapsibleTrigger as-child>
          <button
            type="button"
            class="mr-1 flex size-7 shrink-0 items-center justify-center rounded-md text-[var(--foreground-muted)] hover:bg-[var(--border-subtle)]/40 hover:text-[var(--foreground)] min-h-11 min-w-11 sm:min-h-7 sm:min-w-7"
            :aria-label="isOpen ? 'collapse section' : 'expand section'"
          >
            <slot name="chevron" :open="isOpen">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                :class="cn('size-4 transition-transform duration-200', isOpen && 'rotate-90')"
              >
                <path d="m9 18 6-6-6-6" />
              </svg>
            </slot>
          </button>
        </CollapsibleTrigger>
      </template>
      <!-- 模式 B：标题行本身即折叠触发器。 -->
      <CollapsibleTrigger v-else as-child>
        <button
          type="button"
          :data-active="active ? 'true' : 'false'"
          :class="rowBase"
          :aria-label="isOpen ? 'collapse section' : 'expand section'"
        >
          <component :is="icon" v-if="icon" :class="cn('size-4 shrink-0 transition-colors', iconClass)" />
          <span class="flex-1 truncate text-left">{{ title }}</span>
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            :class="cn('ml-auto size-4 shrink-0 text-[var(--foreground-muted)] transition-transform duration-200', isOpen && 'rotate-90')"
          >
            <path d="m9 18 6-6-6-6" />
          </svg>
        </button>
      </CollapsibleTrigger>
    </div>
    <CollapsibleContent>
      <slot :open="isOpen" />
    </CollapsibleContent>
  </CollapsibleRoot>
</template>
