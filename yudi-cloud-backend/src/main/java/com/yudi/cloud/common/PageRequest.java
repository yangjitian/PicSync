package com.yudi.cloud.common;

import lombok.Data;

@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private Integer current = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认升序）
     */
    private String sortOrder = "descend";
}
