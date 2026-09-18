<script setup lang="ts">
defineProps<{
  label: string
  required?: boolean
  error?: string
  htmlFor?: string
}>()
</script>

<template>
  <div class="space-y-2">
    <Label :for="htmlFor">
      {{ label }}
      <span v-if="required" class="text-red-600">*</span>
    </Label>
    <slot />
    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
  </div>
</template>
