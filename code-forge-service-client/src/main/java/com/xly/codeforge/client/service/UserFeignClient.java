package com.xly.codeforge.client.service;

import cn.dev33.satoken.stp.StpUtil;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.model.enums.RoleEnum;
import com.xly.codeforge.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务
 *
 */
@HttpExchange("http://code-forge-user/api/user/inner")
public interface UserFeignClient {

    /**
     * 根据Id获取用户
     *
     * @param userId
     * @return
     */
    @GetExchange("/get/id")
    User getById(@RequestParam("userId") long userId);

    /**
     * 根据Id获取用户列表
     *
     * @param idList
     * @return
     */
    @GetExchange("/get/ids")
    List<User> listByIds(@RequestParam("idList") List<Long> idList);

    /**
     * 获取当前登录用户
     *
     * <p>实现：登录态以 Redis 中的 token 为中心（Sa-Token），
     * 各服务 {@code StpUtil.getLoginIdDefaultNull()} 直读，天然跨服务。</p>
     *
     * @param request 占位参数，实现不从其中读取任何内容
     * @return 当前登录用户（完整实体）
     */
    default User getLoginUser(HttpServletRequest request) {
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
     * 是否为管理员
     *
     * @param user
     * @return
     */
    default boolean isAdmin(User user) {
        return user != null && RoleEnum.ADMIN.getValue().equals(user.getRole());
    }

    /**
     * 获取脱敏的用户信息
     *
     * @param user
     * @return
     */
    default UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }
}
