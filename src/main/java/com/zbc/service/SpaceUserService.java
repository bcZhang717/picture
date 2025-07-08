package com.zbc.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zbc.domain.dto.spaceuser.SpaceUserAddRequest;
import com.zbc.domain.dto.spaceuser.SpaceUserQueryRequest;
import com.zbc.domain.pojo.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zbc.domain.vo.spaceuser.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 添加空间成员
     *
     * @param spaceUserAddRequest 添加空间成员请求
     * @return 添加的空间成员 id
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 校验空间成员
     *
     * @param spaceUser 空间成员
     * @param add       是否为创建
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 设置查询条件
     *
     * @param spaceUserQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 单个空间成员封装
     *
     * @param spaceUser 空间成员
     * @param request   当前登录用户
     * @return 封装类
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 查询空间封装类列表
     *
     * @param spaceUserList 空间成员列表
     * @return 封装类列表
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
