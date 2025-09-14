package com.yudi.cloud.controller;

import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.yudi.cloud.annotation.AuthCheck;
import com.yudi.cloud.common.BaseResponse;
import com.yudi.cloud.common.Result;
import com.yudi.cloud.contstant.UserConstant;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.manager.CosManager;
import com.yudi.cloud.model.enums.UserRoleEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;

/**
 * 通用文件请求
 */

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private CosManager cosManager;

    @PostMapping("/upload")
    public BaseResponse<?> upload(@RequestPart("file") MultipartFile file) {
        // 1. 验证文件
        if (file.isEmpty()) {
            return Result.error(ErrorCode.PARAMETER_ERROR, "file cannot be empty");
        }
        // 2. 构建存储路径（仅存储桶内的相对路径）
        String filename = file.getOriginalFilename();
        String filePath = "/test/" + filename;
        // 3. 处理临时文件并上传
        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload_", "_temp");
            file.transferTo(tempFile);
            cosManager.uploadObject(filePath, tempFile); // 调用上传方法
            return Result.success("上传成功，路径：" + filePath);
        } catch (Exception e) {
            return Result.error(ErrorCode.SYSTEM_ERROR,e.getMessage());
        } finally {
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                boolean result = tempFile.delete();
                if (!result) {
                    log.error("file delete failed,filePath:{}", tempFile);
                }
            }
        }
    }

    @PostMapping("/download")
    public void download(String filePath, HttpServletResponse response) {
        COSObjectInputStream cosObjectInput = null;
        try {
            COSObject cosObject = cosManager.downloadObject(filePath);
            cosObjectInput = cosObject.getObjectContent();
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition","attachment; filename=\"" +
                    URLEncoder.encode(filePath,"UTF-8") + "\"");
            try (OutputStream out = response.getOutputStream()) {
                out.write(bytes);
                out.flush();
            }
        } catch (IOException e) {
            log.error("File download failed,filePath:{}", filePath,e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,e.getMessage());
        }finally {
            if (cosObjectInput != null) {
                try {
                    cosObjectInput.close();
                } catch (IOException e) {
                    log.error("file close failed");
                }
            }
        }
    }
}