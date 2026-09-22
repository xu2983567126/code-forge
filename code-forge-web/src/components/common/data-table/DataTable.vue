<script setup lang="ts" generic="T extends object">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import {
  Table,
  TableBody,
  TableHeader,
  TableCell,
  TableRow,
  TableHead,
} from "@/components/ui/table";
import { SearchX } from "lucide-vue-next";
import { useVirtualizer } from "@tanstack/vue-virtual";
import { cn } from "@/lib/utils";

export interface ColumnDef {
  key: string;
  header: string;
  class?: string;
  headerClass?: string;
}

// Interface for data with an optional id property
interface Identifiable {
  id?: string | number;
}

const props = withDefaults(
  defineProps<{
    data: T[];
    columns?: ColumnDef[];
    loading?: boolean;
    hasMore?: boolean;
    emptyLabel?: string;
    emptyDescription?: string;
    loadingLabel?: string;
    virtualized?: boolean;
    /**
     * 虚拟滚动容器高度。
     * - number：按 px
     * - string：按 CSS 长度。传 `"100%"` 可填满已定高的 flex 父容器（父容器需 `flex-1 min-h-0`）
     */
    virtualizedHeight?: number | string;
    rowHeight?: number;
  }>(),
  {
    virtualized: false,
    virtualizedHeight: 600,
    rowHeight: 48,
  },
);

// Reference columns to avoid ESLint unused variable warning
// (columns is used in the template but Vue doesn't detect template usage)
const hasColumnDefinitions = computed(() => Boolean(props.columns));

const emit = defineEmits<{
  "load-more": [];
  "row-click": [item: T];
}>();

const scrollRef = ref<HTMLDivElement | null>(null);

/**
 * 虚拟滚动 options 必须是 computed。
 *
 * `useVirtualizer` 的 options 类型是 `MaybeRef` —— 传普通对象字面量时 `count` 只在
 * 初始化那一刻求值一次。列表数据是异步拉取的（首帧 0 条），之后 count 永远停在 0，
 * 总高 0px、一行都不渲染（表格看起来是空白的）。
 *
 * 另外：虚拟行的定位容器必须是 `tbody`（模板里给 TableBody 加了 `relative`，配套
 * Table 的 `container-class="overflow-visible"`）。若让行相对**含表头的**容器定位，
 * `translateY(0)` 会正好落在表头下面被压住，数据只有一两行时整个列表看起来就是空的。
 */
const virtualizer = useVirtualizer(
  computed(() => ({
    count: props.data.length,
    getScrollElement: () => scrollRef.value,
    estimateSize: () => props.rowHeight,
    overscan: 10,
  })),
);

const virtualItems = computed(() => virtualizer.value.getVirtualItems());

const handleScroll = () => {
  if (props.virtualized) return; // Skip window scroll handler when virtualized

  const buffer = 200;
  const isAtBottom =
    window.innerHeight + window.scrollY >=
    document.documentElement.offsetHeight - buffer;
  if (isAtBottom) {
    emit("load-more");
  }
};

// Handle virtualized scroll for load more
watch(
  () => props.data.length,
  () => {
    if (!props.virtualized || !props.hasMore || props.loading) return;

    const range = virtualizer.value.range;
    if (range && range.endIndex >= props.data.length - 5) {
      emit("load-more");
    }
  },
);

onMounted(() => {
  if (!props.virtualized) {
    window.addEventListener("scroll", handleScroll);
  }
});

onUnmounted(() => {
  if (!props.virtualized) {
    window.removeEventListener("scroll", handleScroll);
  }
});

// Helper function to get cell value
const getCellValue = (item: T, key: string) => {
  return (item as Record<string, unknown>)[key];
};

// Helper to safely get item at index - guaranteed to return T since virtualizer only returns valid indices
const getItemAtIndex = (index: number): T => {
  const item = props.data[index];
  // Virtualizer only provides valid indices, so this should never happen
  // but we add a fallback to satisfy TypeScript
  if (!item) {
    throw new Error(
      `Invalid index ${index} for data array of length ${props.data.length}`,
    );
  }
  return item;
};
</script>

