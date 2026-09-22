<script setup lang="ts">
import Label from "@/components/ui/label/Label.vue";

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
      <span v-if="required" class="text-status-error">*</span>
    </Label>
    <slot />
    <p v-if="error" class="text-sm text-status-error">{{ error }}</p>
  </div>
</template>
