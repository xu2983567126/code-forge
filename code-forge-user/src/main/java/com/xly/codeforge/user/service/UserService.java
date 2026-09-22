package com.xly.codeforge.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.xly.codeforge.model.dto.user.UserQueryRequest;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.LoginUserVO;
import com.xly.codeforge.model.vo.UserVO;
import com.xly.codeforge.model.vo.UserWithStatsVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户服务
 *
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param account   用户账户
     * @param password  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String account, String password, String checkPassword);

    /**
     * 用户登录
     *
     * @param account  用户账户
     * @param password 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String account, String password, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    User getLoginUserPermitNull(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    boolean isAdmin(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param userList
     * @return
     */
    List<UserVO> getUserVO(List<User> userList);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 分页获取「带提交统计」的用户列表（管理端）
     *
     * <p>提交数 / 通过数从 submission-service 批量取（一次 RPC 覆盖整页），
     * 不是逐行调用 —— 后者在 20 条/页时会变成 20 次 RPC。</p>
     *
     * @param userQueryRequest 分页与筛选条件
     * @return 带统计的用户视图分页
     */
    Page<UserWithStatsVO> pageUserWithStats(UserQueryRequest userQueryRequest);

    /**
     * 封禁用户（管理端）
     *
     * <p>只改 {@code role} 为 {@code ban}，不做物理删除 —— 封禁要可撤销，
     * 且用户的提交记录仍要保留（榜单、题目通过率会引用到它）。</p>
     *
     * @param id 用户 id
     * @return 是否成功
     */
    boolean banUser(long id);

    /**
     * 解封用户（管理端）
     *
     * <p>把 {@code role} 恢复为 {@code user}。刻意不恢复成 {@code admin}：
     * 封禁前是否管理员这件事没被记录，凭空提权比降权危险得多。</p>
     *
     * @param id 用户 id
     * @return 是否成功
     */
    boolean unbanUser(long id);

    /**
     * 批量删除用户（管理端，逻辑删除）
     *
     * @param idList 用户 id 列表，最多 200 个
     * @return 实际删除条数
     */
    int batchDeleteUser(List<Long> idList);

}
