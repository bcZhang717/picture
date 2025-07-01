package com.zbc.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录DTO
 *
 * @author zbc
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 7342236525319284632L;
    /**
     * 账号
     */
    private String userAccount;
    /**
     * 密码
     */
    private String userPassword;
}
