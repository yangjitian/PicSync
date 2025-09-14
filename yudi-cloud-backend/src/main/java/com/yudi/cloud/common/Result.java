package com.yudi.cloud.common;

import com.yudi.cloud.exception.ErrorCode;

public class Result {

    private static final int SUCCESS_CODE = ErrorCode.SUCCESS.getCode();

    public static <T> BaseResponse<T> success(T data){
        return new BaseResponse<>(SUCCESS_CODE,data,"success");
    }

    public static BaseResponse<?> success(String message) {
        return new BaseResponse<>(SUCCESS_CODE, null, message);
    }

    public static BaseResponse<?> error(ErrorCode errorCode){
        return new BaseResponse<>(errorCode);
    }

    public static BaseResponse<?> error(int code, String message){
        return new BaseResponse<>(code,null,message);
    }

    public static BaseResponse<?> error(ErrorCode errorCode, String message){
        return new BaseResponse<>(errorCode.getCode(),null,message);
    }
}
