package com.zbc.domain.vo;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.zbc.domain.pojo.Picture;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class PictureVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 标签(注意类型, 与数据库字段类型不一致, 需要特别处理)
     */
    private List<String> tags;

    /**
     * 权限列表
     */
    private List<String> permissionList = new ArrayList<>();


    /**
     * 分类
     */
    private String category;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 缩略图Url
     */
    private String thumbnailUrl;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息(非数据库字段)
     */
    private UserVO user;

    private static final long serialVersionUID = 1L;

    /**
     * VO类转实体类
     */
    public static Picture VOtoObject(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureVO, picture);
        List<String> tagsList = pictureVO.getTags();
        String jsonStr = JSONUtil.toJsonStr(tagsList);
        picture.setTags(jsonStr);
        return picture;
    }

    /**
     * 实体类转VO类
     */
    public static PictureVO objectToVO(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();
        BeanUtil.copyProperties(picture, pictureVO);
        String tags = picture.getTags();
        List<String> list = JSONUtil.toList(tags, String.class);
        pictureVO.setTags(list);
        return pictureVO;
    }
}
