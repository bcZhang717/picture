package com.zbc.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zbc.domain.pojo.User;
import com.zbc.exception.BusinessException;
import com.zbc.mapper.UserMapper;
import com.zbc.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;

import static com.zbc.enums.UserRoleEnum.USER;
import static com.zbc.exception.ErrorCode.PARAMS_ERROR;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private UserMapper userMapper;

    /**
     * 用户登录
     */
    @Override
    public Long userRegister(String account, String password, String checkPassword) {
        if (StrUtil.hasBlank(account, password, checkPassword)) {
            throw new BusinessException(PARAMS_ERROR, "参数为空");
        }
        // 1. 账号不能小于 4 位
        if (account.length() < 4) {
            throw new BusinessException(PARAMS_ERROR, "账号不能小于4位");
        }
        // 2. 密码不能小于 8 位
        if (password.length() < 8 && checkPassword.length() < 8) {
            throw new BusinessException(PARAMS_ERROR, "密码不能小于8位");
        }
        // 3. 确认密码必须和密码相同
        if (!password.equals(checkPassword)) {
            throw new BusinessException(PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 4. 账号不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", account);
        Long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(PARAMS_ERROR, "账号重复");
        }
        // 5. 加密
        String res = getEncryptPassword(password);
        // 6. 插入数据
        User user = User.builder()
                .userAccount(account)
                .userPassword(res)
                .userName("无名")
                .userRole(USER.getValue())
                .build();
        boolean saved = this.save(user);
        if (!saved) {
            throw new BusinessException(PARAMS_ERROR, "注册失败");
        }
        // 7. 返回用户 id
        return user.getId();
    }

    /**
     * 密码加密
     */
    @Override
    public String getEncryptPassword(String password) {
        final String SALT = "zbc";
        return DigestUtils.md5DigestAsHex((SALT + password).getBytes());
    }
}




