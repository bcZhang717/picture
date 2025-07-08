package com.zbc.controller;

import com.zbc.domain.dto.space.analyze.*;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.BaseResponse;
import com.zbc.domain.vo.space.analyze.*;
import com.zbc.exception.ErrorCode;
import com.zbc.service.SpaceAnalyzeService;
import com.zbc.service.SpaceService;
import com.zbc.service.UserService;
import com.zbc.utils.ResultUtils;
import com.zbc.utils.ThrowUtils;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space")
@Api(tags = "空间分析接口")
public class SpaceAnalyzeController {
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    SpaceAnalyzeService spaceAnalyzeService;

    /**
     * 空间状态分析
     *
     * @param spaceAnalyzeRequest 状态分析请求
     * @param request             当前登录用户
     * @return 状态分析结果
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User currentUser = userService.getCurrentUser(request);
        SpaceUsageAnalyzeResponse spaceUsageAnalyze = spaceAnalyzeService.getSpaceUsageAnalyze(spaceAnalyzeRequest, currentUser);
        return ResultUtils.success(spaceUsageAnalyze);
    }

    /**
     * 空间图片分类分析
     *
     * @param spaceCategoryAnalyzeRequest 分类分析请求
     * @param request                     当前登录用户
     * @return 分类分析结果
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(@RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getCurrentUser(request);
        List<SpaceCategoryAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceCategoryAnalyze(spaceCategoryAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 获取空间图片标签分析
     *
     * @param spaceTagAnalyzeRequest 标签分析请求
     * @param request                当前登录用户
     * @return 标签分析结果
     */
    @PostMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> getSpaceTagAnalyze(@RequestBody SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getCurrentUser(request);
        List<SpaceTagAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceTagAnalyze(spaceTagAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 空间图片大小分析
     */
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyze(@RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getCurrentUser(request);
        List<SpaceSizeAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceSizeAnalyze(spaceSizeAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 用户行为分析
     *
     * @param spaceUserAnalyzeRequest 用户行为分析请求
     * @param request                 当前登录用户
     * @return 用户行为分析结果
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyze(@RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getCurrentUser(request);
        List<SpaceUserAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceUserAnalyze(spaceUserAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }


}
