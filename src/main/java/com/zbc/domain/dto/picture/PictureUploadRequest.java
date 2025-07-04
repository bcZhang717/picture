package com.zbc.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 图片url
     */
    private String fileUrl;

    private static final long serialVersionUID = 1L;
}
