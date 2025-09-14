package com.yudi.cloud.model.dto.picture;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 批量编辑图片请求
 *
 * @author yudi
 */
@Data
public class PictureEditByBatchRequest implements Serializable {

    /**
     * 空间 id (必需)
     */
    private Long spaceId;

    /**
     * 要修改的图片 id 列表 (必需)
     */
    private List<Long> pictureIdList;

    /**
     * 新分类 (可选)
     */
    private String category;

    /**
     * 新标签列表 (可选)
     */
    private List<String> tags;

    /**
     * 新的命名规则
     */
    private String nameRule;

    private static final long serialVersionUID = 1L;
}
