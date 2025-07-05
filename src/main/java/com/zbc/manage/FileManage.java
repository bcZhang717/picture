package com.zbc.manage;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.zbc.config.CosClientConfig;
import com.zbc.domain.dto.file.UploadPictureResult;
import com.zbc.exception.BusinessException;
import com.zbc.exception.ErrorCode;
import com.zbc.utils.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 贴合业务的文件处理类
 */
@Component
@Slf4j
@Deprecated
public class FileManage {

    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private CosManage cosManage;

    /**
     * 上传图片(本地上传)
     *
     * @param multipartFile    上传的图片
     * @param uploadPathPrefix 上传图片前缀(用户指定)
     * @return 图片基本信息, 用于解析图片结果
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 1. 校验图片
        validPicture(multipartFile);
        // 2. 拼接文件路径(date_uuid.suffix)
        String uuid = RandomUtil.randomString(10); // 随机生成一个10位字符串
        String date = DateUtil.formatDate(new Date()); // 获取当前日期
        String originalFilename = multipartFile.getOriginalFilename(); // 原始文件名
        String suffix = FileUtil.getSuffix(originalFilename); // 原始文件后缀
        // 最终拼接文件路径: 日期_uuid.文件后缀
        String uploadFileName = String.format("%s_%s.%s", date, uuid, suffix);
        // 3. 上传路径
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        // 4. 删除临时文件
        File file = null;
        try {
            // 创建临时文件, uploadPath为前缀
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            // 上传文件到COS
            PutObjectResult putObjectResult = cosManage.putPictureObject(uploadPath, file);
            // 通过数据万象获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            int width = imageInfo.getWidth();
            int height = imageInfo.getHeight();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(width);
            uploadPictureResult.setPicHeight(height);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            // 计算宽高比
            double scale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
            uploadPictureResult.setPicScale(scale);
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 删除临时文件
            deleteTempFile(file);
        }
    }

    /**
     * 校验图片(本地上传)
     *
     * @param multipartFile 上传的文件
     */
    private void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1. 获取文件大小
        long fileSize = multipartFile.getSize();
        // 2. 指定文件大小限制
        final long ONE_M = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过2M");
        // 3. 获取文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许的文件类型
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpg", "jpeg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    /**
     * 删除临时文件(本地上传、url上传)
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

    /**
     * 上传图片(url上传)
     *
     * @param fileUrl          要上传的文件url
     * @param uploadPathPrefix 上传图片前缀(用户指定)
     * @return 上传图片结果
     */
    public UploadPictureResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix) {
        // 1. 校验图片
        // validPicture(multipartFile);
        validPicture(fileUrl);
        // 2. 拼接文件路径
        String uuid = RandomUtil.randomString(10); // 随机生成一个10位字符串
        String date = DateUtil.formatDate(new Date()); // 获取当前日期
        // String originalFilename = multipartFile.getOriginalFilename(); // 原始文件
        String originalFilename = FileUtil.mainName(fileUrl);
        String suffix = FileUtil.getSuffix(originalFilename); // 原始文件后缀
        // 最终拼接文件路径: 日期_uuid.文件后缀
        String uploadFileName = String.format("%s_%s.%s", date, uuid, suffix);
        // 3. 上传路径
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        // 4. 删除临时文件
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            // multipartFile.transferTo(file);
            // 下载文件
            HttpUtil.downloadFile(fileUrl, file);
            PutObjectResult putObjectResult = cosManage.putPictureObject(uploadPath, file);
            // 获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            int width = imageInfo.getWidth();
            int height = imageInfo.getHeight();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            uploadPictureResult.setPicName(FileUtil.getName(originalFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(width);
            uploadPictureResult.setPicHeight(height);
            // 计算宽高比
            double scale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
            uploadPictureResult.setPicScale(scale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            deleteTempFile(file);
        }

    }

    /**
     * 校验图片(url上传)
     *
     * @param fileUrl 图片url
     */
    private void validPicture(String fileUrl) {
        ThrowUtils.throwIf(fileUrl == null, ErrorCode.PARAMS_ERROR, "图片url不能为空");
        // 1. 验证url格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        // 2. 验证url协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"), ErrorCode.PARAMS_ERROR, "文件地址格式不支持");
        // 3. 验证url是否存在(head请求)
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            // 不支持head请求
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 4. 验证文件的类型
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 5. 验证图片大小
            String header = response.header("Content-Length");
            if (StrUtil.isNotBlank(header)) {
                try {
                    long contentLength = Long.parseLong(header);
                    final long TWO_MB = 2 * 1024 * 1024L; // 限制文件大小为 2MB
                    ThrowUtils.throwIf(contentLength > TWO_MB, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
}
