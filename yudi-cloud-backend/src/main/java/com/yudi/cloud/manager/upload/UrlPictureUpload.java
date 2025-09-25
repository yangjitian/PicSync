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
import java.net.URLDecoder;
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
        
        // 首先尝试从URL参数中提取真实的图片文件名（处理Bing重定向等）
        String fileName = extractFileNameFromUrlParams(fileUrl);
        
        // 如果从参数中提取失败，使用传统方法
        if (fileName == null || fileName.isEmpty()) {
            fileName = FileUtil.getName(fileUrl);
            // 如果URL中包含查询参数，需要去掉
            if (fileName.contains("?")) {
                fileName = fileName.split("\\?")[0];
            }
        }
        
        // 兜底处理：如果提取的文件名没有扩展名，尝试从Content-Type获取
        if (!fileName.contains(".")) {
            try {
                // 发送HEAD请求获取Content-Type
                HttpResponse response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
                String contentType = response.header("Content-Type");
                if (contentType != null && contentType.startsWith("image/")) {
                    String extension = contentType.substring(6); // 去掉"image/"前缀
                    // 处理一些特殊的Content-Type
                    if ("jpeg".equals(extension)) {
                        extension = "jpg";
                    }
                    fileName = fileName + "." + extension;
                } else {
                    // 如果无法获取Content-Type或不是图片，默认使用jpg扩展名
                    fileName = fileName + ".jpg";
                }
            } catch (Exception e) {
                log.warn("Failed to get Content-Type for URL: {}, using default extension", fileUrl, e);
                // 兜底：默认使用jpg扩展名
                fileName = fileName + ".jpg";
            }
        }
        
        // 确保文件名不会太长，避免路径问题
        if (fileName.length() > 100) {
            String extension = FileUtil.getSuffix(fileName);
            String nameWithoutExt = FileUtil.mainName(fileName);
            if (nameWithoutExt.length() > 95) {
                nameWithoutExt = nameWithoutExt.substring(0, 95);
            }
            fileName = nameWithoutExt + "." + extension;
        }
        
        return fileName;
    }
    
    /**
     * 从URL参数中提取真实的图片文件名
     * 处理类似Bing图片重定向链接的情况
     * @param fileUrl 原始URL
     * @return 提取的文件名，如果提取失败返回null
     */
    private String extractFileNameFromUrlParams(String fileUrl) {
        try {
            // 解析URL
            URL url = new URL(fileUrl);
            String query = url.getQuery();
            if (query == null || query.isEmpty()) {
                return null;
            }
            
            // 查找常见的图片URL参数（按优先级排序）
            String[] paramNames = {"riu", "url", "src", "image", "img", "file", "link"};
            
            for (String paramName : paramNames) {
                String paramValue = getUrlParameter(query, paramName);
                if (paramValue != null && !paramValue.isEmpty()) {
                    // URL解码
                    paramValue = URLDecoder.decode(paramValue, "UTF-8");
                    
                    // 检查是否是有效的图片URL
                    if (isValidImageUrl(paramValue)) {
                        String fileName = FileUtil.getName(paramValue);
                        if (fileName.contains(".")) {
                            log.info("Extracted filename from URL param '{}': {} -> {}", paramName, paramValue, fileName);
                            return fileName;
                        }
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract filename from URL params: {}", fileUrl, e);
            return null;
        }
    }
    
    /**
     * 从查询字符串中获取指定参数的值
     */
    private String getUrlParameter(String query, String paramName) {
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && paramName.equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
    
    /**
     * 检查是否是有效的图片URL
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        // 检查是否包含常见的图片扩展名
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") || 
               lowerUrl.contains(".png") || lowerUrl.contains(".gif") || 
               lowerUrl.contains(".webp") || lowerUrl.contains(".bmp") ||
               lowerUrl.contains(".tiff") || lowerUrl.contains(".svg");
    }

    @Override
    protected void processFile(Object inputSource, File tempFile) throws IOException {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, tempFile);
    }
}
