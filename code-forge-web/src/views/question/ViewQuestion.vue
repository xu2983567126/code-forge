<template>
  <div class="uc-page-stack">
    <Card>
      <CardContent class="p-0">
        <!-- 加载态 -->
        <div v-if="loading" class="space-y-4 p-6">
          <div class="h-8 w-1/2 animate-pulse rounded bg-muted" />
          <div class="h-64 w-full animate-pulse rounded bg-muted" />
          <div class="h-96 w-full animate-pulse rounded bg-muted" />
        </div>

        <div v-else-if="questionDetail" class="uc-solve-split">
          <ResizablePanelGroup
            direction="horizontal"
            auto-save-id="code-forge:solve-split"
            class="h-full w-full overflow-hidden"
          >
            <!-- 左侧：题目详情 -->
            <ResizablePanel
              :default-size="45"
              :min-size="25"
              class="flex min-w-0 flex-col"
            >
              <div
                class="flex shrink-0 items-center justify-between border-b border-border px-4 py-2"
              >
                <div class="flex gap-1">
                  <button
                    v-for="t in tabs"
                    :key="t.key"
                    type="button"
                    :class="[
                      'rounded-md px-4 py-2 text-sm transition-colors',
                      activeTab === t.key
                        ? 'bg-muted font-medium text-foreground'
                        : 'text-muted-foreground hover:bg-muted/60',
                    ]"
                    @click="activeTab = t.key"
                  >
                    {{ t.label }}
                  </button>
                </div>
                <Button variant="ghost" size="icon" title="刷新" @click="handleRefresh">
                  <RefreshCw class="h-4 w-4" />
                </Button>
              </div>

              <div class="min-h-0 flex-1 overflow-y-auto p-6">
                <template v-if="activeTab === 'question'">
                  <div class="mb-5 border-b border-border pb-4">
                    <h2 class="text-2xl font-semibold">
                      {{ questionDetail.title || "无标题" }}
                    </h2>
                    <div
                      v-if="questionDetail.tags && questionDetail.tags.length"
                      class="mt-2 flex flex-wrap gap-2"
                    >
                      <Badge
                        v-for="tag in questionDetail.tags"
                        :key="tag"
                        variant="secondary"
                      >
                        {{ tag }}
                      </Badge>
                    </div>
                  </div>

                  <div class="mb-5 rounded-lg bg-muted p-4">
                    <p class="mb-2 text-sm font-medium text-muted-foreground">
                      判题条件
                    </p>
                    <div class="grid grid-cols-3 gap-4 text-sm">
                      <div>
                        <span class="text-muted-foreground">时间限制</span>
                        <p>{{ questionDetail.judgeConfig?.timeLimit || 1000 }}ms</p>
                      </div>
                      <div>
                        <span class="text-muted-foreground">内存限制</span>
                        <p>{{ questionDetail.judgeConfig?.memoryLimit || 128 }}KB</p>
                      </div>
                      <div>
                        <span class="text-muted-foreground">堆栈限制</span>
                        <p>{{ questionDetail.judgeConfig?.stackLimit || 128 }}KB</p>
                      </div>
                    </div>
                  </div>

                  <div class="markdown-block min-h-[400px]">
                    <MdViewer :content="questionDetail.content || ''" />
                  </div>
                </template>

                <div
                  v-else-if="activeTab === 'comment'"
                  class="flex h-72 items-center justify-center text-muted-foreground"
                >
                  评论功能暂未实现
                </div>
                <div
                  v-else
                  class="flex h-72 items-center justify-center text-muted-foreground"
                >
                  答案功能暂未实现
                </div>
              </div>
            </ResizablePanel>

            <ResizableHandle with-handle />

            <!-- 右侧：代码编辑器 -->
            <ResizablePanel
              :default-size="55"
              :min-size="30"
              class="flex min-w-0 flex-col"
            >
              <div
                class="flex shrink-0 items-center justify-between border-b border-border px-4 py-2"
              >
                <h3 class="text-lg font-semibold">代码编辑器</h3>
                <select
                  v-model="selectedLanguage"
                  class="h-9 rounded-md border border-input bg-background px-3 text-sm"
                  @change="handleLanguageChange"
                >
                  <option
                    v-for="opt in languageOptions"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ opt.label }}
                  </option>
                </select>
              </div>

              <div class="min-h-0 flex-1 p-4">
                <div class="h-full w-full overflow-hidden rounded-md border border-border">
                  <CodeEditor
                    v-model="code"
                    :language="selectedLanguage"
                    :minimap="false"
                  />
                </div>
              </div>

              <div class="shrink-0 border-t border-border p-3 text-center">
                <Button size="lg" :disabled="submitting" @click="handleSubmit">
                  提交代码
                </Button>
              </div>
            </ResizablePanel>
          </ResizablePanelGroup>
        </div>

        <div v-else class="p-16 text-center text-muted-foreground">
          题目不存在或加载失败
        </div>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { toast } from "vue-sonner";
