package com.yudi.cloud.model.vo.picture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户对图片的行为状态
 * 
 * @author yudi
 * date 2025-01-27
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPictureActionStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 是否已点赞
     */
    private Boolean liked;

    /**
     * 是否已收藏
     */
    private Boolean collected;

    /**
     * 是否已浏览
     */
    private Boolean viewed;

    /**
     * 点赞时间
     */
    private String likeTime;

    /**
     * 收藏时间
     */
    private String collectTime;

    /**
     * 浏览时间
     */
    private String viewTime;

}