<script setup lang="ts">
import { ref } from 'vue'
import { X } from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'

const model = defineModel<string[]>({ default: () => [] })
const draft = ref('')

function commit() {
  const v = draft.value.trim()
  if (v && !model.value.includes(v)) model.value.push(v)
  draft.value = ''
}

function remove(i: number) {
  model.value.splice(i, 1)
}
</script>

<template>
  <div class="flex min-h-9 flex-wrap items-center gap-2 rounded-md border px-3 py-0.5">
    <Badge v-for="(tag, i) in model" :key="tag" variant="secondary" class="gap-1">
      {{ tag }}
      <button type="button" @click="remove(i)"><X class="size-3" /></button>
    </Badge>
    <Input
      v-model="draft"
      class="h-7 w-auto min-w-24 flex-1 border-0 shadow-none focus-visible:ring-0"
      placeholder="输入标签后回车"
      @keydown.enter.prevent="commit"
      @keydown.delete="!draft && model.length && remove(model.length - 1)"
    />
  </div>
</template>
