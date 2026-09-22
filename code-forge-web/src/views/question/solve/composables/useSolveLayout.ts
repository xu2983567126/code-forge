import { ref, watch, type Ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useBreakpoints } from "@/composables/useBreakpoints";

/**
 * 移动端做题面板。桌面端同时显示描述 + 代码 + 测试，窄屏只能挨个看，
 * 因此把五个区块摊平成一个 tab 序列。
 */
export type SolveMobileTab =
  | "description"
  | "code"
  | "cases"
  | "results"
  | "submissions";

const TAB_IDS: SolveMobileTab[] = [
  "description",
  "code",
  "cases",
  "results",
  "submissions",
];

function isTab(value: unknown): value is SolveMobileTab {
  return typeof value === "string" && TAB_IDS.includes(value as SolveMobileTab);
}

/**
 * 做题页布局：<768px 走移动端 tab 布局，其余走桌面分栏。
 *
 * 移动端当前 tab 与路由 `:tab?` 双向同步 —— 刷新、分享链接能停在同一屏。
 * 双向 watch 必须带守卫，否则「路由改 → 状态改 → 写路由 → 路由改」会成环。
 *
 * 桌面端的两个 tab（左侧描述/提交、右侧用例/结果）**不进路由**：它们本来就是并存的
 * 两个维度，塞进同一个参数会让语义打架。
 */
export function useSolveLayout(): {
  isMobile: Ref<boolean>;
  mobileTab: Ref<SolveMobileTab>;
} {
  const route = useRoute();
  const router = useRouter();
  const { isMobile } = useBreakpoints();

  const raw = route.params.tab;
  const initial = Array.isArray(raw) ? raw[0] : raw;
  const mobileTab = ref<SolveMobileTab>(isTab(initial) ? initial : "description");

  /** 两个方向的更新互相屏蔽，避免回环。 */
  let updatingFromRoute = false;
  let updatingFromState = false;

  watch(
    () => route.params.tab,
    (value) => {
      if (updatingFromState) return;
      const next = Array.isArray(value) ? value[0] : value;
      if (isTab(next) && next !== mobileTab.value) {
        updatingFromRoute = true;
        mobileTab.value = next;
        // 下一个 tick 再解除：等本次路由变更引发的渲染结束
        Promise.resolve().then(() => {
          updatingFromRoute = false;
        });
      }
    }
  );

  watch(mobileTab, (next) => {
    if (updatingFromRoute) return;
    const current = Array.isArray(route.params.tab)
      ? route.params.tab[0]
      : route.params.tab;
    if (next === current) return;
    updatingFromState = true;
    void router
      .replace({
        name: route.name ?? undefined,
        params: { ...route.params, tab: next },
        query: route.query,
      })
      .catch(() => {
        /* 重复导航等异常忽略即可，状态已由本地 ref 持有 */
      })
      .finally(() => {
        Promise.resolve().then(() => {
          updatingFromState = false;
        });
      });
  });

  return { isMobile, mobileTab };
}
