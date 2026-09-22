package com.xly.codeforge.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.constant.CommonConstant;
import com.xly.codeforge.common.constant.UserConstant;
import com.xly.codeforge.common.exception.BusinessAssert;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.common.utils.SqlUtils;
import com.xly.codeforge.model.dto.SubmissionStatsItemDTO;
import com.xly.codeforge.model.dto.user.UserQueryRequest;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.enums.RoleEnum;
import com.xly.codeforge.model.vo.LoginUserVO;
import com.xly.codeforge.model.vo.UserVO;
import com.xly.codeforge.model.vo.UserWithStatsVO;
import com.xly.codeforge.client.service.SubmissionFeignClient;
import com.xly.codeforge.user.mapper.UserMapper;
import com.xly.codeforge.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 用户服务实现
 *
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，混淆密码
     */
    public static final String SALT = "yupi";

    /**
     * 单次批量删除的用户数上限
     *
     * <p>不设上限时前端手滑传几万个 id，一条 {@code IN (...)} 会把 SQL 撑爆。</p>
     */
    private static final int MAX_DELETE_BATCH = 200;

    @Resource
    private SubmissionFeignClient submissionFeignClient;

    @Override
    public long userRegister(String account, String password, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(account, password, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (account.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (password.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!password.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 账户不能重复。
        // 并发防重交给数据库唯一索引承担（account 列建了 UNIQUE KEY，见 sql/create_table.sql），
        // 兜底捕获 DuplicateKeyException。
        // 不要用 synchronized：它只锁得住单个 JVM，多实例部署时完全失效，
        // 且 intern() 会往字符串常量池里塞无限多的账号名。
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());
        // 3. 插入数据
        User user = new User();
        user.setAccount(account);
        user.setPassword(encryptPassword);
        try {
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
        } catch (DuplicateKeyException e) {
            // 并发下两个请求同时通过了上面的 count 检查，靠唯一索引兜底
            log.warn("并发注册冲突，account={}", account, e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String account, String password, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(account, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (account.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (password.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        queryWrapper.eq("password", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, account cannot match password");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录用户的登录态（Redis 中的 token 为中心，loginId = userId）
        StpUtil.login(user.getId());
        // 角色写入 Sa-Token Session，供 BanCheckInterceptor 等读取
        StpUtil.getSession().set("role", user.getRole());
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 登录态从 Redis 中的 token 解析（userId 即 loginId）
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User currentUser = this.getById(Long.parseLong(loginId.toString()));
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return null;
        }
        return this.getById(Long.parseLong(loginId.toString()));
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return false;
        }
        User user = this.getById(Long.parseLong(loginId.toString()));
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && RoleEnum.ADMIN.getValue().equals(user.getRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (StpUtil.getLoginIdDefaultNull() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 注销登录态（清除 Redis 中的 token）
        StpUtil.logout();
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String username = userQueryRequest.getUsername();
        String profile = userQueryRequest.getProfile();
        String role = userQueryRequest.getRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "union_id", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mp_open_id", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(role), "role", role);
        queryWrapper.like(StringUtils.isNotBlank(profile), "profile", profile);
        queryWrapper.like(StringUtils.isNotBlank(username), "username", username);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    // region 管理端

    @Override
    public Page<UserWithStatsVO> pageUserWithStats(UserQueryRequest userQueryRequest) {
        BusinessAssert.notNull(userQueryRequest, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();

        Page<User> userPage = this.page(new Page<>(current, size), getQueryWrapper(userQueryRequest));
        Page<UserWithStatsVO> voPage = new Page<>(current, size, userPage.getTotal());
        List<User> records = userPage.getRecords();
        if (CollUtil.isEmpty(records)) {
            voPage.setRecords(new ArrayList<>());
            return voPage;
        }

        // 一次 RPC 拿整页的提交统计 —— 逐行调用会变成 20 次 RPC
        List<Long> userIdList = records.stream().map(User::getId).collect(Collectors.toList());
        Map<Long, SubmissionStatsItemDTO> statsMap = new LinkedHashMap<>();
        try {
            List<SubmissionStatsItemDTO> stats = submissionFeignClient.listStatsByUserIds(userIdList);
            if (stats != null) {
                for (SubmissionStatsItemDTO item : stats) {
                    statsMap.put(item.getUserId(), item);
                }
            }
        } catch (Exception e) {
            // 统计拿不到不该让整个用户列表打不开：基础字段照常返回，统计留 0。
            // 这里不往响应里塞 degraded 标记，是因为「用户的提交统计」不是页面主信息，
            // 而列表本身可用才是关键 —— 与 dashboard 的处理粒度不同。
            log.error("批量获取用户提交统计失败，统计列降级为 0，userIdList={}", userIdList, e);
        }

        List<UserWithStatsVO> voList = new ArrayList<>(records.size());
        for (User user : records) {
            UserWithStatsVO vo = new UserWithStatsVO();
            BeanUtils.copyProperties(user, vo);
            SubmissionStatsItemDTO item = statsMap.get(user.getId());
            long submitCount = item == null || item.getSubmitCount() == null ? 0L : item.getSubmitCount();
            long acceptedCount = item == null || item.getAcceptedCount() == null ? 0L : item.getAcceptedCount();
            vo.setSubmitCount(submitCount);
            vo.setAcceptedCount(acceptedCount);
            // 分母为 0 时给 0.0 而不是 null：前端直接乘 100 展示，null 会渲染成 NaN%
            vo.setAcceptedRate(submitCount == 0 ? 0.0 : acceptedCount / (double) submitCount);
            voList.add(vo);
        }
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public boolean banUser(long id) {
        BusinessAssert.isTrue(id > 0, ErrorCode.INVALID_ID);
        User oldUser = this.getById(id);
        BusinessAssert.notNull(oldUser, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        // 幂等：已经是 ban 就直接返回成功，不报错也不重复写库
        if (UserConstant.BAN_ROLE.equals(oldUser.getRole())) {
            return true;
        }
        // 不允许封禁管理员：否则一个管理员手滑能把整个平台锁死
        BusinessAssert.isTrue(RoleEnum.ADMIN != RoleEnum.getEnumByValue(oldUser.getRole()),
                ErrorCode.FORBIDDEN_ERROR, "不能封禁管理员");
        User update = new User();
        update.setId(id);
        update.setRole(UserConstant.BAN_ROLE);
        return this.updateById(update);
    }

    @Override
    public boolean unbanUser(long id) {
        BusinessAssert.isTrue(id > 0, ErrorCode.PARAMS_ERROR);
        User oldUser = this.getById(id);
        BusinessAssert.notNull(oldUser, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        if (RoleEnum.BAN != RoleEnum.getEnumByValue(oldUser.getRole())) {
            return true;
        }
        User update = new User();
        update.setId(id);
        update.setRole(RoleEnum.USER.getValue());
        return this.updateById(update);
    }

    @Override
    public int batchDeleteUser(List<Long> idList) {
        BusinessAssert.notEmpty(idList, ErrorCode.PARAMS_ERROR, "待删除的用户列表为空");

        List<Long> distinctIds = idList.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        BusinessAssert.notEmpty(distinctIds, ErrorCode.PARAMS_ERROR, "无有效的用户 id");
        BusinessAssert.isTrue(distinctIds.size() <= MAX_DELETE_BATCH, ErrorCode.PARAMS_ERROR,
                "单次最多删除 " + MAX_DELETE_BATCH + " 个用户");

        // removeByIds 走的是逻辑删除（User.isDelete 有 @TableLogic），
        // 用户的提交记录、题目都还引用着这个 id，物理删除会让历史数据变成孤儿。
        boolean result = this.removeByIds(distinctIds);
        BusinessAssert.isTrue(result, ErrorCode.OPERATION_ERROR);
        return distinctIds.size();
    }

    // endregion
}
