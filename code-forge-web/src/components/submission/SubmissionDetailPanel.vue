<script setup lang="ts">
/**
 * 提交详情主体（共享）。
 *
 * 独立路由页（`/submissions/:id`）与做题页内的「提交记录」面板共用同一个实现 ——
 * 搬运提交详情进题目上下文时，两处必须是同一份渲染与同一套刷新策略，否则一边有
 * 轮询、另一边是死数据。
 *
 * 与旧详情页的两点差异：
 * 1. **判题中自动轮询**：旧实现只在挂载时拉一次，判题中是死数据；这里在状态为
 *    「等待中 / 判题中」时按固定间隔重新拉取，直到进入终态。
 * 2. 只负责内容，不带 PageHeader —— 页面标题与返回按钮由宿主决定。
 */
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { getSubmissionVoById } from "@generated";
import type { SubmissionVo } from "@generated";
import CodeEditor from "@/components/CodeEditor.vue";
import { Card, CardContent } from "@/components/ui/card";
import { formatTime } from "@/utils/format";
import {
  JUDGE_RESULT,
  JUDGE_STATUS,
  formatStatus,
  formatJudgeInfo,
  judgeColorClass,
} from "@/constants/judge";

const props = withDefaults(
  defineProps<{
    submissionId: string;
    /**
     * 是否展示「题目」字段。做题页内的提交面板上下文就是本题，再显示一次是冗余；
     * 独立路由页（从提交记录列表进来）必须显示，否则不知道这是哪道题。
     */
    showQuestion?: boolean;
  }>(),
  { showQuestion: true }
);

/** 判题中的轮询间隔。判题单题耗时在秒级，2s 足够跟得上，又不会打爆后端。 */
const POLL_INTERVAL_MS = 2000;

const loading = ref(false);
const submission = ref<SubmissionVo | null>(null);
const errorText = ref("");
const errorDetail = ref("");

const caseResults = computed(() => submission.value?.judgeInfo?.caseResults ?? []);

/** 轮询只在非终态时进行（等待中 / 判题中）。 */
const isJudging = computed(() => {
  const status = submission.value?.status;
  return (
    status === JUDGE_STATUS.PENDING.value || status === JUDGE_STATUS.JUDGING.value
  );
});

function openQuestion(questionId: string) {
  window.open(`/questions/${questionId}/view`);
}

/** caseResults 的 status 是 VerdictEnum.code（ACCEPTED / WRONG_ANSWER…），直接按 code 取 JUDGE_RESULT 配色。 */
const caseClass = (status?: string) => {
  const entry = status ? JUDGE_RESULT[status] : undefined;
  return judgeColorClass(entry?.color || "gray");
};

async function load() {
  const id = props.submissionId;
  if (!id) {
    errorText.value = "提交不存在";
    errorDetail.value = "提交 ID 无效";
    return;
  }
  loading.value = true;
  try {
    // ⚠️ 雪花 id 超出 Number 安全整数，转 number 会丢末位导致查不到 —— path 参数必须保持字符串
    const res = await getSubmissionVoById({ path: { id } });
    if (res.data?.code === 0 && res.data.data) {
      submission.value = res.data.data;
      errorText.value = "";
      errorDetail.value = "";
    } else {
      submission.value = null;
      errorText.value = res.data?.code === 40101 ? "无权查看该提交" : "提交不存在";
      errorDetail.value = res.data?.message || "";
    }
  } catch (error: any) {
    const code = error?.response?.data?.code;
    errorText.value = code === 40101 ? "无权查看该提交" : "加载失败";
    errorDetail.value = error?.response?.data?.message || "请稍后重试";
  } finally {
    loading.value = false;
  }
}

let pollTimer: number | null = null;

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = window.setInterval(() => {
    if (isJudging.value) void load();
  }, POLL_INTERVAL_MS);
}

// 切换提交时重新加载；判题中开始轮询，进入终态后停止
watch(
  () => props.submissionId,
  () => {
    void load();
  }
);
watch(isJudging, (judging) => {
  if (judging) {
    startPolling();
  } else {
    stopPolling();
  }
});

