package com.zbc.domain.dto.file;

import lombok.Data;

/**
 * 图片基本信息, 用于解析图片结果
 */
@Data
public class UploadPictureResult {

    /**
     * 图片地址
     */
    private String url;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 图片体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private int picWidth;

    /**
     * 图片高度
     */
    private int picHeight;

    /**
     * 图片宽高比
     */
    private Double picScale;
    /**
     * 缩略图Url
     */
    private String thumbnailUrl;

    /**
     * 图片格式
     */
    private String picFormat;

}
