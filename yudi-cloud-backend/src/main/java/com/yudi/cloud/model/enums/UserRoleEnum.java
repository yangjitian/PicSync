package com.yudi.cloud.model.enums;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.Getter;

@Getter
public enum UserRoleEnum {
    USER("普通用户","user"),
    ADMIN("管理员","admin"),
    VIP("VIP用户","vip");

    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static UserRoleEnum getEnumByValue(String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            if (userRoleEnum.getValue().equals(value)) {
                return userRoleEnum;
            }
        }
        return null;
    }
}