onMounted(() => {
  void load();
});
onUnmounted(stopPolling);
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col gap-4">
    <div
      v-if="loading && !submission"
      class="flex h-40 items-center justify-center text-muted-foreground"
    >
      提交详情加载中…
    </div>

    <!-- 无权限 / 不存在 -->
    <Card v-else-if="!submission">
      <CardContent class="flex h-40 flex-col items-center justify-center gap-2">
        <p class="text-lg font-medium">{{ errorText }}</p>
        <p class="text-sm text-muted-foreground">{{ errorDetail }}</p>
      </CardContent>
    </Card>

    <template v-else>
      <!-- 概要信息 -->
      <Card class="shrink-0 py-0">
        <CardContent class="grid grid-cols-2 gap-4 p-4 sm:grid-cols-4">
          <div v-if="showQuestion">
            <p class="text-xs text-muted-foreground">题目</p>
            <button
              v-if="submission.questionVO?.id"
              class="truncate text-left text-sm font-medium text-foreground-strong hover:underline"
              @click="openQuestion(String(submission.questionVO.id))"
            >
              {{ submission.questionVO.title || `#${submission.questionVO.id}` }}
            </button>
            <p v-else class="text-sm">-</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">提交号</p>
            <p class="truncate text-sm font-medium">{{ submission.id }}</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">语言</p>
            <p class="text-sm">{{ submission.language || '-' }}</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">状态</p>
            <span
              class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
              :class="judgeColorClass(formatStatus(submission.status ?? -1).color)"
            >
              {{ formatStatus(submission.status ?? -1).text }}
            </span>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">判定</p>
            <span
              v-if="formatJudgeInfo(submission.judgeInfo)?.text"
              class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
              :class="judgeColorClass(formatJudgeInfo(submission.judgeInfo)!.color)"
            >
              {{ formatJudgeInfo(submission.judgeInfo)!.text }}
            </span>
            <p v-else class="text-sm text-muted-foreground">暂无</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">耗时 / 内存</p>
            <p class="text-sm">
              {{ submission.judgeInfo?.time ?? '-' }}ms ·
              {{ submission.judgeInfo?.memory ?? '-' }}KB
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">提交时间</p>
            <p class="text-sm">
              {{ submission.createTime ? formatTime(submission.createTime) : '-' }}
            </p>
          </div>
        </CardContent>
      </Card>

      <!-- 分用例结果 -->
      <Card v-if="caseResults.length" class="shrink-0 py-0">
        <CardContent class="space-y-3 p-4">
          <p class="text-sm font-medium text-muted-foreground">分用例结果</p>
          <div
            v-for="(c, i) in caseResults"
            :key="i"
            class="flex items-center justify-between rounded-md border border-l-[3px] border-l-primary bg-muted/40 px-4 py-2.5"
          >
            <span class="text-sm font-medium">用例 {{ i + 1 }}</span>
            <span
              class="inline-flex items-center rounded border px-2 py-0.5 text-xs"
              :class="caseClass(c.status)"
            >
              {{ c.status || '未知' }}
            </span>
            <span class="text-xs text-muted-foreground">
              {{ c.time ?? '-' }}ms · {{ c.memory ?? '-' }}KB
            </span>
          </div>
        </CardContent>
      </Card>

      <!-- 提交代码（只读） -->
      <Card class="uc-detail-code flex min-h-0 flex-[1_1_auto] flex-col overflow-hidden py-0">
        <CardContent class="flex min-h-0 flex-1 flex-col p-4 pb-0">
          <p class="mb-3 shrink-0 text-sm font-medium text-muted-foreground">
            提交代码
          </p>
          <div class="min-h-0 flex-1 overflow-hidden rounded-md border border-border">
            <CodeEditor
              :model-value="submission.code || '// 无代码'"
              :language="submission.language || 'java'"
              :minimap="false"
              read-only
            />
          </div>
        </CardContent>
      </Card>
    </template>
  </div>
</template>

<style scoped>
/**
 * 代码区高度：Monaco 的容器是 `h-full`（`height: 100%`），它要有一个**确定高度**的父链才画得出来。
 *
 * 两个都不能踩的坑（无头浏览器实测 card → holder → monaco 三层高度）：
 * - **不能用 `min-height`**：它不会让盒子成为「确定高度」，`height: 100%` 找不到确定高度就
 *   退化为 `auto`，而 CodeEditor 的宿主是空 div → 高度 0，代码区全空白。
 * - **flex-basis 必须 `auto`**：Tailwind 的 `flex-1` 是 `flex: 1 1 0%`，`0%` 会覆盖 `height`
 *   作为主轴尺寸，反而把卡片压成内容高（实测 41px）；`flex-[1_1_auto]` 才既保留 `height`，
 *   又能在祖先有确定高度时继续 `flex-grow` 撑满剩余空间（即本区块的设计意图）。
 */
.uc-detail-code {
  flex: 1 1 auto;
  height: 22rem;
}
</style>
