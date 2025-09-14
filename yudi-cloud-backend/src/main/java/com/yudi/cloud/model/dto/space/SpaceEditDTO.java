package com.yudi.cloud.model.dto.space;

import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceEditDTO implements Serializable {

    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    private static final long serialVersionUID = -4513658283463350457L;
}

