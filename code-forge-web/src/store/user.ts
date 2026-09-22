import { defineStore } from 'pinia'
import { getLoginUser } from '@generated'
import ACCESS_ENUM from '@/access/accessEnum'

interface LoginUserState {
  /**
   * 当前用户 id（雪花，字符串）。
   *
   * 用于「这条数据是不是我的」这类前端判断（题单编辑 / 移出题目入口的显隐）。
   * ⚠️ 保持字符串：19 位雪花转 number 会丢末位，比较结果恒为 false。
   */
  id?: string
  username: string
  role?: string
}

export const useUserStore = defineStore('user', {
  state: () => ({
    loginUser: {
      username: '未登录',
      role: ACCESS_ENUM.NOT_LOGIN
    } as LoginUserState
  }),

  getters: {
    // 可以在这里定义计算属性
    isLoggedIn: (state) => state.loginUser.username !== '未登录'
  },

  actions: {
    // Pinia中直接修改state，不需要mutations
    updateUser(payload: { username?: string; role?: string }) {
      this.loginUser = { ...this.loginUser, ...payload }
    },

    // 异步操作示例
    async getLoginUser() {
      try {
        // 使用生成的API调用
        const res = await getLoginUser()

        // 处理返回的数据
        // BaseResponseLoginUserVo结构: { code, data: LoginUserVo, message }
        if (res && res.data && res.data.data) {
          this.loginUser = {
            id: res.data.data.id,
            username: res.data.data.username || '已登录用户',
            role: res.data.data.role || ACCESS_ENUM.NOT_LOGIN
          }
        } else {
          // 如果获取失败，保持未登录状态
          this.loginUser = {
            username: '未登录',
            role: ACCESS_ENUM.NOT_LOGIN
          }
        }
      } catch (error) {
        console.error('获取用户信息失败:', error)
        // 发生错误时也设置为未登录状态
        this.loginUser = {
          username: '未登录',
          role: ACCESS_ENUM.NOT_LOGIN
        }
      }
    },

    // 登录不放这里：真实请求由 src/views/user/Login.vue 直接调 SDK 的 userLogin()，
    // 成功后调本 store 的 getLoginUser() 刷新登录态。

    // 登出：清本地 state。真实请求由侧边栏 NavUser 调 SDK 的 userLogout()。
    logout() {
      this.loginUser = { username: '未登录' }
    }
  }
})