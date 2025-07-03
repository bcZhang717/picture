package com.zbc.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class PictureTagCategory {
    /**
     * 标签列表
     */
    List<String> tagList;
    /**
     * 分类列表
     */
    List<String> categoryList;
}
