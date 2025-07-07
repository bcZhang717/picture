package com.zbc.domain.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量操作DTO
 * 批量修改图片
 * 批量重命名图片
 */
@Data
public class PictureEditByBatchRequest implements Serializable {

    /**
     * 图片 id 列表
     */
    private List<Long> pictureIdList;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 命名规则
     */
    private String nameRule;

    private static final long serialVersionUID = 1L;
}
