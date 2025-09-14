package com.yudi.cloud.utils;

import java.awt.Color;

/**
 * 颜色相似度工具类
 */
public class ColorSimilarUtils {

    /**
     * 私有构造函数，防止实例化
     */
    private ColorSimilarUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 计算两种颜色之间的欧几里得距离
     *
     * @param hexColor1 颜色的十六进制表示 (例如, "#RRGGBB")
     * @param hexColor2 颜色的十六进制表示 (例如, "#RRGGBB")
     * @return 颜色之间的欧几里得距离. 距离越小，颜色越相似.
     */
    public static double calculateColorSimilarity(String hexColor1, String hexColor2) {
        Color color1 = Color.decode(hexColor1);
        Color color2 = Color.decode(hexColor2);

        return calculateColorSimilarity(color1, color2);
    }

    /**
     * 计算两种颜色之间的欧几里得距离
     *
     * @param color1 java.awt.Color 对象
     * @param color2 java.awt.Color 对象
     * @return 颜色之间的欧几里得距离. 距离越小，颜色越相似.
     */
    public static double calculateColorSimilarity(Color color1, Color color2) {
        long r1 = color1.getRed();
        long g1 = color1.getGreen();
        long b1 = color1.getBlue();
        long r2 = color2.getRed();
        long g2 = color2.getGreen();
        long b2 = color2.getBlue();

        double distance = Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));

        // 计算相似度
        return 1 - distance / Math.sqrt(3 * Math.pow(255, 2));
    }

}