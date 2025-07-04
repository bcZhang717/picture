package com.zbc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zbc.domain.dto.file.UploadPictureResult;
import com.zbc.domain.dto.picture.PictureQueryRequest;
import com.zbc.domain.dto.picture.PictureReviewRequest;
import com.zbc.domain.dto.picture.PictureUploadByBatchRequest;
import com.zbc.domain.dto.picture.PictureUploadRequest;
import com.zbc.domain.pojo.Picture;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.PictureVO;
import com.zbc.domain.vo.UserVO;
import com.zbc.enums.PictureReviewStatusEnum;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.manage.FileManage;
import com.zbc.manage.upload.FilePictureUpload;
import com.zbc.manage.upload.PictureUploadTemplate;
import com.zbc.manage.upload.UrlPictureUpload;
import com.zbc.mapper.PictureMapper;
import com.zbc.service.PictureService;
import com.zbc.service.UserService;
import com.zbc.utils.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService {
    @Resource
    FileManage fileManage;
    @Resource
    private UserService userService;
    @Resource
    private FilePictureUpload filePictureUpload;
    @Resource
    private UrlPictureUpload urlPictureUpload;

    /**
     * 图片上传
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest uploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Long pictureId = null;
        if (uploadRequest != null) {
            pictureId = uploadRequest.getId();
        }
        // 更新图片,id存在
        if (pictureId != null) {
            Picture picture = getById(pictureId);
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人和管理员可编辑
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // boolean exists = this.lambdaQuery().eq(Picture::getId, pictureId).exists();
            // ThrowUtils.throwIf(!exists, ErrorCode.PARAMS_ERROR, "图片不存在");
        }

        // 上传图片
        String uploadPathPrefix = String.format("picture/%s", loginUser.getId()); // 以用户id划分文件夹
        PictureUploadTemplate template = filePictureUpload;
        if (inputSource instanceof String) {
            template = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = template.uploadPicture(inputSource, uploadPathPrefix);
        // 属性拷贝
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        String picName = uploadPictureResult.getPicName();
        // 支持从外部传入图片名称
        if (uploadRequest != null && StrUtil.isNotBlank(uploadPictureResult.getPicName())) {
            picName = uploadPictureResult.getPicName();
        }
        picture.setName(picName);
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
        boolean saved = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "图片上传失败");
        return PictureVO.objectToVO(picture);
    }

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
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }

        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
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
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objectToVO).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

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

    @Override
    public void pictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        // 2. 判断图片是否存在
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum pictureReviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || pictureReviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(pictureReviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
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
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 成功,解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片失败");
        }
        // List
        Elements imgList = div.select(".iusc");
        // 遍历结果,依次处理
        int count = 0;
        for (Element ele : imgList) {
            String fileUrl = ele.attr("m");
            JSONObject mObj = new  JSONObject(fileUrl);
            fileUrl = mObj.get("murl").toString();
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
            PictureUploadRequest request = new PictureUploadRequest();
            request.setFileUrl(fileUrl);
            String namePrefix = byBatchRequest.getNamePrefix();
            if (StrUtil.isBlank(namePrefix)) {
                // 默认使用搜索词
                namePrefix = byBatchRequest.getSearchText();
            }
            request.setPicName(namePrefix + (count + 1));
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
}




