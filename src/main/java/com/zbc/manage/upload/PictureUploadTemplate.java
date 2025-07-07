package com.zbc.manage.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.zbc.config.CosClientConfig;
import com.zbc.domain.dto.file.UploadPictureResult;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.manage.CosManage;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * 图片上传模版
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private CosManage cosManage;

    /**
     * 上传图片
     *
     * @param inputSource      文件
     * @param uploadPathPrefix 上传图片前缀(用户指定)
     * @return 上传图片结果
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片
        // TODO: 不同
        validPicture(inputSource);
        // 2. 图片上传地址(拼接文件路径)
        String uuid = RandomUtil.randomString(10); // 随机生成一个10位字符串
        String date = DateUtil.formatDate(new Date()); // 获取当前日期
        // TODO: 不同
        String originalFilename = getOriginalFilename(inputSource); // 原始文件
        String suffix = FileUtil.getSuffix(originalFilename); // 原始文件后缀
        // 最终拼接文件路径: 日期_uuid.文件后缀
        StrUtil.blankToDefault(suffix, "jpg"); // 如果url没有后缀表明文件类型, 默认为jpg
        String uploadFileName = String.format("%s_%s.%s", date, uuid, suffix);
        // 上传路径
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        File file = null;
        try {
            // 3. 创建临时文件, 获取文件到服务器
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源
            // TODO: 不同
            processFile(inputSource, file);
            // 4. 上传文件到COS
            PutObjectResult putObjectResult = cosManage.putPictureObject(uploadPath, file);
            // 5. 获取图片信息, 封装返回结果
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 获取图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                CIObject compressCiObject = objectList.get(0); // 获取压缩后的图片信息
                // 没有缩略图, 默认为压缩图
                CIObject thumbnailCiObject = compressCiObject;
                // 有缩略图, 获取缩略图
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1); // 获取缩略图信息
                }
                // 封装压缩图与缩略图的返回结果
                return buildResult(originalFilename, compressCiObject, thumbnailCiObject);
            }
            return buildResult(originalFilename, file, uploadPath, imageInfo);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 删除临时文件
            deleteTempFile(file);
        }

    }

    /**
     * 封装返回结果(压缩、缩略图)
     *
     * @param originalFilename  原始文件名
     * @param compressCiObject  压缩图对象
     * @param thumbnailCiObject 缩略图对象
     * @return 上传图片结果
     */
    private UploadPictureResult buildResult(String originalFilename, CIObject compressCiObject, CIObject thumbnailCiObject) {

        int width = compressCiObject.getWidth();
        int height = compressCiObject.getHeight();
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        // 设置原图压缩后的地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressCiObject.getKey());
        // 设置缩略图地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(compressCiObject.getSize().longValue());
        uploadPictureResult.setPicWidth(width);
        uploadPictureResult.setPicHeight(height);
        uploadPictureResult.setPicFormat(compressCiObject.getFormat());
        // TODO: new 对象?
        ImageInfo imageInfo = new ImageInfo();
        uploadPictureResult.setPicColor(imageInfo.getAve());
        // 计算宽高比
        double scale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
        uploadPictureResult.setPicScale(scale);
        return uploadPictureResult;
    }

    /**
     * 校验输入源
     *
     * @param inputSource 本地文件或url
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     *
     * @param inputSource 本地文件或url
     * @return 原始文件名
     */
    protected abstract String getOriginalFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     *
     * @param inputSource 本地文件或url
     */
    protected abstract void processFile(Object inputSource, File file) throws IOException;


    /**
     * 封转返回结果
     *
     * @param originalFilename 原始文件名
     * @param file             本地文件
     * @param uploadPath       上传路径
     * @param imageInfo        COS返回的图片信息
     * @return 上传图片结果
     */
    private UploadPictureResult buildResult(String originalFilename, File file, String uploadPath, ImageInfo imageInfo) {
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        uploadPictureResult.setPicName(FileUtil.getName(originalFilename));
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicWidth(width);
        uploadPictureResult.setPicHeight(height);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());
        // 计算宽高比
        double scale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
        uploadPictureResult.setPicScale(scale);
        return uploadPictureResult;
    }

    /**
     * 删除临时文件
     *
     * @param file 文件
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        boolean deleted = file.delete();
        if (!deleted) {
            log.error("文件删除失败: {}", file.getAbsolutePath());
        }
    }
}
