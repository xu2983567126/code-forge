import router from '@/router'
import { useUserStore } from '@/store/user'
import ACCESS_ENUM from './accessEnum'
import checkAccess from './checkAccess'

export interface LoginUser {
  role?: string
  username?: string
}

// 检查用户是否具有有效登录状态
const hasValidLogin = (user: LoginUser | null | undefined): boolean => {
  return !!user?.role && user.role !== ACCESS_ENUM.NOT_LOGIN
}

// 判断是否需要向后端回拉登录态。
//
// ⚠️ 不能用 `!user?.role`：store 的初始 state 里 role 就是 'notLogin'（真值），
//    那样刷新后会被误判成「已有登录态」而跳过回拉 —— 页面永远停在初始的
//    「未登录」，表现为「一刷新登录态就没了，必须重新登录」。
//    正确判据是「role 尚未取到，或仍是未登录哨兵值」。
const needsUserInfoRefresh = (user: LoginUser | null | undefined): boolean => {
  return !hasValidLogin(user)
}

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()

  // 如果未登录，尝试自动登录
  if (needsUserInfoRefresh(userStore.loginUser)) {
    await userStore.getLoginUser()
  }

  const loginUser = userStore.loginUser
  const needAccess = (to.meta?.access as string) ?? ACCESS_ENUM.NOT_LOGIN

  if (needAccess !== ACCESS_ENUM.NOT_LOGIN) {
    // 如果用户登录状态无效，重定向到登录页
    if (!hasValidLogin(loginUser)) {
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }

    if (!checkAccess(loginUser, needAccess)) {
      next({ path: '/noAuth' })
      return
    }
  }

  next()
})
