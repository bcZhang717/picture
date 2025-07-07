package com.zbc.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 工具类：计算颜色相似度
 */
public class ColorTransformUtils {

    private ColorTransformUtils() {
        // 工具类不需要实例化
    }

    /**
     * 获取标准颜色(数据万象5位转化为6位)
     *
     * @param color 颜色
     * @return 标准颜色
     */
    public static String getStandardColor(String color) {
        if (StrUtil.isNotBlank(color)) {
            if (color.length() == 7) {
                color = color.substring(0, 4) + "0" + color.substring(4, 7);
            }
        }
        return color;
    }
}
