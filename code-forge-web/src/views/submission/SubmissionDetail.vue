<template>
  <div class="uc-page-stack">
    <PageHeader title="提交详情" description="查看本次提交的判题结果与代码">
      <template #actions>
        <Button variant="outline" @click="router.back()">返回</Button>
      </template>
    </PageHeader>

    <!--
      详情主体与做题页内的「提交记录」面板共用同一个组件 —— 两处必须是同一份渲染与
      同一套刷新策略。加载、错误、轮询、代码区高度都在面板内部自持。
    -->
    <SubmissionDetailPanel :submission-id="submissionId" />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Button } from "@/components/ui/button";
import PageHeader from "@/components/PageHeader.vue";
import SubmissionDetailPanel from "@/components/submission/SubmissionDetailPanel.vue";

const route = useRoute();
const router = useRouter();

/** 雪花 id 必须保持字符串，不能转 number（超出 JS 安全整数会丢末位）。 */
const submissionId = computed(() => route.params.id as string);
</script>
