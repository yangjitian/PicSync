package com.yudi.cloud.common;

import com.yudi.cloud.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 全局响应封装
 * @param <T>
 */
@Data
public class BaseResponse<T> implements Serializable {

    private static final long serialVersionUID = 7433764381264802912L;

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
      this(code, data, null);
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }

    public BaseResponse(String message) {
        this(ErrorCode.SUCCESS.getCode(), null, message);
    }

}
