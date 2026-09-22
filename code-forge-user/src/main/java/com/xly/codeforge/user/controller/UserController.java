package com.xly.codeforge.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xly.codeforge.common.annotation.AuthCheck;
import com.xly.codeforge.common.common.Result;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.utils.ResultUtils;
import com.xly.codeforge.common.constant.UserConstant;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.dto.dashboard.UserHeatmapDTO;
import com.xly.codeforge.model.dto.dashboard.UserStats;
import com.xly.codeforge.model.dto.user.*;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.vo.LoginUserVO;
import com.xly.codeforge.model.vo.UserVO;
import com.xly.codeforge.model.vo.UserWithStatsVO;
import com.xly.codeforge.user.service.DashboardService;
import com.xly.codeforge.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.xly.codeforge.user.service.impl.UserServiceImpl.SALT;


/**
 * 用户接口
 *
 * <p><b>管理端路径约定</b>：所有仅管理员可用的接口，路径统一带
 * {@code /manage} 前缀（如 {@code /user/manage/{id}}、{@code /user/manage/list/page}），
 * 并在方法上标 {@code @AuthCheck(mustRole = ADMIN_ROLE)}。</p>
 *
 * <p>这样做的目的不是好看，而是<b>为将来抽独立管理服务预留迁移缝</b>：
 * 管理端接口成规模后，可以整体迁到独立服务，网关把 {@code /api/*&#47;manage/**}
 * 路由过去即可，<b>前端一行都不用改</b>（URL 未变）。</p>
 *
 * <p>注意 {@code POST /user/list/page/vo} 是公开的用户展示列表（榜单等），
 * 不属于管理端，故不带该前缀。</p>
 *
 */
