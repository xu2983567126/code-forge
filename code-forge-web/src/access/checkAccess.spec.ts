import { describe, it, expect } from 'vitest'
import checkAccess from './checkAccess'
import ACCESS_ENUM from './accessEnum'

describe('checkAccess 权限判定', () => {
  it('未登录可访问无需权限的页面', () => {
    expect(checkAccess(null, ACCESS_ENUM.NOT_LOGIN)).toBe(true)
  })

  it('需要 USER 但无登录态则拒绝', () => {
    expect(checkAccess({ role: ACCESS_ENUM.NOT_LOGIN }, ACCESS_ENUM.USER)).toBe(false)
  })

  it('已登录普通用户可访问 USER 页面', () => {
    expect(checkAccess({ role: ACCESS_ENUM.USER }, ACCESS_ENUM.USER)).toBe(true)
  })

  it('管理员可访问 USER 页面', () => {
    expect(checkAccess({ role: ACCESS_ENUM.ADMIN }, ACCESS_ENUM.USER)).toBe(true)
  })

  it('非管理员访问 ADMIN 页面被拒绝', () => {
    expect(checkAccess({ role: ACCESS_ENUM.USER }, ACCESS_ENUM.ADMIN)).toBe(false)
  })
})
