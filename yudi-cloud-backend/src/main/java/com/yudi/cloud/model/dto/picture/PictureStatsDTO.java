package com.yudi.cloud.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片统计数据DTO
 */
@Data
public class PictureStatsDTO implements Serializable {

    /**
     * 总发布数
     */
    private Long totalPictures;

    /**
     * 总点赞数
     */
    private Long totalLikes;

    /**
     * 总收藏数
     */
    private Long totalCollects;

    /**
     * 总浏览量
     */
    private Long totalViews;

    /**
     * 总下载量
     */
    private Long totalDownloads;

    private static final long serialVersionUID = 1L;
}