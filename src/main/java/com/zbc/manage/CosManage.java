package com.zbc.manage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.zbc.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

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
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }
}
