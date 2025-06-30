package com.zbc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zbc.domain.pojo.User;

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
}