<template>
  <div
    v-if="virtualized"
    ref="scrollRef"
    class="relative w-full overflow-auto"
    :style="{
      height:
        typeof virtualizedHeight === 'number'
          ? `${virtualizedHeight}px`
          : virtualizedHeight,
    }"
  >
    <div
      :style="{
        height: `${virtualizer.getTotalSize()}px`,
        width: '100%',
        position: 'relative',
      }"
    >
      <Table container-class="overflow-visible" class="table-fixed">
        <TableHeader class="sticky top-0 bg-background z-10">
          <slot name="header">
            <TableRow
              v-if="hasColumnDefinitions"
              class="border-b border-border"
            >
              <!--
                col.class 必须同时上 th：table-fixed 的列宽由表头决定，虚拟行又是
                脱离布局的 absolute —— 若只有 td 有宽度而 th 没有，表头均分列宽、
                数据行按各自宽度渲染，逐列全部错位。

                虚拟行自身还要是 `display:table` + `table-layout:fixed`：absolute 的
                tr 会被块化，td 被浏览器包进匿名表格走自动布局（剩余空间按内容分配），
                与表头的 fixed 比例分配不是一套算法，宽屏下逐列偏开。让行自己也成为
                fixed 表格（同 width:100%、同列宽），两端才算出同一套列宽。
              -->
              <TableHead
                v-for="col in columns"
                :key="col.key"
                :class="[
                  'font-extrabold text-[var(--primary)] text-xxs uppercase tracking-widest py-4 px-4 bg-[var(--surface-sunken)]/60',
                  col.class,
                  col.headerClass,
                ]"
              >
                {{ col.header }}
              </TableHead>
            </TableRow>
          </slot>
        </TableHeader>
        <TableBody class="relative">
          <template v-if="data.length > 0">
            <template v-if="hasColumnDefinitions">
              <TableRow
                v-for="virtualRow in virtualItems"
                :key="
                  (getItemAtIndex(virtualRow.index) as Identifiable).id ||
                  virtualRow.index
                "
                class="odd:bg-[var(--surface-sunken)] even:bg-card hover:bg-[var(--primary)]/5 border-b border-border cursor-pointer transition-colors"
                :style="{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  display: 'table',
                  tableLayout: 'fixed',
                  width: '100%',
                  height: `${virtualRow.size}px`,
                  transform: `translateY(${virtualRow.start}px)`,
                }"
                @click="emit('row-click', getItemAtIndex(virtualRow.index))"
              >
                <TableCell
                  v-for="col in columns"
                  :key="col.key"
                  :class="
                    cn(
                      'py-3.5 px-4 text-xs text-foreground font-medium align-middle',
                      col.class,
                    )
                  "
                >
                  <!--
                    必须包一层 truncate：虚拟行是 absolute，脱离表格布局后每行的列宽
                    各自按内容计算，长内容（如 19 位雪花 ID）会撑破列宽压到隔壁列。
                    外层 table 用 table-fixed 锁死列宽，这里负责把超宽内容截断。
                  -->
                  <div class="truncate">
                    <template v-if="$slots[`cell-${col.key}`]">
                      <slot
                        :name="`cell-${col.key}`"
                        :item="getItemAtIndex(virtualRow.index)"
                      />
                    </template>
                    <template v-else>
                      {{
                        getCellValue(getItemAtIndex(virtualRow.index), col.key)
                      }}
                    </template>
                  </div>
                </TableCell>
              </TableRow>
            </template>
          </template>
          <TableRow v-else>
            <TableCell colspan="100%" class="p-0">
              <slot name="empty">
                <div
                  class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-none bg-muted/5 text-center px-6 m-4"
                >
                  <div
                    class="flex h-16 w-16 items-center justify-center rounded-none bg-muted/50 mb-4"
                  >
                    <SearchX class="h-8 w-8 text-muted-foreground/50" />
                  </div>
                  <p class="text-xl font-bold text-foreground">
                    {{ emptyLabel || "No results found" }}
                  </p>
                  <p class="text-sm text-muted-foreground mt-2 max-w-[300px]">
                    {{
                      emptyDescription ||
                      "Try adjusting your filters or search query."
                    }}
                  </p>
                </div>
              </slot>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
    <slot name="footer" />
    <div v-if="hasMore" class="text-center py-4 text-muted-foreground">
      {{ loadingLabel || "Loading more..." }}
    </div>
  </div>

  <!-- Non-virtualized mode (original implementation) -->
  <div v-else class="relative w-full overflow-auto">
    <Table>
      <TableHeader>
        <slot name="header">
          <TableRow v-if="hasColumnDefinitions" class="border-b border-border">
            <TableHead
              v-for="col in columns"
              :key="col.key"
              :class="[
                'font-extrabold text-[var(--primary)] text-xxs uppercase tracking-widest py-4 px-4 bg-[var(--surface-sunken)]/60',
                col.headerClass,
              ]"
            >
              {{ col.header }}
            </TableHead>
          </TableRow>
        </slot>
      </TableHeader>

      <TableBody>
        <template v-if="data.length > 0">
          <template v-if="hasColumnDefinitions">
            <TableRow
              v-for="(item, index) in data"
              :key="(item as Identifiable).id || index"
              class="odd:bg-[var(--surface-sunken)] even:bg-card hover:bg-[var(--primary)]/5 border-b border-border cursor-pointer transition-colors"
              @click="emit('row-click', item)"
            >
              <TableCell
                v-for="col in columns"
                :key="col.key"
                :class="
                  cn(
                    'py-3.5 px-4 text-xs text-foreground-muted dark:text-[var(--foreground-muted)] font-medium align-middle',
                    col.class,
                  )
                "
              >
                <template v-if="$slots[`cell-${col.key}`]">
                  <slot :name="`cell-${col.key}`" :item="item" />
                </template>
                <template v-else>
                  {{ getCellValue(item, col.key) }}
                </template>
              </TableCell>
            </TableRow>
          </template>
          <template v-else>
            <slot
              name="row"
              v-for="item in data"
              :key="(item as Identifiable).id || JSON.stringify(item)"
              :item="item"
            />
          </template>
        </template>

        <TableRow v-else>
          <TableCell colspan="100%" class="p-0">
            <slot name="empty">
              <div
                class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 rounded-none bg-muted/5 text-center px-6 m-4"
              >
                <div
                  class="flex h-16 w-16 items-center justify-center rounded-none bg-muted/50 mb-4"
                >
                  <SearchX class="h-8 w-8 text-muted-foreground/50" />
                </div>
                <p class="text-xl font-bold text-foreground">
                  {{ emptyLabel || "No results found" }}
                </p>
                <p class="text-sm text-muted-foreground mt-2 max-w-[300px]">
                  {{
                    emptyDescription ||
                    "Try adjusting your filters or search query."
                  }}
                </p>
              </div>
            </slot>
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
    <slot name="footer" />
    <div v-if="hasMore" class="text-center py-4 text-muted-foreground">
      {{ loadingLabel || "Loading more..." }}
    </div>
  </div>
</template>
