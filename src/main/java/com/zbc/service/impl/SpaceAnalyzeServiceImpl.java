package com.zbc.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zbc.domain.dto.space.analyze.*;
import com.zbc.domain.pojo.Picture;
import com.zbc.domain.pojo.Space;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.space.analyze.*;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.mapper.SpaceMapper;
import com.zbc.service.PictureService;
import com.zbc.service.SpaceAnalyzeService;
import com.zbc.service.SpaceService;
import com.zbc.service.UserService;
import com.zbc.utils.ThrowUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceAnalyzeService {

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    /**
     * 获取空间使用情况
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        if (queryAll || queryPublic) {
            // 2. 全空间分析 / 公共图库分析 ===> Picture 表
            // 权限校验, 仅管理员使用
            checkSpaceAuth(spaceAnalyzeRequest, loginUser);
            // 获取查询条件
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize"); // 指定字段, 优化
            fillAnalyzeQueryWrapper(spaceAnalyzeRequest, queryWrapper);
            // 其实列表里就是图片大小一个字段
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            // 统计已使用的大小
            long usedSize = pictureObjList.stream().mapToLong(obj -> (Long) obj).sum();
            // 统计已使用的数量
            long usedCount = pictureObjList.size();
            // 封装返回结果
            SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
            response.setUsedSize(usedSize);
            response.setUsedCount(usedCount);
            // 全空间分析 / 公共图库分析容量没有上限
            response.setMaxSize(null);
            response.setSizeUsageRatio(null);
            response.setMaxCount(null);
            response.setCountUsageRatio(null);
            return response;
        } else {
            // 3. 个人空间分析 ===> Space 表
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 权限校验
            checkSpaceAuth(spaceAnalyzeRequest, loginUser);
            // 封装返回结果
            SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
            response.setUsedSize(space.getTotalSize());
            response.setUsedCount(space.getTotalCount());
            // 全空间分析 / 公共图库分析容量没有上限
            response.setMaxSize(space.getMaxSize());
            response.setMaxCount(space.getMaxCount());
            double usedSize = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            double usedCount = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            response.setSizeUsageRatio(usedSize);
            response.setCountUsageRatio(usedCount);
            return response;
        }
    }

    /**
     * 空间图片分类分析
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest categoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(categoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 检查权限
        checkSpaceAuth(categoryAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(categoryAnalyzeRequest, queryWrapper);
        // 构造查询条件: 根据分类进行分组查询
        queryWrapper.select("category", "count(*) as count", "sum(picSize) as totalSize").groupBy("category");
        // 转换结果为List<SpaceCategoryAnalyzeResponse>
        return pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result -> {
                    String category = result.get("category").toString();
                    long count = Long.parseLong((String) result.get("count"));
                    long totalSize = Long.parseLong((String) result.get("totalSize"));
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                }).collect(Collectors.toList());
    }

    /**
     * 空间图片标签分析
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest tagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(tagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 检查权限
        checkSpaceAuth(tagAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(tagAnalyzeRequest, queryWrapper);

        // 1. 查询所有符合条件的标签
        queryWrapper.select("tas");
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                .map(Object::toString)
                .collect(Collectors.toList());

        // 2. 合并所有标签并统计使用次数(k, v : 标签名, 使用次数)
        // 扁平化: ["java", "python"], ["java", "php"] ===> ["java", "python", "php"]
        Map<String, Long> tagsCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        // 3. 转换为响应对象, 按使用次数降序排序
        return tagsCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 降序排序
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 空间图片大小分析
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 检查权限
        checkSpaceAuth(spaceSizeAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        queryWrapper.select("picSize");
        List<Long> picSizeList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .map(size -> (Long) size)
                .collect(Collectors.toList());
        // 定义分段范围, 使用有序的Map(k, v : "图片范围(大小)", 符合条件的图片数量)
        Map<String, Long> sizeRangeMap = new LinkedHashMap<>();
        sizeRangeMap.put("<100KB", picSizeList.stream().filter(size -> size < 100 * 1024).count());
        sizeRangeMap.put("100KB - 500KB", picSizeList.stream().filter(size -> size > 100 * 1024 && size < 500 * 1024).count());
        sizeRangeMap.put("500KB - 1MB", picSizeList.stream().filter(size -> size > 500 * 1024 && size < 1024 * 1024).count());
        sizeRangeMap.put(">1MB", picSizeList.stream().filter(size -> size > 1024 * 1024).count());
        // 转换为响应对象
        return sizeRangeMap.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 用户上传行为分析
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 检查权限
        checkSpaceAuth(spaceUserAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
        // 时间维度: 按天、按周、按月
        String dimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (dimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') as period", "count(*) as count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) as period", "count(*) as count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') as period", "count(*) as count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间维度错误");
        }
        // 分组并排序
        queryWrapper.groupBy("period").orderByDesc("period");
        // 查询结果并转换
        return pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    Long count = Long.parseLong(result.get("count").toString());
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());
    }


    /**
     * 空间分析的权限校验
     *
     * @param request   请求
     * @param loginUser 登录用户
     */
    private void checkSpaceAuth(SpaceAnalyzeRequest request, User loginUser) {
        boolean queryPublic = request.isQueryPublic();
        boolean queryAll = request.isQueryAll();
        // 1. 全空间分析、公共图库分析仅管理员可用
        if (queryAll || queryPublic) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "仅管理员可进行全空间分析");
        } else {
            // 2. 个人空间分析仅空间创建人或管理员可用
            Long spaceId = request.getSpaceId();
            if (spaceId != null) {
                Space space = spaceService.getById(spaceId);
                ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
                ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "仅空间创建人或管理员可用");
            }
        }
    }

    /**
     * 根据分析的条件构建不同的查询对象
     *
     * @param request 请求
     * @param wrapper 查询对象
     */
    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest request, QueryWrapper<Picture> wrapper) {
        boolean queryAll = request.isQueryAll();
        if (queryAll) {
            // 全空间分析(admin), 无查询条件
            return;
        }
        boolean queryPublic = request.isQueryPublic();
        Long spaceId = request.getSpaceId();
        if (queryPublic) {
            // 公共图库分析(admin), 查询条件为spaceId == null
            wrapper.isNull("spaceId");
            return;
        }
        if (spaceId != null) {
            wrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "请指定查询范围");
    }
}





