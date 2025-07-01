package com.zbc.controller;


import cn.hutool.core.bean.BeanUtil;
import com.zbc.annotations.AuthCheck;
import com.zbc.constants.UserConstant;
import com.zbc.domain.dto.UserLoginRequest;
import com.zbc.domain.dto.UserRegisterRequest;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.BaseResponse;
import com.zbc.domain.vo.UserLoginVO;
import com.zbc.service.UserService;
import com.zbc.utils.ResultUtils;
import com.zbc.utils.ThrowUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

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
}
