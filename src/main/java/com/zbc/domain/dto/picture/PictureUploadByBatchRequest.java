package com.zbc.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量导入图片
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {
    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 抓取数量
     */
    private Integer count = 10;

    private static final long serialVersionUID = 1L;
}
