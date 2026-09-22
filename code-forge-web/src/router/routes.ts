import Home from '@/views/Home.vue'
import NoAuth from '@/views/NoAuth.vue'
import ACCESS_ENUM from '@/access/accessEnum'
import AppLayout from '@/layouts/AppLayout.vue'
import UserLayout from '@/layouts/UserLayout.vue'
import Login from '@/views/user/Login.vue'
import Register from '@/views/user/Register.vue'
import CreateQuestion from '@/views/question/CreateQuestion.vue'
import EditQuestion from '@/views/question/EditQuestion.vue'
import ManageQuestion from '@/views/question/ManageQuestion.vue'
import Questions from '@/views/question/Questions.vue'
import ViewQuestion from '@/views/question/ViewQuestion.vue'
import Submissions from '@/views/question/Submissions.vue'
import SubmissionDetail from '@/views/submission/SubmissionDetail.vue'
import QuestionBanks from '@/views/question/QuestionBanks.vue'
import QuestionBankDetail from '@/views/question/QuestionBankDetail.vue'
import UserManage from '@/views/user/UserManage.vue'
import Dashboard from '@/views/dashboard/Dashboard.vue'
import ProfileDashboard from '@/views/dashboard/ProfileDashboard.vue'

/**
 * 路由表。
 *
 * 布局层由路由决定（不再由 App.vue 按路径字符串手工分支）：
 * - `/user/*` → UserLayout（独立的居中卡片版式，无侧边栏）
 * - 其余       → AppLayout（侧边栏 + 顶栏 + uc-page-main/uc-page-container）
 *
 * 菜单**不**从本表反向推导，而是 `layouts/sidebar.data.ts` 的显式白名单；
 * 这里保留的 `meta.hideInMenu` 只是历史标记，供旧逻辑兼容。
 */
export const routes = [
  {
    path: '/user',
    name: '用户',
    component: UserLayout,
    meta: {
      hideInMenu: true
    },
    children: [
      {
        path: 'login',
        name: '用户登录',
        component: Login,
        meta: {
          access: ACCESS_ENUM.NOT_LOGIN
        }
      },
      {
        path: 'register',
        name: '用户注册',
        component: Register,
        meta: {
          access: ACCESS_ENUM.NOT_LOGIN
        }
      }
    ]
  },
  {
    path: '/',
    component: AppLayout,
    children: [
      {
        path: '',
        name: '浏览题目',
        component: Questions
      },
      {
        path: 'questions',
        name: '题目列表',
        component: Questions
      },
      {
        path: 'submissions',
        name: '提交记录',
        component: Submissions,
        meta: {
          access: ACCESS_ENUM.USER
        }
      },
      {
        path: 'submissions/:id',
        name: '提交详情',
        component: SubmissionDetail,
        meta: {
          access: ACCESS_ENUM.USER,
          hideInMenu: true
        }
      },
      {
        path: 'hide',
        name: '隐藏页面',
        component: Home,
        meta: {
          hideInMenu: true
        }
      },
      {
        path: 'noAuth',
        name: '无权限',
        component: NoAuth,
        meta: {
          hideInMenu: true
        }
      },
      {
        path: 'questions/create',
        name: '创建题目',
        component: CreateQuestion,
        meta: {
          access: ACCESS_ENUM.USER
        }
      },
      {
        path: 'questions/manage',
        name: '管理题目',
        component: ManageQuestion,
        meta: {
          access: ACCESS_ENUM.ADMIN
        }
      },
      {
        path: 'questions/:id/edit',
        name: '更新题目',
        component: EditQuestion,
        meta: {
          access: ACCESS_ENUM.ADMIN,
          hideInMenu: true
        }
      },
      {
        path: 'questions/:id/view/:tab?',
        name: '在线做题',
        component: ViewQuestion,
        props: true,
        meta: {
          access: ACCESS_ENUM.USER,
          hideInMenu: true
        }
      },
      {
        path: 'banks',
        name: '题库专题',
        component: QuestionBanks
      },
      {
        path: 'banks/:id',
        name: '题单详情',
        component: QuestionBankDetail,
        props: true,
        meta: {
          hideInMenu: true
        }
      },
      {
        path: 'admin/users',
        name: '用户管理',
        component: UserManage,
        meta: {
          access: ACCESS_ENUM.ADMIN
        }
      },
      {
        path: 'admin/dashboard',
        name: '平台仪表板',
        component: Dashboard,
        meta: {
          access: ACCESS_ENUM.ADMIN
        }
      },
      {
        path: 'dashboard/me',
        name: '我的主页',
        component: ProfileDashboard,
        meta: {
          access: ACCESS_ENUM.USER
        }
      }
    ]
  }
]
