<template>
  <div class="mb-4 flex flex-wrap items-center gap-3 rounded-lg border bg-card p-4">
    <slot />
    <div class="ml-auto flex gap-2">
      <Button @click="$emit('search')">
        <Search class="size-4" />
        搜索
      </Button>
      <Button variant="outline" @click="$emit('reset')">
        <RotateCcw class="size-4" />
        重置
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search, RotateCcw } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'

defineEmits<{
  search: []
  reset: []
}>()
</script>