import { RefreshCw } from "lucide-vue-next";
import CodeEditor from "@/components/CodeEditor.vue";
import MdViewer from "@/components/MdViewer.vue";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "@/components/ui/resizable";
import { getQuestionVoById, submit } from "@generated";
import type { QuestionVo } from "@generated";

const route = useRoute();

// 题目ID
const questionId = computed(() => route.params.id as string);

// 响应式数据
const loading = ref(false);
const submitting = ref(false);
const code = ref("");
const questionDetail = ref<QuestionVo | null>(null);
const activeTab = ref("question");
const selectedLanguage = ref("java");

// 标签页
const tabs = [
  { key: "question", label: "题目" },
  { key: "comment", label: "评论" },
  { key: "answer", label: "答案" },
];

// 编程语言选项（value 既是后端枚举值，也是 Monaco 语言 id）
const languageOptions = [
  { label: "Java", value: "java" },
  { label: "C++", value: "cpp" },
  { label: "Go", value: "go" },
  { label: "Python", value: "python" },
  { label: "C", value: "c" },
];

// 获取题目详情
const loadQuestionDetail = async () => {
  if (!questionId.value) {
    toast.error("题目ID不存在");
    return;
  }

  loading.value = true;
  try {
    const questionIdNum = parseInt(questionId.value);
    if (isNaN(questionIdNum)) {
      toast.error("题目ID格式错误");
      return;
    }

    const res = await getQuestionVoById({
      path: { id: questionIdNum },
    });

    if (res.data && res.data.code === 0 && res.data.data) {
      questionDetail.value = res.data.data;
    } else {
      toast.error("获取题目详情失败");
    }
  } catch (error) {
    console.error("加载题目详情失败:", error);
    toast.error("加载题目详情失败");
  } finally {
    loading.value = false;
  }
};

// 处理语言切换
const handleLanguageChange = () => {
  const opt = languageOptions.find((o) => o.value === selectedLanguage.value);
  toast.success(`已切换到 ${opt?.label || selectedLanguage.value}`);
};

// 提交代码
const handleSubmit = async () => {
  console.log("提交代码:", code.value, selectedLanguage.value);

  if (!code.value || code.value.length === 0) {
    toast.error("请先编写有效的代码");
    return;
  }

  submitting.value = true;
  try {
    const result = await submit({
      body: {
        language: selectedLanguage.value,
        code: code.value,
        questionId: parseInt(questionId.value),
      },
    });

    if (result.data && result.data.code === 0) {
      toast.success("代码提交成功！");
    } else {
      toast.error(result.data?.message || "提交失败");
    }
  } catch (error: any) {
    console.error("提交代码失败:", error);

    if (error.response?.data?.message) {
      toast.error(`提交失败: ${error.response.data.message}`);
    } else {
      toast.error("提交代码失败，请检查网络连接");
    }
  } finally {
    submitting.value = false;
  }
};

// 刷新页面
const handleRefresh = () => {
  loadQuestionDetail();
  toast.info("已刷新");
};

// 组件挂载时加载题目详情
onMounted(() => {
  loadQuestionDetail();
});
</script>

<style scoped>
/**
 * 做题页分栏（L3）。
 *
 * 必须给分栏容器一个**确定高度**，两侧面板的 `overflow-y-auto` 才有意义 ——
 * 否则高度由内容撑开，滚动条会落回整页，独立滚动名存实亡。
 * 高度用视口反推：顶栏 h-14 + 页面 gutter（上下各一份）+ Card 上下边框 2px。
 * 用 `--uc-layout-page-gutter` 而不是写死数值，换 locale 时自动跟随。
 *
 * 小屏（<1024px）退化为纵向堆叠：面板组转 column、隐藏把手、清掉 reka 写在
 * 面板上的 inline flex 尺寸，让内容自然撑开、整页滚动 —— 窄屏上横向分栏两边都不可用。
 * reka 会把 `display/flex-direction/width` 直接写成 **inline style**，内联样式特异性最高，
 * 这里的每条覆盖都必须 !important。
 */
.uc-solve-split {
  height: calc(100dvh - 3.5rem - var(--uc-layout-page-gutter) * 2 - 2px);
  min-height: 26rem;
}

@media (max-width: 1023.98px) {
  .uc-solve-split {
    height: auto;
    min-height: 0;
  }

  .uc-solve-split :deep([data-slot="resizable-panel-group"]) {
    flex-direction: column !important;
  }

  .uc-solve-split :deep([data-slot="resizable-handle"]) {
    display: none !important;
  }

  .uc-solve-split :deep([data-slot="resizable-panel"]) {
    flex: none !important;
    width: 100% !important;
    max-width: 100% !important;
  }
}
</style>
