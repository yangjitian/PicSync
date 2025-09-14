package com.yudi.cloud.manager;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.transfer.Upload;
import com.yudi.cloud.common.Result;
import com.yudi.cloud.config.CosClientConfig;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.model.dto.picture.PictureUploadDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.*;

/**
 * 弃用
 */
@Deprecated
@Component
@Slf4j
public class FileManager {

    final String PROJECT_NAME = "yudiPicSync";

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片到云存储
     *
     * @param multipartFile    上传的图片文件
     * @param uploadPathPrefix 上传路径前缀（如：avatar、post等）
     * @return 包含图片信息的DTO对象
     */
    public PictureUploadDTO uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 验证图片
        validPicture(multipartFile);
        String dateStr = DateUtil.format(new Date(), "yyyyMMdd");
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 拼接新的文件名，20250802_a1b2c3d4.jpg
        String uploadFileName = String.format("%s_%s.%s", dateStr, uuid, suffix);
        // 构建完整的上传路径,/avatar/20250802_a1b2c3d4.jpg
        String uploadPath = String.format(PROJECT_NAME + "/%s/%s", uploadPathPrefix, uploadFileName);
        // 创建临时文件，用于暂存上传的图片数据
        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload_", "_temp");
            // 将MultipartFile的内容转存到临时文件
            multipartFile.transferTo(tempFile);
            // 调用COS管理器上传文件到腾讯云对象存储，返回上传结果
            PutObjectResult putObjectResult = cosManager.uploadPictureObject(uploadPath, tempFile);
            // 提取图片信息
            return getPictureUploadDTO(multipartFile, putObjectResult, uploadPath, tempFile);
        } catch (Exception e) {
            log.error("picture upload failed", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "picture upload error");
        } finally {
            deleteTemp(tempFile);
        }
    }

    /**
     * 构建图片上传DTO
     *
     * @param multipartFile   原始文件
     * @param putObjectResult 上传到COS返回的结果
     * @param uploadPath      上传路径
     * @param tempFile        临时文件
     * @return
     */
    private PictureUploadDTO getPictureUploadDTO(MultipartFile multipartFile, PutObjectResult putObjectResult, String uploadPath, File tempFile) {
        ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        // 创建返回结果DTO对象
        PictureUploadDTO pictureUploadDTO = new PictureUploadDTO();
        // 设置图片的完整访问URL（COS域名 + 上传路径）
        pictureUploadDTO.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        pictureUploadDTO.setPicName(FileUtil.mainName(multipartFile.getOriginalFilename()));
        pictureUploadDTO.setPicSize(FileUtil.size(tempFile));
        pictureUploadDTO.setPicWidth(picWidth);
        pictureUploadDTO.setPicHeight(picHeight);
        pictureUploadDTO.setPicScale(picScale);
        pictureUploadDTO.setPicFormat(imageInfo.getFormat());
        return pictureUploadDTO;
    }


    /**
     * 校验文件
     *
     * @param multipartFile
     */
    private void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMETER_ERROR, "file cannot be empty");
        long fileSize = multipartFile.getSize();
        final long MB = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > 5 * MB, ErrorCode.PARAMETER_ERROR, "file size can`t larger than 5MB");
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMETER_ERROR, "file type error");
    }

    /**
     * 清理临时文件
     *
     * @param tempFile
     */
    private void deleteTemp(File tempFile) {
        if (tempFile == null) {
            return;
        }
        boolean result = tempFile.delete();
        if (!result) {
            log.error("file delete failed,filePath:{}", tempFile.getAbsoluteFile());
        }
    }
}
