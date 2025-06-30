package com.zbc.controller;

import com.zbc.domain.request.UserRegisterRequest;
import com.zbc.domain.response.BaseResponse;
import com.zbc.exception.ErrorCode;
import com.zbc.service.UserService;
import com.zbc.utils.ResultUtils;
import com.zbc.utils.ThrowUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static com.zbc.exception.ErrorCode.*;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 用户注册
     *
     * @param request 用户注册请求
     * @return 用户 id
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest request) {
        ThrowUtils.throwIf(request == null, PARAMS_ERROR);
        String account = request.getUserAccount();
        String password = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        Long userId = userService.userRegister(account, password, checkPassword);
        return ResultUtils.success(userId);
    }
}
