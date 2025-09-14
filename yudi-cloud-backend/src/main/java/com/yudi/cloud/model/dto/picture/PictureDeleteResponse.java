package com.yudi.cloud.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片删除响应
 *
 * @author yudi
 */
@Data
public class PictureDeleteResponse implements Serializable {

    /**
     * 是否删除成功
     */
    private Boolean success;

    /**
     * 原图片所属空间ID（删除后跳转目标）
     */
    private Long spaceId;

    /**
     * 是否为公共图库图片
     */
    private Boolean isPublicPicture;

    private static final long serialVersionUID = 1L;
}