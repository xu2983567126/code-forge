<script setup lang="ts">
/**
 * 主题切换器（L5）。
 *
 * 三态（浅色 / 深色 / 跟随系统）而非二态开关：做题页会长时间停留，
 * "跟随系统"是多数人的默认期望，且切换系统外观时页面要实时响应 —— 这由
 * `useTheme` 内部的 `matchMedia` 监听保证。
 *
 * 触发器只显示**当前模式**的图标；生效主题与模式不同的情况（如 system 模式
 * 下系统为暗色）由 title 文案说明，避免用户误判"点了没反应"。
 */
import { computed } from 'vue'
import { Check, Monitor, Moon, Sun } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { useTheme } from '@/lib/theme/useTheme'
import type { ThemeMode } from '@/lib/theme'

const { mode, resolved, setMode } = useTheme()

interface ThemeOption {
  value: ThemeMode
  label: string
  icon: typeof Sun
}

const OPTIONS: ThemeOption[] = [
  { value: 'light', label: '浅色', icon: Sun },
  { value: 'dark', label: '深色', icon: Moon },
  { value: 'system', label: '跟随系统', icon: Monitor }
]

const activeOption = computed(
  () => OPTIONS.find((option) => option.value === mode.value) ?? OPTIONS[2]
)

const triggerTitle = computed(() =>
  mode.value === 'system'
    ? `主题：跟随系统（当前${resolved.value === 'dark' ? '深色' : '浅色'}）`
    : `主题：${activeOption.value.label}`
)
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="ghost" size="icon-sm" :title="triggerTitle" aria-label="切换主题">
        <component :is="activeOption.icon" class="size-4" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="end" :side-offset="4" class="w-36">
      <DropdownMenuLabel>主题</DropdownMenuLabel>
      <DropdownMenuSeparator />
      <DropdownMenuItem
        v-for="option in OPTIONS"
        :key="option.value"
        class="cursor-pointer"
        @click="setMode(option.value)"
      >
        <component :is="option.icon" class="mr-2 h-4 w-4" />
        {{ option.label }}
        <Check v-if="mode === option.value" class="ml-auto size-4 text-foreground-muted" />
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
