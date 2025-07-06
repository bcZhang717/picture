package com.zbc.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zbc.domain.dto.space.SpaceAddRequest;
import com.zbc.domain.dto.space.SpaceQueryRequest;
import com.zbc.domain.pojo.Space;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;


public interface SpaceService extends IService<Space> {
    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest 查询参数
     * @return 查询条件
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 获取单个空间
     *
     * @param space 空间对象
     * @return 空间视图对象
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取分页空间列表
     *
     * @param spacePage 空间分页对象
     * @return 空间视图分页对象
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 空间校验规则
     *
     * @param space 空间对象
     */
    void validSpace(Space space, boolean add);

    /**
     * 根据空间等级填充空间信息
     *
     * @param space 空间对象
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 用户创建私有空间
     *
     * @param addRequest 创建参数
     * @param loginUser  登录用户
     * @return 创建的空间ID
     */
    long addSpace(SpaceAddRequest addRequest, User loginUser);
}
