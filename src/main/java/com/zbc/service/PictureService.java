package com.zbc.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zbc.domain.dto.picture.PictureQueryRequest;
import com.zbc.domain.dto.picture.PictureReviewRequest;
import com.zbc.domain.dto.picture.PictureUploadByBatchRequest;
import com.zbc.domain.dto.picture.PictureUploadRequest;
import com.zbc.domain.pojo.Picture;
import com.zbc.domain.pojo.User;
import com.zbc.domain.vo.PictureVO;

import javax.servlet.http.HttpServletRequest;


public interface PictureService extends IService<Picture> {
    /**
     * 上传图片
     *
     * @param inputSource   文件输入源
     * @param uploadRequest 上传参数
     * @param loginUser     当前登录用户
     * @return 图片视图对象
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest uploadRequest, User loginUser);

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest 查询参数
     * @return 查询条件
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取单个图片
     *
     * @param picture 图片对象
     * @return 图片视图对象
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取分页图片列表
     *
     * @param picturePage 图片分页对象
     * @return 图片视图分页对象
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 图片校验规则
     *
     * @param picture 图片对象
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核参数
     * @param loginUser            当前登录用户
     */
    void pictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 补充审核参数
     *
     * @param picture   图片对象
     * @param loginUser 当前登录用户
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param byBatchRequest 图片批量上传参数
     * @param loginUser      当前登录用户
     * @return 陈工创建的图片数量
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest byBatchRequest, User loginUser);

    /**
     * 图片清理
     *
     * @param picture 图片对象
     */
    void deletePicture(Picture picture);
}
