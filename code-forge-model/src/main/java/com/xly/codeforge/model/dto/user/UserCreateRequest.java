package com.xly.codeforge.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户创建请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Data
public class UserCreateRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String username;

    /**
     * 账号
     */
    private String account;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 用户角色: user, admin
     */
    private String role;

    private static final long serialVersionUID = 1L;
}