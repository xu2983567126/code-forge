package com.xly.codeforge.user.controller;

import com.xly.codeforge.model.entity.User;
import com.xly.codeforge.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户服务「内部接口」—— 只给其他微服务和网关调用，不对外暴露给前端。
 *
 * <p>服务的 context-path 是 {@code /api/user}，因此完整路径为 {@code /api/user/inner/xxx}，
 * 与 service-client 里 UserFeignClient 的声明一一对应。</p>
 *
 * @author xuxu
 */
@RestController
@RequestMapping("/inner")
public class InnerUserController {

    @Resource
    private UserService userService;

    /**
     * 根据 id 获取用户（Feign: GET /api/user/inner/get/id）
     */
    @GetMapping("/get/id")
    public User getById(@RequestParam("userId") long userId) {
        return userService.getById(userId);
    }

    /**
     * 根据 id 列表批量获取用户（Feign: GET /api/user/inner/get/ids）
     */
    @GetMapping("/get/ids")
    public List<User> listByIds(@RequestParam("idList") List<Long> idList) {
        return userService.listByIds(idList);
    }

    /**
     * 获取当前登录用户，未登录返回 null（网关解析登录态时调用）
     *
     * <p>网关会透传浏览器的 cookie，所以这里能从 user-service 自己的 session 里读到登录态；
     * 复用已有的 {@code getLoginUserPermitNull}，不额外写逻辑。</p>
     */
    @GetMapping("/get/login")
    public User getLoginUser(HttpServletRequest request) {
        return userService.getLoginUserPermitNull(request);
    }
}
