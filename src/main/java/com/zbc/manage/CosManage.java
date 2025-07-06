package com.zbc.manage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.zbc.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用的文件上传与下载
 */
@Component
public class CosManage {

    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private COSClient cosClient;

    /**
     * 文件上传
     *
     * @param key  唯一键,简单理解为文件路径
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        String bucketName = cosClientConfig.getBucket();
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, file);
        // 设置存储类型为标准(默认)
        putObjectRequest.setStorageClass(StorageClass.Standard);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载文件到本地
     *
     * @param key 唯一键,简单理解为文件路径
     */
    public COSObject getObject(String key) {
        String bucketName = cosClientConfig.getBucket();
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传并解析图片
     * 数据万象: 图片处理
     * 优化: 压缩图片
     *
     * @param key  唯一键,简单理解为文件路径
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        String bucketName = cosClientConfig.getBucket();
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, file);
        // 对图片进行处理(获取基本信息也被视作为一种处理)
        PicOperations picOperations = new PicOperations();
        // 1: 返回原图信息; 0: 不返回原图信息(默认)
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>(); // 规则集合
        /*
           图片压缩处理
         */
        String webpKey = FileUtil.mainName(key) + ".webp"; // 转换图片格式(优化)
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webpKey);
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosClientConfig.getBucket());
        rules.add(compressRule);
        /*
           缩略图处理, 仅对 > 30kb 的图片进行处理
         */
        if (file.length() > 3 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            // 拼接路径(以防万一设置默认值)
            String suffix = StrUtil.blankToDefault(FileUtil.getSuffix(key), "jpg");
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + suffix;
            thumbnailRule.setFileId(thumbnailKey);
            // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 256, 256));
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            rules.add(thumbnailRule);
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除图片
     *
     * @param key 唯一键,简单理解为文件路径
     */
    public void deleteObject(String key) {
        String bucketName = cosClientConfig.getBucket();
        cosClient.deleteObject(bucketName, key);
    }
}
