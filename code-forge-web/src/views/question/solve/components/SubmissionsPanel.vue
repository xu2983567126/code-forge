<script setup lang="ts">
/**
 * 提交记录面板（做题页左侧，与「题目描述」并列）。
 *
 * 形态：本题的提交列表 ↔ 选中某条后切到详情（同页切换，不跳路由）—— 与 UltiCode
 * 的 `SubmissionsView` 同构，这样提交完能立刻看到判题结果，不用跑去提交记录页。
 *
 * 刷新策略：列表轮询（存在「等待中/判题中」时按 10s 重载，与提交记录页一致）；
 * 详情的轮询由 `SubmissionDetailPanel` 自己负责（2s），两条链路互不干扰。
 */
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { ChevronLeft, ChevronRight, Loader2 } from "lucide-vue-next";
import { listSubmissionByPage } from "@generated";
import type { SubmissionVo } from "@generated";
import SubmissionDetailPanel from "@/components/submission/SubmissionDetailPanel.vue";
import { Button } from "@/components/ui/button";
import { useInfiniteList } from "@/composables/useInfiniteList";
import { formatTime } from "@/utils/format";
import {
  JUDGE_STATUS,
  formatStatus,
  formatJudgeInfo,
  judgeColorClass,
} from "@/constants/judge";
import { useSolveContext } from "../composables/useSolveSession";

/** 与提交记录页一致：只在确实有判题中的提交时才轮询，避免翻看历史时被强行刷新。 */
const LIST_POLL_INTERVAL_MS = 10000;

const { questionId, selectedSubmissionId, selectSubmission } = useSolveContext();

const { items, loading, loadingMore, hasMore, reload, loadMore } =
  useInfiniteList<SubmissionVo>({
    api: listSubmissionByPage,
    buildParams: (page) => ({
      current: page.current,
      pageSize: page.pageSize,
      questionId: questionId.value || undefined,
    }),
    defaultFilters: {},
  });

const sentinel = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | undefined;
let pollTimer: number | null = null;

const hasPendingJudge = computed(() =>
  items.value.some(
    (s) =>
      s.status === JUDGE_STATUS.PENDING.value ||
      s.status === JUDGE_STATUS.JUDGING.value
  )
);

function statusClass(status?: number) {
  return judgeColorClass(formatStatus(status ?? -1).color);
}

function judgeText(item: SubmissionVo) {
  return formatJudgeInfo(item.judgeInfo)?.text || "—";
}

function backToList() {
  selectSubmission(null);
  void reload();
}

// 切换题目（路由变化）时重置选中并重新拉取
watch(questionId, () => {
  selectSubmission(null);
  void reload();
});

onMounted(() => {
  void reload();
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) loadMore();
    },
    { rootMargin: "300px" }
  );
  watch(
    sentinel,
    (el) => {
      if (el) observer?.observe(el);
    },
    { immediate: true }
  );
  pollTimer = window.setInterval(() => {
    if (hasPendingJudge.value) void reload();
  }, LIST_POLL_INTERVAL_MS);
});

onUnmounted(() => {
  observer?.disconnect();
  if (pollTimer !== null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
});
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col">
    <!-- 详情态：由会话层的 selectedSubmissionId 驱动（提交成功后自动进入） -->
    <template v-if="selectedSubmissionId">
      <div class="flex shrink-0 items-center gap-2 border-b border-border px-4 py-2">
        <Button variant="ghost" size="sm" @click="backToList">
          <ChevronLeft class="mr-1 h-4 w-4" />
          返回列表
        </Button>
        <span class="truncate text-xs text-muted-foreground">
          #{{ selectedSubmissionId }}
        </span>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto p-4">
        <SubmissionDetailPanel :submission-id="selectedSubmissionId" />
      </div>
    </template>

    <!-- 列表态 -->
    <div v-else class="min-h-0 flex-1 overflow-y-auto p-4">
      <div class="mb-3 flex items-center justify-between">
        <p class="text-sm font-medium text-muted-foreground">本题的提交</p>
        <Button
          variant="ghost"
          size="icon"
          :disabled="loading"
          title="刷新"
          @click="reload"
        >
          <Loader2 class="h-4 w-4" :class="loading ? 'animate-spin' : ''" />
        </Button>
      </div>

      <div
        v-if="loading && !items.length"
        class="flex h-32 items-center justify-center text-sm text-muted-foreground"
      >
        加载中…
      </div>
      <div
        v-else-if="!items.length"
        class="flex h-32 items-center justify-center text-sm text-muted-foreground"
      >
        还没有提交记录，运行通过后点击「提交代码」。
      </div>

      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="mb-2 flex w-full items-center gap-3 rounded-md border border-border bg-muted/40 px-3 py-2.5 text-left transition-colors hover:bg-muted"
        @click="selectSubmission(String(item.id))"
      >
        <span
          class="inline-flex shrink-0 items-center rounded border px-2 py-0.5 text-xs"
          :class="statusClass(item.status)"
        >
          {{ formatStatus(item.status ?? -1).text }}
        </span>
        <span class="min-w-0 flex-1 truncate text-sm">
          {{ judgeText(item) }}
        </span>
        <span class="shrink-0 text-xxs text-muted-foreground">
          {{ item.createTime ? formatTime(item.createTime) : '-' }}
        </span>
        <ChevronRight class="h-4 w-4 shrink-0 text-muted-foreground" />
      </button>

      <div ref="sentinel" class="h-4"></div>
      <p v-if="loadingMore" class="text-center text-xs text-muted-foreground">
        加载更多…
      </p>
      <p
        v-else-if="!hasMore && items.length"
        class="text-center text-xs text-muted-foreground"
      >
        已到末尾
      </p>
    </div>
  </div>
</template>
