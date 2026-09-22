<template>
  <Dialog v-model:open="open">
    <DialogContent class="max-w-2xl">
      <DialogHeader>
        <DialogTitle>代码模板库</DialogTitle>
        <DialogDescription>
          选一段 {{ languageLabel }} 模板插入到编辑器。核心代码模式（题目自带驱动代码）下不可用。
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-3">
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索模板名称或说明…"
          class="uc-tpl-search"
        />
        <div class="max-h-[52vh] space-y-4 overflow-y-auto pr-1">
          <div v-for="cat in visibleCategories" :key="cat.value">
            <p class="mb-2 text-xs font-medium text-muted-foreground">
              {{ cat.label }}
            </p>
            <div class="space-y-2">
              <button
                v-for="tpl in templatesOf(cat.value)"
                :key="tpl.id"
                type="button"
                class="w-full rounded-md border border-border p-3 text-left transition-colors hover:border-primary hover:bg-primary/5"
                @click="pick(tpl)"
              >
                <p class="text-sm font-medium">{{ tpl.name }}</p>
                <p class="mt-0.5 text-xs text-muted-foreground">
                  {{ tpl.description }}
                </p>
              </button>
            </div>
          </div>
          <p v-if="visibleCategories.length === 0" class="text-sm text-muted-foreground">
            没有匹配的模板。
          </p>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { LANGUAGE_OPTIONS } from "@/constants/language";
import {
  getTemplateCategories,
  getTemplatesByCategory,
  type CodeTemplate,
  type TemplateCategory,
} from "@/constants/codeTemplates";

const props = defineProps<{ language: string }>();
const emit = defineEmits<{ select: [code: string] }>();

const open = defineModel<boolean>("open", { default: false });
const keyword = ref("");

const languageLabel = computed(
  () => LANGUAGE_OPTIONS.find((o) => o.value === props.language)?.label || props.language
);

const visibleCategories = computed(() =>
  getTemplateCategories().filter((cat) => templatesOf(cat.value).length > 0)
);

function templatesOf(cat: TemplateCategory): CodeTemplate[] {
  const kw = keyword.value.trim().toLowerCase();
  return getTemplatesByCategory(cat).filter(
    (t) =>
      t.language === props.language &&
      (kw === "" ||
        t.name.toLowerCase().includes(kw) ||
        t.description.toLowerCase().includes(kw))
  );
}

function pick(tpl: CodeTemplate) {
  emit("select", tpl.code);
  open.value = false;
}
</script>

<style scoped>
.uc-tpl-search {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--background);
  padding: 8px 10px;
  font-size: 13px;
}
</style>
