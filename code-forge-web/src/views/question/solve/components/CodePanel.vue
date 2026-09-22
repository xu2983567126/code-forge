<script setup lang="ts">
/**
 * 代码面板：语言选择 + 模板库入口 + Monaco 编辑器。
 *
 * 状态（code / selectedLanguage）都在会话层，编辑器只做双向绑定。
 * 模板库仅在非核心代码模式开放 —— 核心模式下骨架由题级 codeTemplate 决定，
 * 插入语言级模板反而会破坏方法签名。
 */
import { ref } from "vue";
import CodeEditor from "@/components/CodeEditor.vue";
import CodeTemplatesDialog from "@/components/editor/CodeTemplatesDialog.vue";
import { Button } from "@/components/ui/button";
import { LANGUAGE_OPTIONS } from "@/constants/language";
import { useSolveContext } from "../composables/useSolveSession";

const {
  code,
  selectedLanguage,
  isCoreMode,
  applyTemplate,
  notifyLanguageChange,
} = useSolveContext();

const showTemplates = ref(false);
</script>

<template>
  <div class="min-h-0 flex-[1.15] p-4 pb-2">
    <div class="flex h-full min-h-0 flex-col gap-2">
      <div class="flex shrink-0 items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <h3 class="text-sm font-semibold">代码</h3>
          <Button
            v-if="!isCoreMode"
            variant="ghost"
            size="sm"
            @click="showTemplates = true"
          >
            模板库
          </Button>
        </div>
        <select
          v-model="selectedLanguage"
          class="h-8 rounded-md border border-input bg-background px-3 text-sm"
          @change="notifyLanguageChange"
        >
          <option
            v-for="opt in LANGUAGE_OPTIONS"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </option>
        </select>
      </div>

      <div class="min-h-0 flex-1 overflow-hidden rounded-md border border-border">
        <CodeEditor v-model="code" :language="selectedLanguage" :minimap="false" />
      </div>
    </div>

    <CodeTemplatesDialog
      v-model:open="showTemplates"
      :language="selectedLanguage"
      @select="applyTemplate"
    />
  </div>
</template>
