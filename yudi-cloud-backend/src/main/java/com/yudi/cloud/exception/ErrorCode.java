package com.yudi.cloud.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAMETER_ERROR(40000, "Parameter Error"),
    NOT_LOGIN_ERROR(40100, "Not Login"),
    NO_AUTH_ERROR(40101, "No Permissions"),
    CANNOT_FOUND_DATA_ERROR(40400, "Cannot Found Data"),
    FORBIDDEN_ERROR(40300, "Forbidden"),
    SYSTEM_ERROR(50000, "System Error"),
    OPERATION_ERROR(50001, "Operation Failed");


    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;


    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
