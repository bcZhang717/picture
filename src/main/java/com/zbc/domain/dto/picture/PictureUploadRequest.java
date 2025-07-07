package com.zbc.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片上传DTO
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id(用于修改)
     */
    private Long id;

    /**
     * 图片url
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间 id
     */
    private Long spaceId;


    private static final long serialVersionUID = 1L;
}
