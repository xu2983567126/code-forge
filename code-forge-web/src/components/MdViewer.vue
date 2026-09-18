<template>
  <div class="md-viewer">
    <div v-if="content" v-html="renderedContent" class="markdown-content"></div>
    <div v-else class="empty-content">
      <a-empty description="暂无内容" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

interface Props {
  content?: string
}

const props = withDefaults(defineProps<Props>(), {
  content: ''
})

// 配置marked选项（sanitize 已在 marked v12+ 移除，统一由 DOMPurify 负责 XSS 防护）
marked.setOptions({
  breaks: true,
  gfm: true,
})

// 渲染Markdown内容
const renderedContent = computed(() => {
  if (!props.content) return ''

  try {
    const html = marked(props.content) as string
    // 使用DOMPurify进行XSS防护
    return DOMPurify.sanitize(html)
  } catch (error) {
    console.error('Markdown渲染错误:', error)
    return '<p>内容渲染失败</p>'
  }
})
</script>

<style scoped>
.md-viewer {
  width: 100%;
  height: 100%;
}

.markdown-content {
  line-height: 1.6;
  color: var(--color-text-1);
  overflow-y: auto;
  max-height: 100%;
}

.markdown-content :deep(h1) {
  font-size: 1.8em;
  margin: 0.5em 0;
  color: var(--color-text-1);
  border-bottom: 1px solid var(--color-border-2);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h2) {
  font-size: 1.5em;
  margin: 0.8em 0 0.4em 0;
  color: var(--color-text-1);
}

.markdown-content :deep(h3) {
  font-size: 1.3em;
  margin: 0.6em 0 0.3em 0;
  color: var(--color-text-1);
}

.markdown-content :deep(p) {
  margin: 0.5em 0;
}

.markdown-content :deep(code) {
  background: var(--color-fill-2);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  padding: 1em;
  overflow-x: auto;
  margin: 1em 0;
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid var(--color-primary-6);
  margin: 1em 0;
  padding-left: 1em;
  color: var(--color-text-2);
  background: var(--color-fill-1);
  padding: 1em;
  border-radius: 0 6px 6px 0;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1em 0;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid var(--color-border-2);
  padding: 0.5em;
  text-align: left;
}

.markdown-content :deep(th) {
  background: var(--color-fill-1);
  font-weight: 600;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 2em;
}

.markdown-content :deep(li) {
  margin: 0.3em 0;
}

.empty-content {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--color-text-3);
}
</style>
