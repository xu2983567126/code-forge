import ACCESS_ENUM from './accessEnum'
import type { LoginUser } from './index'

/**
 * 检查用户是否有足够的权限
 * @param loginUser 当前登录用户
 * @param needAccess 需要的权限
 * @returns 是否有足够的权限
 */
const checkAccess = (loginUser: LoginUser, needAccess = ACCESS_ENUM.NOT_LOGIN) => {
  // 获取当前用户的权限
  const loginUserAccess = loginUser?.role ?? ACCESS_ENUM.NOT_LOGIN

  // 如果不需要权限，直接通过
  if (needAccess === ACCESS_ENUM.NOT_LOGIN) {
    return true
  }

  // 如果需要登录但用户未登录
  if (needAccess === ACCESS_ENUM.USER && loginUserAccess === ACCESS_ENUM.NOT_LOGIN) {
    return false
  }

  // 如果需要管理员权限
  if (needAccess === ACCESS_ENUM.ADMIN) {
    return loginUserAccess === ACCESS_ENUM.ADMIN
  }

  // 默认情况下，只要用户已登录就通过
  return loginUserAccess !== ACCESS_ENUM.NOT_LOGIN
}

export default checkAccess
