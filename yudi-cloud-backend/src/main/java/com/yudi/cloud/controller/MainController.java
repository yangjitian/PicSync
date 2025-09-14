package com.yudi.cloud.controller;

import com.yudi.cloud.common.BaseResponse;
import com.yudi.cloud.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public BaseResponse<?> health() {
        return Result.success("ok");
    }

}