@RestController
@RequestMapping("/")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private DashboardService dashboardService;

    // region 登录相关

    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    public Result<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String account = userRegisterRequest.getAccount();
        String password = userRegisterRequest.getPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(account, password, checkPassword)) {
            return null;
        }
        long result = userService.userRegister(account, password, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login")
    public Result<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String account = userLoginRequest.getAccount();
        String password = userLoginRequest.getPassword();
        if (StringUtils.isAnyBlank(account, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUserVO = userService.userLogin(account, password, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public Result<Boolean> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public Result<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(user));
    }

    // endregion

    // region 增删改查

    /**
     * 创建用户
     *
     * <p>{@code POST /user/manage/create}。管理端路径统一带 {@code /manage} 前缀，
     * 见类注释的迁移说明。</p>
     */
    @PostMapping("/manage/create")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Long> createUser(@RequestBody UserCreateRequest userCreateRequest, HttpServletRequest request) {
        if (userCreateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userCreateRequest, user);
        // 默认密码 12345678
        String defaultPassword = "12345678";
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + defaultPassword).getBytes());
        user.setPassword(encryptPassword);
        boolean result = userService.save(user);
        BusinessAssert.isTrue(result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 删除用户
     *
     * <p>{@code DELETE /user/manage/{id}}</p>
     */
    @DeleteMapping("/manage/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> deleteUser(@PathVariable("id") long id, HttpServletRequest request) {
                BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 修改用户（仅管理员）
     *
     * <p>{@code PATCH /user/manage/{id}}。</p>
     *
     * @param id                用户 id（路径参数）
     * @param userUpdateRequest 只带需要改的字段
     */
    @PatchMapping("/manage/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> updateUser(@PathVariable("id") long id,
                                      @RequestBody UserUpdateRequest userUpdateRequest,
                                      HttpServletRequest request) {
        if (userUpdateRequest == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        user.setId(id);
        boolean result = userService.updateById(user);
        BusinessAssert.isTrue(result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取用户（仅管理员）
     *
     * <p>{@code GET /user/manage/{id}}</p>
     */
    @GetMapping("/manage/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<User> getUserById(@PathVariable("id") long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        BusinessAssert.notNull(user, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     *
     * <p>{@code GET /user/manage/{id}/vo}</p>
     */
    @GetMapping("/manage/{id}/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<UserVO> getUserVOById(@PathVariable("id") long id, HttpServletRequest request) {
                BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        User user = userService.getById(id);
        BusinessAssert.notNull(user, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 分页获取用户列表（仅管理员）
     *
     * <p>{@code POST /user/manage/list/page}</p>
     */
    @PostMapping("/manage/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest,
                                             HttpServletRequest request) {
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        return ResultUtils.success(userPage);
    }

    /**
     * 分页获取用户封装列表
     *
     * <p>{@code POST /user/list/page/vo}。注意本接口<b>不带</b> {@code /manage} 前缀 ——
     * 它是公开的用户展示列表（榜单、题解作者列表等），不是管理端接口。</p>
     */
    @PostMapping("/list/page/vo")
    public Result<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest,
                                                 HttpServletRequest request) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        // 限制爬虫
        BusinessAssert.isTrue(size <= 20, ErrorCode.PARAMS_ERROR);
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userService.getUserVO(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return ResultUtils.success(userVOPage);
    }

    // endregion

    // region 管理端扩展（带统计的列表 / 封禁 / 批量删除）

    /**
     * 分页获取带提交统计的用户列表（仅管理员）
     *
     * <p>{@code POST /user/manage/list/page/vo/with-stats}</p>
     *
     * <p>单独一个接口而不是给 {@code /list/page/vo} 加开关：后者是公开榜单接口，
     * 不该因为管理端要看统计就被迫依赖 submission 服务。分开之后，
     * submission 挂了也只是这个管理页面不显示统计列，公开榜单不受影响。</p>
     */
    @PostMapping("/manage/list/page/vo/with-stats")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Page<UserWithStatsVO>> listUserWithStatsByPage(
            @RequestBody UserQueryRequest userQueryRequest,
            HttpServletRequest request) {
        BusinessAssert.notNull(userQueryRequest, ErrorCode.PARAMS_ERROR);
        BusinessAssert.isTrue(userQueryRequest.getPageSize() <= 50, ErrorCode.PARAMS_ERROR, "单页最多 50 条");
        return ResultUtils.success(userService.pageUserWithStats(userQueryRequest));
    }

    /**
     * 封禁用户（仅管理员）
     *
     * <p>{@code POST /user/manage/{id}/ban}。用 POST 而不是 PATCH：
     * 这是状态机上的动作（user → ban），不是对资源字段的局部修改。</p>
     */
    @PostMapping("/manage/{id}/ban")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> banUser(@PathVariable("id") long id, HttpServletRequest request) {
                BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        return ResultUtils.success(userService.banUser(id));
    }

    /**
     * 解封用户（仅管理员）
     *
     * <p>{@code POST /user/manage/{id}/unban}</p>
     */
    @PostMapping("/manage/{id}/unban")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> unbanUser(@PathVariable("id") long id, HttpServletRequest request) {
                BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        return ResultUtils.success(userService.unbanUser(id));
    }

    /**
     * 批量删除用户（仅管理员）
     *
     * <p>{@code POST /user/manage/batch/delete}。走 POST + body 的原因见
     * {@code UserBatchDeleteRequest} 的类注释（DELETE body 会被部分网关丢弃）。</p>
     *
     * @return 实际删除的用户数
     */
    @PostMapping("/manage/batch/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Integer> batchDeleteUser(@RequestBody UserBatchDeleteRequest batchDeleteRequest,
                                           HttpServletRequest request) {
        BusinessAssert.notNull(batchDeleteRequest, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userService.batchDeleteUser(batchDeleteRequest.getIdList()));
    }

    /**
     * 用户域统计概览（仅管理员）
     *
     * <p>{@code GET /user/manage/stats}。与 {@code DashboardController#getUserStats}
     * 是同一个数据的两个入口：这里挂在用户域下，方便前端在用户管理页只依赖
     * user 服务的路径；dashboard 那个挂在看板下。</p>
     */
    @GetMapping("/manage/stats")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<UserStats> getUserStats(HttpServletRequest request) {
        return ResultUtils.success(dashboardService.loadUserStats());
    }

    /**
     * 查看指定用户的提交热力图（仅管理员）
     *
     * <p>{@code GET /user/manage/{id}/heatmap?days=}。用户自己看热力图走
     * {@code GET /dashboard/heatmap}（无需管理员），这里是管理员查别人的入口。</p>
     *
     * @param id   目标用户 id
     * @param days 统计窗口天数，默认 365
     */
    @GetMapping("/manage/{id}/heatmap")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<UserHeatmapDTO> getUserHeatmap(@PathVariable("id") long id,
                                                 @RequestParam(value = "days", defaultValue = "365") int days,
                                                 HttpServletRequest request) {
                BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        int safeDays = Math.clamp(days, 1, 365);
        return ResultUtils.success(dashboardService.loadHeatmap(id, safeDays));
    }

    // endregion

    /**
     * 更新个人信息
     *
     * <p>{@code PATCH /user/me}。</p>
     *
     * <p>用 {@code /me} 而不是 {@code /my/{id}}：本人操作的 id 恒等于登录态里的 id，
     * 放进路径只会多一个可被伪造/越权的参数位，直接由服务端从登录态取更安全。</p>
     *
     * @param userUpdateMyRequest 只带需要改的字段
     */
    @PatchMapping("/me")
    public Result<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
                                        HttpServletRequest request) {
        if (userUpdateMyRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        User user = new User();
        BeanUtils.copyProperties(userUpdateMyRequest, user);
        user.setId(loginUser.getId());
        boolean result = userService.updateById(user);
        BusinessAssert.isTrue(result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }
}
