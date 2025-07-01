package com.zbc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.UserLoginVO;

import javax.servlet.http.HttpServletRequest;

public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param account       用户账号
     * @param password      用户密码
     * @param checkPassword 确认密码
     * @return 用户 id
     */
    Long userRegister(String account, String password, String checkPassword);

    /**
     * 加密密码
     *
     * @param password 密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String password);

    /**
     * 用户登录
     *
     * @param userAccount  用户账号
     * @param userPassword 用户密码
     * @param request      登录信息
     * @return 脱敏后的登录信息
     */
    UserLoginVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request 登录信息
     * @return 当前登录用户
     */
    User getCurrentUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request 登录信息
     * @return 是否注销成功
     */
    boolean userLogout(HttpServletRequest request);
}
