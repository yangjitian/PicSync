package com.yudi.cloud.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 按颜色搜索图片请求
 *
 * @author yudi
 */
@Data
public class SearchPictureByColorRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 图片主色调 (例如, "#RRGGBB")
     */
    private String picColor;

    private static final long serialVersionUID = 1L;
}