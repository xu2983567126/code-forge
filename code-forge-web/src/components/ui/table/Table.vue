<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { cn } from "@/lib/utils";

const props = defineProps<{
  class?: HTMLAttributes["class"];
  /**
   * 外层容器的额外类。
   * 虚拟滚动必须传 `overflow-visible`：虚拟行是 `position: absolute`，会让 `tbody`
   * 高度塌陷成 0，默认的 `overflow-auto` 会把整片行裁掉（DOM 在、屏幕上看不见）。
   */
  containerClass?: HTMLAttributes["class"];
}>();
</script>

<template>
  <div
    data-slot="table-container"
    :class="cn('relative w-full overflow-auto', props.containerClass)"
  >
    <table
      data-slot="table"
      :class="cn('w-full caption-bottom text-sm', props.class)"
    >
      <slot />
    </table>
  </div>
</template>
