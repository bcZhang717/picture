package com.zbc.controller;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zbc.annotations.AuthCheck;
import com.zbc.constants.UserConstant;
import com.zbc.domain.dto.DeleteRequest;
import com.zbc.domain.dto.UserLoginRequest;
import com.zbc.domain.dto.UserRegisterRequest;
import com.zbc.domain.dto.user.UserAddRequest;
import com.zbc.domain.dto.user.UserQueryRequest;
import com.zbc.domain.dto.user.UserUpdateRequest;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.BaseResponse;
import com.zbc.domain.vo.UserLoginVO;
import com.zbc.domain.vo.UserVO;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.service.UserService;
import com.zbc.utils.ResultUtils;
import com.zbc.utils.ThrowUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.List;

import static com.zbc.exception.ErrorCode.NOT_FOUND_ERROR;
import static com.zbc.exception.ErrorCode.PARAMS_ERROR;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 用户注册
     *
     * @param request 用户注册DTO
     * @return 用户 id
     */
    @PostMapping("/register")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest request) {
        ThrowUtils.throwIf(request == null, PARAMS_ERROR);
        String account = request.getUserAccount();
        String password = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        Long userId = userService.userRegister(account, password, checkPassword);
        return ResultUtils.success(userId);
    }

    /**
     * 用户登录
     *
     * @param loginRequest 用户登录DTO
     * @param request      用户信息
     * @return 脱敏后的用户信息
     */
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> userLogin(@RequestBody UserLoginRequest loginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(loginRequest == null, PARAMS_ERROR);
        String userAccount = loginRequest.getUserAccount();
        String userPassword = loginRequest.getUserPassword();
        UserLoginVO userLoginVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(userLoginVO);
    }

    /**
     * 获取当前登录用户
     *
     * @param request 用户信息
     * @return 脱敏后的用户信息
     */
    @GetMapping("get/login")
    public BaseResponse<UserLoginVO> getLoginUser(HttpServletRequest request) {
        User currentUser = userService.getCurrentUser(request);
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(currentUser, userLoginVO);
        return ResultUtils.success(userLoginVO);
    }

    /**
     * 用户注销
     *
     * @param request 用户信息
     * @return 是否注销成功
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        boolean isSuccess = userService.userLogout(request);
        return ResultUtils.success(isSuccess);
    }

    /**
     * 创建用户(管理员权限)
     *
     * @param request 用户信息
     * @return 用户 id
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest request) {
        ThrowUtils.throwIf(request == null, PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(request, user);
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean saved = userService.save(user);
        ThrowUtils.throwIf(!saved, PARAMS_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据id获取用户(管理员权限)
     *
     * @param id 用户id
     * @return 用户信息
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据id删除用户(管理员权限)
     *
     * @param deleteRequest 删除参数
     * @return 删除结果
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, PARAMS_ERROR);
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 根据id查询用户信息
     *
     * @param id 用户id
     * @return 脱敏后的用户信息
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVO(@RequestParam Long id) {
        if (id <= 0) {
            throw new BusinessException(PARAMS_ERROR);
        }
        BaseResponse<User> userById = getUserById(id);
        User user = userById.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 修改用户(管理员权限)
     *
     * @param userUpdateRequest 修改用户请求
     * @return 修改用户结果
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null) {
            throw new BusinessException(PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean updated = userService.updateById(user);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页查询(管理员权限)
     *
     * @param userQueryRequest 查询条件
     * @return 用户列表
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = userQueryRequest.getCurrent();
        int pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize), userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = userService.listUserVO(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }
}
