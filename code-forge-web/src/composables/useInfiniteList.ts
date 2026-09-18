/**
 * 无限滚动列表（L4）。
 *
 * 与逐页翻页的 `usePaginationList` 是两种模型：这里的 `items` 是**累积**的，
 * 滚动到底触发 `loadMore` 追加下一页，配合 `DataTable` 的虚拟滚动处理千条数据。
 *
 * 两个必须内建的保护：
 * - **竞态**：快切筛选时旧请求可能后到，会把新筛选的结果覆盖成旧的。每次请求领一个
 *   自增 token，响应回来只认「token 仍是最新」的那次。
 * - **防抖**：输入框每敲一个字就重载会打出大量废请求，`applyFilters` 默认 300ms 合并。
 */
import { computed, reactive, ref, type Ref } from 'vue'
import { toast } from 'vue-sonner'

interface PageParams {
  current: number
  pageSize: number
}

interface InfiniteListOptions {
  /**
   * SDK 列表方法。
   * 参数与返回值放宽为 `any`：生成的方法带 `ThrowOnError` 泛型，写死具体签名反而无法赋值；
   * 响应结构在 `fetchPage` 内部逐层取，不依赖此处的类型。
   */
  api: (params: any) => Promise<any>
  /** 由分页参数 + 筛选条件拼出请求体 */
  buildParams: (page: PageParams, filters: Record<string, any>) => any
  /** 初始筛选条件，同时作为「重置」的还原目标 */
  defaultFilters?: Record<string, any>
  /** 每页条数。后端 `pageSize` 上限为 20，超过会报 40000 */
  pageSize?: number
  /** 防抖窗口，单位毫秒 */
  debounceMs?: number
}

export function useInfiniteList<T>(options: InfiniteListOptions) {
  const pageSize = Math.min(options.pageSize ?? 20, 20)
  const debounceMs = options.debounceMs ?? 300

  const items = ref([]) as Ref<T[]>
  const filters = reactive({ ...(options.defaultFilters ?? {}) })
  /** 首次加载 / 筛选重载中 */
  const loading = ref(false)
  /** 追加下一页中 */
  const loadingMore = ref(false)
  const total = ref(0)
  const current = ref(1)

  /** 竞态令牌。只有持有最新令牌的响应才允许写入状态。 */
  let latestRequestToken = 0
  let debounceTimer: ReturnType<typeof setTimeout> | undefined

  const hasMore = computed(() => items.value.length < total.value)

  function resetItems() {
    items.value = []
    total.value = 0
    current.value = 1
  }

  async function fetchPage(page: number, mode: 'replace' | 'append') {
    const token = ++latestRequestToken
    if (mode === 'replace') loading.value = true
    else loadingMore.value = true

    try {
      const res = await options.api({
        body: options.buildParams({ current: page, pageSize }, filters)
      })
      // 已被更新的请求取代：整段丢弃，连 toast 都不发（否则会闪一个过期的错误）
      if (token !== latestRequestToken) return

      const data = res?.data?.data as { records?: T[]; total?: number } | undefined
      if (res?.data?.code === 0 && data) {
        const records = data.records ?? []
        items.value = mode === 'replace' ? records : [...items.value, ...records]
        total.value = Number(data.total) || 0
        current.value = page
      } else {
        if (mode === 'replace') resetItems()
        toast.error(res?.data?.message || '获取数据失败')
      }
    } catch {
      if (token !== latestRequestToken) return
      if (mode === 'replace') resetItems()
      toast.error('加载数据失败')
    } finally {
      // 只有最新请求负责收尾，避免旧请求把新请求的 loading 提前关掉
      if (token === latestRequestToken) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  /** 回到第一页重新加载（筛选变化后调用）。 */
  function reload() {
    if (debounceTimer) clearTimeout(debounceTimer)
    return fetchPage(1, 'replace')
  }

  /** 滚动到底时追加下一页。 */
  function loadMore() {
    if (loading.value || loadingMore.value || !hasMore.value) return
    return fetchPage(current.value + 1, 'append')
  }

  /** 筛选条件变化后调用：防抖合并，再重载第一页。 */
  function applyFilters() {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      fetchPage(1, 'replace')
    }, debounceMs)
  }

  /** 还原筛选条件并立即重载（不防抖 —— 用户点了按钮就是要立刻看到结果）。 */
  function resetFilters() {
    Object.assign(filters, options.defaultFilters ?? {})
    return reload()
  }

  return {
    items,
    filters,
    loading,
    loadingMore,
    total,
    hasMore,
    reload,
    loadMore,
    applyFilters,
    resetFilters
  }
}
