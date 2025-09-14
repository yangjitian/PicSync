package com.yudi.cloud.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 兑换码
 */
@Data
public class VipCode implements Serializable {


    private static final long serialVersionUID = -1287020283846832178L;

    // 兑换码
    private String code;

    // 是否已经使用
    private boolean hasUsed;
}