package com.zbc.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.zbc.annotations.AuthCheck;
import com.zbc.api.imagesearch.ImageSearchApiFacade;
import com.zbc.api.imagesearch.model.ImageSearchResult;
import com.zbc.constants.UserConstant;
import com.zbc.domain.dto.DeleteRequest;
import com.zbc.domain.dto.picture.*;
import com.zbc.domain.pojo.Picture;
import com.zbc.domain.pojo.Space;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.BaseResponse;
import com.zbc.domain.vo.PictureTagCategory;
import com.zbc.domain.vo.PictureVO;
import com.zbc.enums.PictureReviewStatusEnum;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.service.PictureService;
import com.zbc.service.SpaceService;
import com.zbc.service.UserService;
import com.zbc.utils.ResultUtils;
import com.zbc.utils.ThrowUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/picture")
@Api(tags = "图片管理接口")
public class PictureController {
    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SpaceService spaceService;
    /**
     * 构造本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024) // 初始容量
            .maximumSize(10000L) // 最大容量
            .expireAfterWrite(5L, TimeUnit.MINUTES) // 缓存有效期 5 分钟
            .build();

    /**
     * 文件上传(本地上传)
     *
     * @param multipartFile 要上传的文件
     * @param uploadRequest 上传参数DTO
     * @param request       获取当前登录用户
     * @return PictureVO
     */
    @ApiOperation(value = "上传图片(本地上传)")
    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile, PictureUploadRequest uploadRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User currentUser = userService.getCurrentUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, uploadRequest, currentUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 文件上传(url上传)
     *
     * @param uploadRequest 上传参数DTO
     * @param request       获取当前登录用户
     * @return PictureVO
     */
    @ApiOperation(value = "上传图片(url 上传)")
    @PostMapping("/upload/url")
    @ApiOperationSupport(order = 2) // 排序为2
    public BaseResponse<PictureVO> uploadPictureByUrl(@RequestBody PictureUploadRequest uploadRequest, HttpServletRequest request) {
        // 获取当前登录用户
        User currentUser = userService.getCurrentUser(request);
        String fileUrl = uploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, uploadRequest, currentUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     *
     * @param deleteRequest id
     * @param request       获取当前登录用户
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @ApiOperation(value = "删除图片")
    @ApiOperationSupport(order = 1) // 排序为1
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getCurrentUser(request);
        long id = deleteRequest.getId();
        pictureService.deletePicture(id, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片(管理员)
     *
     * @param pictureUpdateRequest 图片基本信息封装
     * @param request              获取当前登录用户
     * @return 是否更新成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        User currentUser = userService.getCurrentUser(request);
        pictureService.fillReviewParams(oldPicture, currentUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片(管理员)
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片(用户)
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            User currentUser = userService.getCurrentUser(request);
            pictureService.checkPictureAuth(picture, currentUser);
        }
        // 获取封装类
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页获取图片列表(管理员)
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表(用户)
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库
            // 普通用户默认只能看到审核通过的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User currentUser = userService.getCurrentUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!currentUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 分页获取图片列表(用户, 带redis缓存)
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 1. 先查询缓存, 缓存中没有再查询数据库
        // 构造key
        String str = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(str.getBytes());
        final String key = "picture:listPictureVOByPage:" + hashKey;
        // redis查询到的数据
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value != null) {
            // 缓存中存在数据, 直接返回
            Page<PictureVO> page = JSONUtil.toBean(value, Page.class);
            return ResultUtils.success(page);
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 2. 查询数据库之后, 写入到redis
        String jsonStr = JSONUtil.toJsonStr(pictureVOPage);
        // 随机TTL为300-600秒
        int timeout = 300 + RandomUtil.randomInt(0, 300);
        stringRedisTemplate.opsForValue().set(key, jsonStr, timeout, TimeUnit.SECONDS);
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 分页获取图片列表(用户, 带本地缓存)
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache/local")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithLocalCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 1. 先查询缓存, 缓存中没有再查询数据库
        // 构造key
        String str = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(str.getBytes());
        final String key = "listPictureVOByPage:" + hashKey;
        // 从本地化缓存中查询
        String value = LOCAL_CACHE.getIfPresent(key);
        if (value != null) {
            // 缓存中存在数据, 直接返回
            Page<PictureVO> page = JSONUtil.toBean(value, Page.class);
            return ResultUtils.success(page);
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 2. 查询数据库之后, 写入到本地缓存
        String jsonStr = JSONUtil.toJsonStr(pictureVOPage);
        LOCAL_CACHE.put(key, jsonStr);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 分页获取图片列表(用户, 多级缓存)
     */
    @Deprecated
    @PostMapping("/list/page/vo/many")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithMany(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 1. 先查询本地缓存, 缓存中再查询分布式redis缓存, 没有再查询数据库
        // 构造key
        String str = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(str.getBytes());
        final String key = "picture:listPictureVOByPage:" + hashKey;
        // 查询本地缓存
        String value = LOCAL_CACHE.getIfPresent(key);
        if (value != null) {
            // 缓存中存在数据, 直接返回
            Page<PictureVO> page = JSONUtil.toBean(value, Page.class);
            return ResultUtils.success(page);
        }
        // 本地缓存没有, 查询分布式redis缓存
        value = stringRedisTemplate.opsForValue().get(key);
        if (value != null) {
            // 缓存中存在数据, 写入到本地缓存, 直接返回
            LOCAL_CACHE.put(key, value);
            Page<PictureVO> page = JSONUtil.toBean(value, Page.class);
            return ResultUtils.success(page);
        }
        // 都没有, 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 2. 查询数据库之后, 写入到本地缓存和redis
        String jsonStr = JSONUtil.toJsonStr(pictureVOPage);
        // 随机TTL为300-600秒
        int timeout = 300 + RandomUtil.randomInt(0, 300);
        stringRedisTemplate.opsForValue().set(key, jsonStr, timeout, TimeUnit.SECONDS);
        LOCAL_CACHE.put(key, jsonStr);
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 编辑图片(用户)
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User currentUser = userService.getCurrentUser(request);
        pictureService.editPicture(pictureEditRequest, currentUser);
        return ResultUtils.success(true);
    }

    /**
     * 获取预制标签和分类
     *
     * @return 预制标签和分类
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 图片审核(admin)
     *
     * @param pictureReviewRequest DTO
     * @param request              当前登录用户
     * @return 是否审核成功
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> pictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User currentUser = userService.getCurrentUser(request);
        pictureService.pictureReview(pictureReviewRequest, currentUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量抓取图片
     *
     * @param byBatchRequest DTO
     * @param request        当前登录用户
     * @return 抓取数量
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest byBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(byBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User currentUser = userService.getCurrentUser(request);
        int count = pictureService.uploadPictureByBatch(byBatchRequest, currentUser);
        return ResultUtils.success(count);
    }


    /**
     * 以图搜图请求
     *
     * @param searchPictureByPictureRequest 请求参数
     * @return 图片信息
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        String url = oldPicture.getUrl();
        if (!url.toLowerCase().endsWith(".jpg") && !url.toLowerCase().endsWith(".png") && !url.toLowerCase().endsWith(".jpeg")) {
            url = url + "?imageMogr2/format/png"; // 添加格式转换
        }
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(url);
        return ResultUtils.success(resultList);
    }

    /**
     * 以色搜图请求
     *
     * @param searchPictureByColorRequest 请求参数
     * @param request                     当前登录用户
     * @return 图片信息
     */
    @ApiOperation(value = "以色搜图")
    @ApiOperationSupport(order = 3)
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        String picColor = searchPictureByColorRequest.getPicColor();
        User currentUser = userService.getCurrentUser(request);
        List<PictureVO> pictureVOList = pictureService.searchPictureByColor(spaceId, picColor, currentUser);
        return ResultUtils.success(pictureVOList);
    }
}
