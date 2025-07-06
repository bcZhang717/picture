package com.zbc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zbc.domain.dto.space.SpaceAddRequest;
import com.zbc.domain.dto.space.SpaceQueryRequest;
import com.zbc.domain.pojo.Space;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.SpaceVO;
import com.zbc.domain.vo.UserVO;
import com.zbc.enums.SpaceLevelEnum;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.mapper.SpaceMapper;
import com.zbc.service.SpaceService;
import com.zbc.service.UserService;
import com.zbc.utils.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {
    @Resource
    private UserService userService;
    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 构造查询条件
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(spaceLevel != null, "spaceLevel", spaceLevel);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 单个空间封转
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取VO
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    /**
     * 分页获取空间封装
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // spaceList => spaceVOList
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        List<User> users = userService.listByIds(userIdSet);
        Map<Long, List<User>> userIdUserListMap = users.stream().collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }


    /**
     * 空间校验规则
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum enumByValue = SpaceLevelEnum.getEnumByValue(spaceLevel);
        if (add) { // 创建空间
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改空间
        if (spaceLevel != null && enumByValue == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

    /**
     * 根据空间等级填充空间信息
     * 以管理员指定为准
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * 用户创建私有空间
     */
    @Override
    public long addSpace(SpaceAddRequest addRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        String spaceName = addRequest.getSpaceName();
        Integer spaceLevel = addRequest.getSpaceLevel();
        if (StrUtil.isBlank(spaceName)) {
            addRequest.setSpaceName("默认空间");
        }
        if (spaceLevel == null) {
            addRequest.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        Space space = new Space();
        BeanUtil.copyProperties(addRequest, space);
        // 填充信息
        this.fillSpaceBySpaceLevel(space);
        // 数据校验
        this.validSpace(space, true);
        // 2. 非管理员用户只能创建普通级别的空间
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if (SpaceLevelEnum.COMMON.getValue() != addRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "普通用户只能创建指定级别的空间");
        }
        // 3. 一个用户只能创建一个空间(lock + 编程式事务)
        // 保证锁的粒度
        final String LOCK = String.valueOf(userId).intern();
        synchronized (LOCK) {
            Long spaceId = transactionTemplate.execute(status -> {
                // 判断用户是否已经有空间了
                boolean exists = this.lambdaQuery().eq(Space::getUserId, userId).exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "用户已经创建过空间,不能重复创建");
                // 没有才创建
                boolean saved = this.save(space);
                ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建空间失败");
                // 返回新创建的空间 id
                return space.getId();
            });
            return Optional.ofNullable(spaceId).orElse(-1L);
        }
    }
}




