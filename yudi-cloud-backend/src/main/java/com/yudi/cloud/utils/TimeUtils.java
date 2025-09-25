package com.yudi.cloud.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * 时间工具类 - 统一处理时区问题
 * 确保所有时间都使用北京时间（Asia/Shanghai）
 *
 * @author yudi
 */
public class TimeUtils {

    /**
     * 北京时区ID
     */
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 获取当前北京时间
     * 
     * @return 当前北京时间的Date对象
     */
    public static Date getCurrentBeijingTime() {
        return Date.from(ZonedDateTime.now(BEIJING_ZONE).toInstant());
    }

    /**
     * 获取北京时区ID
     * 
     * @return 北京时区ID
     */
    public static ZoneId getBeijingZone() {
        return BEIJING_ZONE;
    }
}