package com.zbc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zbc.constants.UserConstant;
import com.zbc.domain.dto.user.UserQueryRequest;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.UserLoginVO;
import com.zbc.domain.vo.UserVO;
import com.zbc.enums.UserRoleEnum;
import com.zbc.exception.BusinessException;
import com.zbc.mapper.UserMapper;
import com.zbc.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.zbc.enums.UserRoleEnum.USER;
import static com.zbc.exception.ErrorCode.*;

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
            throw new BusinessException(SYSTEM_ERROR, "注册失败");
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

    /**
     * 用户登录
     */
    @Override
    public UserLoginVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 字段校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(PARAMS_ERROR, "参数为空");
        }
        // 账号不能小于 4 位
        if (userAccount.length() < 4) {
            throw new BusinessException(PARAMS_ERROR, "账号不能小于4位");
        }
        // 密码不能小于 8 位
        if (userPassword.length() < 8) {
            throw new BusinessException(PARAMS_ERROR, "密码不能小于8位");
        }
        // 2. 密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(PARAMS_ERROR, "用户账号或密码输入错误");
        }
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(user, userLoginVO);
        return userLoginVO;
    }

    /**
     * 获取当前登录用户
     */
    @Override
    public User getCurrentUser(HttpServletRequest request) {
        // 判断当前用户是否登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(NOT_LOGIN_ERROR, "用户未登录");
        }
        Long userId = currentUser.getId();
        currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            throw new BusinessException(NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 用户注销
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 判断当前用户是否登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(NOT_LOGIN_ERROR, "用户未登录");
        }
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取脱敏后的UserLoginVO
     */
    @Override
    public UserLoginVO getLoginUserVO(User user) {
        if (user == null) {
            throw new BusinessException(PARAMS_ERROR);
        }
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(user, userLoginVO);
        return userLoginVO;
    }

    /**
     * 获取脱敏后的UserVO
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            throw new BusinessException(PARAMS_ERROR);
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏后的用户列表
     */
    @Override
    public List<UserVO> listUserVO(List<User> userList) {
        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    /**
     * 封装查询条件
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNull(id), "id", id);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;

    }

    /**
     * 判断是否为管理员
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }
}