<script setup lang="ts">
/**
 * 做题页 · 三段式页头右段：提交记录 / 重置代码 / 刷新。
 *
 * 上游右段是**布局预设切换器**（leet/classic/compact/wide）。我方只做单一桌面布局
 * （可拖拽分栏已够用），因此右段放实操入口：跳转到提交记录、加入题单、恢复初始代码、重载题目。
 */
import { computed, ref } from "vue";
import { History, ListPlus, RotateCcw, RefreshCw } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { useSolveContext } from "../composables/useSolveSession";
import BankPickerDialog from "../../components/BankPickerDialog.vue";

const { goToSubmissions, resetCode, refresh, loading, questionDetail } =
  useSolveContext();

const pickerOpen = ref(false);
/**
 * 题目 id 是雪花（19 位），必须保持字符串 —— 交给 BankPickerDialog 后直接进
 * `questionIdList`，一旦转 number 后端就按被舍入的 id 查不到题目。
 */
const currentQuestionIds = computed(() =>
  questionDetail.value?.id ? [questionDetail.value.id] : [],
);
</script>

<template>
  <div class="flex items-center gap-1">
    <Button
      variant="ghost"
      size="icon"
      title="加入题单"
      :disabled="!currentQuestionIds.length"
      @click="pickerOpen = true"
    >
      <ListPlus class="h-4 w-4" />
    </Button>
    <Button
      variant="ghost"
      size="icon"
      title="我的提交记录"
      @click="goToSubmissions"
    >
      <History class="h-4 w-4" />
    </Button>
    <Button variant="ghost" size="icon" title="恢复为初始代码" @click="resetCode">
      <RotateCcw class="h-4 w-4" />
    </Button>
    <Button
      variant="ghost"
      size="icon"
      title="刷新题目"
      :disabled="loading"
      @click="refresh"
    >
      <RefreshCw class="h-4 w-4" :class="loading ? 'animate-[spin_0.9s_linear]' : ''" />
    </Button>

    <BankPickerDialog v-model:open="pickerOpen" :question-ids="currentQuestionIds" />
  </div>
</template>
