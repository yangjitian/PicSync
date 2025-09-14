package com.yudi.cloud.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * url上传图片
 */
@Slf4j
@Component
public class UrlPictureUpload extends PictureUploadTemplate {
    @Override
    protected void validPicture(Object inputSource) {
        // 1.非空校验
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(fileUrl == null || fileUrl.trim().isEmpty(),
                ErrorCode.PARAMETER_ERROR, "fileUrl is null or empty");

        // 2.校验 url 格式
        URL url;
        try {
            url = new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "fileUrl is invalid");
        }

        // 3.校验 url 协议
        String protocol = url.getProtocol();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "Only http and https protocols are supported");
        }

        // 4.发送HEAD请求验证文件是否存在
        // 如果是AI生成的链接，则跳过校验
        if (fileUrl.contains("aliyuncs.com/service_dashscope")) {
            log.info("AI-generated URL detected, skipping HEAD request validation.");
        } else {
            HttpResponse httpResponse = null;
            try {
                httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
                if (httpResponse.getStatus() != HttpURLConnection.HTTP_OK) {
                    throw new BusinessException(ErrorCode.PARAMETER_ERROR,
                            "无法访问");
                }

                // 5.校验文件类型
                String contentType = httpResponse.header("Content-Type");
                if (StrUtil.isBlank(contentType)) {
                    final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("image/jpg", "image/jpeg", "image/png",
                            "image/gif", "image/webp", "image/svg");
                    ThrowUtils.throwIf(!ALLOWED_CONTENT_TYPES.contains(contentType), ErrorCode.PARAMETER_ERROR,
                            "file type not supported");
                }

                // 6.校验文件大小
                String contentLengthStr = httpResponse.header("Content-Length");
                if (StrUtil.isNotBlank(contentLengthStr)) {
                    try {
                        long fileSize = Long.parseLong(contentLengthStr);
                        final long MB = 1024 * 1024;
                        ThrowUtils.throwIf(fileSize > 5 * MB, ErrorCode.PARAMETER_ERROR,
                                "fileSize cannot large than 5MB");
                    } catch (NumberFormatException e) {
                        // Content-Length 解析失败，跳过大小校验
                    }
                }
            } finally {
                if (httpResponse != null) {
                    httpResponse.close();
                }
            }
        }
    }

    @Override
    protected String getOriginalFileName(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    @Override
    protected void processFile(Object inputSource, File tempFile) throws IOException {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, tempFile);
    }
}
