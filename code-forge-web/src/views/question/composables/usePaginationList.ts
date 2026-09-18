import { ref, reactive, watch } from 'vue'
import { toast } from 'vue-sonner'

interface PaginationConfig {
  current: number
  pageSize: number
  total: number
  showTotal?: boolean
  showPageSize?: boolean
  showJumper?: boolean
}

interface Option {
  api: (params: any) => Promise<any>
  buildParams: (pagination: { current: number; pageSize: number }, searchData: any) => any
  initSearchParams?: Record<string, any>
  immediate?: boolean
  onSuccess?: (data: any) => void
}

export function usePaginationList<T>(options: Option) {
  const loading = ref(false)
  const tableData = ref<T[]>([])
  const pagination = reactive<PaginationConfig>({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: true,
    showPageSize: true,
    showJumper: true
  })
  const searchParams = reactive({ ...options.initSearchParams })

  const fetchData = async () => {
    loading.value = true
    try {
      const params = options.buildParams(
        { current: pagination.current, pageSize: pagination.pageSize },
        searchParams
      )
      const res = await options.api({ body: params })
      if (res.data && res.data.code === 0 && res.data.data) {
        tableData.value = res.data.data.records || []
        pagination.total = Number(res.data.data.total) || 0
        options.onSuccess?.(res.data.data)
      } else {
        tableData.value = []
        pagination.total = 0
        toast.error(res.data?.message || '获取数据失败')
      }
    } catch {
      tableData.value = []
      pagination.total = 0
      toast.error('加载数据失败')
    } finally {
      loading.value = false
    }
  }

  const handleSearch = () => {
    pagination.current = 1
    fetchData()
  }

  const handleReset = () => {
    Object.assign(searchParams, options.initSearchParams || {})
    pagination.current = 1
    fetchData()
  }

  const handlePageChange = (page: number) => {
    pagination.current = page
  }

  const handlePageSizeChange = (size: number) => {
    pagination.pageSize = size
    pagination.current = 1
  }

  // 分页参数变化时自动请求
  watch(
    [() => pagination.current, () => pagination.pageSize],
    () => {
      fetchData()
    },
    { immediate: options.immediate !== false }
  )

  return {
    loading,
    tableData,
    pagination,
    searchParams,
    fetchData,
    handleSearch,
    handleReset,
    handlePageChange,
    handlePageSizeChange
  }
}
