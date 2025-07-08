package com.zbc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zbc.domain.dto.space.analyze.*;
import com.zbc.domain.pojo.Space;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.space.analyze.*;

import java.util.List;


public interface SpaceAnalyzeService extends IService<Space> {
    /**
     * 获取空间使用情况
     *
     * @param spaceAnalyzeRequest 获取空间使用情况请求
     * @param loginUser           登录用户
     * @return 空间使用情况
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser);

    /**
     * 空间图片分类分析
     *
     * @param categoryAnalyzeRequest 获取空间图片分类分析请求
     * @param loginUser              登录用户
     * @return 图片分类分析结果
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest categoryAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片标签分析
     *
     * @param tagAnalyzeRequest 获取空间图片标签分析请求
     * @param loginUser         登录用户
     * @return 图片标签分析结果
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest tagAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片大小分析
     *
     * @param spaceSizeAnalyzeRequest 获取空间图片大小分析请求
     * @param loginUser               登录用户
     * @return 图片大小分析结果
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 获取空间用户分析
     *
     * @param spaceUserAnalyzeRequest 获取空间用户分析请求
     * @param loginUser               登录用户
     * @return 用户分析结果
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

}
