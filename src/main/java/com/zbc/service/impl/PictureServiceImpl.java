package com.zbc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zbc.domain.dto.file.UploadPictureResult;
import com.zbc.domain.dto.picture.*;
import com.zbc.domain.pojo.Picture;
import com.zbc.domain.pojo.Space;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.PictureVO;
import com.zbc.domain.vo.UserVO;
import com.zbc.enums.PictureReviewStatusEnum;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.manage.CosManage;
import com.zbc.manage.upload.FilePictureUpload;
import com.zbc.manage.upload.PictureUploadTemplate;
import com.zbc.manage.upload.UrlPictureUpload;
import com.zbc.mapper.PictureMapper;
import com.zbc.service.PictureService;
import com.zbc.service.SpaceService;
import com.zbc.service.UserService;
import com.zbc.utils.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService {
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private FilePictureUpload filePictureUpload;
    @Resource
    private UrlPictureUpload urlPictureUpload;
    @Resource
    private CosManage cosManage;
    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 图片上传
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest uploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(uploadRequest == null && inputSource == null, ErrorCode.PARAMS_ERROR, "图片为空");
        // 校验空间是否存在
        Long spaceId = uploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 仅空间管理员才有权限
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间图片数量已满");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间大小不足");
            }
        }
        Long pictureId = null;
        pictureId = uploadRequest.getId();
        // 更新图片,id存在
        if (pictureId != null) {
            Picture oldPicture = getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人和管理员可编辑
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 校验空间id是否一致
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    // 没传 spaceId,则使用老图片的(兼容公共图库)
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId, 校验新老图片的 spaceId 是否一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
            // boolean exists = this.lambdaQuery().eq(Picture::getId, pictureId).exists();
            // ThrowUtils.throwIf(!exists, ErrorCode.PARAMS_ERROR, "图片不存在");
        }

        // 上传图片
        // 公共图库: public / userId; 空间: space / spaceId
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库: public / userId
            uploadPathPrefix = String.format("public/%s", loginUser.getId()); // 以用户id划分文件夹
        } else {
            // 空间: space / spaceId
            uploadPathPrefix = String.format("space/%s", spaceId); // 以空间id划分文件夹
        }
        // 根据inputSource选择上传模板
        PictureUploadTemplate template = filePictureUpload;
        if (inputSource instanceof String) {
            template = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = template.uploadPicture(inputSource, uploadPathPrefix);
        // 属性拷贝
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        String picName = uploadPictureResult.getPicName();
        // 支持从外部传入图片名称
        if (uploadRequest != null && StrUtil.isNotBlank(uploadRequest.getPicName())) {
            picName = uploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setSpaceId(spaceId);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        if (pictureId != null) { // 更新
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 填充审核参数
        fillReviewParams(picture, loginUser);
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean saved = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "图片上传失败");
            // 更新空间的额度
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, finalSpaceId)
                    .setSql("totalSize = totalSize + " + picture.getPicSize())
                    .setSql("totalCount = totalCount + 1")
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return picture;
        });

        return PictureVO.objectToVO(picture);
    }

    /**
     * 构造查询条件
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // where (name like '%searchText%' or introduction like '%searchText%') and isDelete = 0;
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 单个图片封转
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取VO
        PictureVO pictureVO = PictureVO.objectToVO(picture);
        // 关联查询
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // pictureList => pictureVOList
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objectToVO).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        List<User> users = userService.listByIds(userIdSet);
        Map<Long, List<User>> userIdUserListMap = users.stream().collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 图片校验规则
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 图片审核
     */
    @Override
    public void pictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum pictureReviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || pictureReviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(pictureReviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断图片是否存在
        Picture picture = this.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 检查审核状态是否重复
        if (picture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片已审核过");
        }
        // 4. 数据库操作
        Picture newPicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, newPicture);
        newPicture.setReviewerId(loginUser.getId());
        newPicture.setReviewTime(new Date());
        boolean updated = this.updateById(newPicture);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 审核参数
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            // 非管理员, 编辑之后都要改为 "待审核"
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }

    }

    /**
     * 批量抓取和创建图片
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest byBatchRequest, User loginUser) {
        ThrowUtils.throwIf(byBatchRequest.getCount() > 30, ErrorCode.PARAMS_ERROR, "最多抓取30条");
        // 拼接爬取路径
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", byBatchRequest.getSearchText());
        Document document = null;
        try {
            // 建立连接
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 成功,解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片失败");
        }
        // List
        Elements imgList = div.select(".iusc");
        // 遍历结果,依次处理
        int count = 0;
        for (Element ele : imgList) {
            String fileUrl = ele.attr("m");
            JSONObject obj = JSONUtil.parseObj(fileUrl);
            fileUrl = obj.getStr("murl");
            if (StrUtil.isBlank(fileUrl)) {
                log.error("图片地址为空, 跳过: {}", fileUrl);
                continue;
            }
            // 处理图片地址,防止转义或COS冲突
            // https://www.baidu.com?a=1&b=2(去掉问号后的内容,包括问号)
            int index = fileUrl.indexOf("?");
            if (index > -1) {
                fileUrl = fileUrl.substring(0, index);
            }
            String namePrefix = byBatchRequest.getNamePrefix();
            if (StrUtil.isBlank(namePrefix)) {
                // 默认使用搜索词
                namePrefix = byBatchRequest.getSearchText();
            }
            PictureUploadRequest request = new PictureUploadRequest();
            request.setFileUrl(fileUrl);
            if (StrUtil.isNotBlank(namePrefix)) {
                request.setPicName(namePrefix + (count + 1));
            }
            // 上传图片
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, request, loginUser);
                log.info("上传图片成功: {}", pictureVO);
                count++;
            } catch (Exception e) {
                log.error("上传图片失败: {}", e.getMessage());
                continue;
            }
            if (count >= byBatchRequest.getCount()) {
                break;
            }
        }
        return count;
    }

    /**
     * 删除图片
     */
    @Async // 异步执行
    @Override
    public void deletePicture(Picture picture) {
        // 1. 先查询图片是否被多次使用
        String url = picture.getUrl();
        Long count = this.lambdaQuery().eq(Picture::getUrl, url).count();
        if (count > 1) {
            // 2. 多次使用, 不清理
            return;
        }
        try {
            // 3. 否则清理图片(压缩图), 并删除缩略图
            String urlPath = new URL(url).getPath();
            cosManage.deleteObject(urlPath);
            String thumbnailUrl = picture.getThumbnailUrl();
            if (StrUtil.isNotBlank(thumbnailUrl)) {
                String thumbnailUrlPath = new URL(thumbnailUrl).getPath();
                cosManage.deleteObject(thumbnailUrlPath);
            }
        } catch (Exception e) {
            log.error("删除图片失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
    }

    /**
     * 检查图片权限
     */
    @Override
    public void checkPictureAuth(Picture picture, User loginUser) {
        Long spaceId = picture.getSpaceId();
        Long userId = loginUser.getId();
        if (spaceId == null) {
            // 公共图库, 仅当前登录用户或管理员可操作
            if (!userId.equals(picture.getUserId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 个人创建的空间, 仅空间的管理员可操作
            if (!picture.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 删除图片
     */
    @Override
    public void deletePicture(Long pictureId, User loginUser) {
        // 判断是否存在
        ThrowUtils.throwIf(pictureId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
/*        // 仅本人或管理员可删除
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }*/
        // 权限校验
        this.checkPictureAuth(oldPicture, loginUser);
        transactionTemplate.execute(status -> {
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的额度
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, oldPicture.getSpaceId())
                    .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                    .setSql("totalCount = totalCount - 1")
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return true;
        });
        // 删除文件
        this.deletePicture(oldPicture);
    }

    /**
     * 编辑图片
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(oldPicture, loginUser);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

}




