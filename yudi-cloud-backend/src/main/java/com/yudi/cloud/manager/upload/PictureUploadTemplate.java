package com.yudi.cloud.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.yudi.cloud.config.CosClientConfig;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.manager.CosManager;
import com.yudi.cloud.model.dto.picture.PictureUploadDTO;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 图片上传模版
 */
@Slf4j
public abstract class PictureUploadTemplate {

    final String PROJECT_NAME = "yudiPicSync";

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片到云存储
     *
     * @param inputSource      上传源
     * @param uploadPathPrefix 上传路径前缀（如：avatar、post等）
     * @return 包含图片信息的DTO对象
     */
    public PictureUploadDTO uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 验证图片
        validPicture(inputSource);
        String dateStr = DateUtil.format(new Date(), "yyyyMMdd");
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String originalFileName = getOriginalFileName(inputSource);
        String suffix = FileUtil.getSuffix(originalFileName);
        // 拼接新的文件名，20250802_a1b2c3d4.jpg
        String uploadFileName = String.format("%s_%s.%s", dateStr, uuid, suffix);
        // 处理ai扩图的文件路径
        if (uploadFileName.contains("?")) {
            uploadFileName = uploadFileName.split("\\?")[0];
        }
        // 构建完整的上传路径,/avatar/20250802_a1b2c3d4.jpg
        String uploadPath = String.format(PROJECT_NAME + "/%s/%s", uploadPathPrefix, uploadFileName);
        // 创建临时文件，用于暂存上传的图片数据
        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload_", "_temp");
            // 处理文件来源
            processFile(inputSource, tempFile);
            // 调用COS管理器上传文件到腾讯云对象存储，返回上传结果
            PutObjectResult putObjectResult = cosManager.uploadPictureObject(uploadPath, tempFile);
            // 提取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 获得图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                CIObject compressedCiObject = objectList.get(0);
                CIObject thumbnailObject = compressedCiObject;
                if (objectList.size() > 1) {
                    thumbnailObject = objectList.get(1);
                }
                return getPictureUploadDTO(originalFileName, compressedCiObject, thumbnailObject, imageInfo, uploadPath);
            }
            return getPictureUploadDTO(originalFileName, imageInfo, uploadPath, tempFile);
        } catch (Exception e) {
            log.error("picture upload failed", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "picture upload error");
        } finally {
            deleteTemp(tempFile);
        }
    }

    /**
     * 对输入源进行校验(file or url)
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源原始名称
     */
    protected abstract String getOriginalFileName(Object inputSource);

    /**
     * 处理输入源并生成临时文件
     */
    protected abstract void processFile(Object inputSource, File tempFile) throws IOException;

    /**
     * 封装返回结果
     *
     * @param originalFilename   原始文件名
     * @param compressedCiObject 压缩后的对象
     * @param thumbnailCiObject  缩略图对象
     * @param imageInfo          图片信息
     * @param uploadPath         上传路径
     * @return
     */
    private PictureUploadDTO getPictureUploadDTO(String originalFilename, CIObject compressedCiObject, CIObject thumbnailCiObject,
                                                 ImageInfo imageInfo, String uploadPath) {
        // 计算宽高
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        // 封装返回结果
        PictureUploadDTO pictureUploadDTO = new PictureUploadDTO();
        // 设置原图地址（保持原始格式）
        pictureUploadDTO.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        pictureUploadDTO.setPicName(FileUtil.mainName(originalFilename));
        pictureUploadDTO.setPicSize(compressedCiObject.getSize().longValue());
        pictureUploadDTO.setPicWidth(picWidth);
        pictureUploadDTO.setPicHeight(picHeight);
        pictureUploadDTO.setPicScale(picScale);
        // 使用ImageInfo检测到的实际图片格式，而不是文件名扩展名
        // ImageInfo.getFormat()返回的是腾讯云COS检测到的真实图片格式
        String actualFormat = imageInfo.getFormat();
        // 统一格式名称：jpeg -> jpg
        if ("jpeg".equals(actualFormat)) {
            actualFormat = "jpg";
        }
        pictureUploadDTO.setPicFormat(actualFormat);
        pictureUploadDTO.setPicColor(imageInfo.getAve());
        // 设置缩略图地址
        if (compressedCiObject.getSize().longValue() > 20 * 1024) {
            pictureUploadDTO.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        }

        // 设置WebP格式地址（根据上传路径生成）
        String webpKey = FileUtil.mainName(uploadPath) + ".webp";
        pictureUploadDTO.setWebpUrl(cosClientConfig.getHost() + "/" + webpKey);

        // 返回可访问的地址
        return pictureUploadDTO;
    }


    /**
     * 构建图片上传DTO
     *
     * @param originalFileName 原始文件
     * @param imageInfo        图片信息
     * @param uploadPath       上传路径
     * @param tempFile         临时文件
     * @return
     */
    private PictureUploadDTO getPictureUploadDTO(String originalFileName, ImageInfo imageInfo, String uploadPath, File tempFile) {
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        // 创建返回结果DTO对象
        PictureUploadDTO pictureUploadDTO = new PictureUploadDTO();
        // 设置图片的完整访问URL（COS域名 + 上传路径）
        pictureUploadDTO.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        pictureUploadDTO.setPicName(FileUtil.mainName(originalFileName));
        pictureUploadDTO.setPicSize(FileUtil.size(tempFile));
        pictureUploadDTO.setPicWidth(picWidth);
        pictureUploadDTO.setPicHeight(picHeight);
        pictureUploadDTO.setPicScale(picScale);
        // 使用ImageInfo检测到的实际图片格式
        String actualFormat = imageInfo.getFormat();
        // 统一格式名称：jpeg -> jpg
        if ("jpeg".equals(actualFormat)) {
            actualFormat = "jpg";
        }
        pictureUploadDTO.setPicFormat(actualFormat);
        pictureUploadDTO.setPicColor(imageInfo.getAve());

        // 设置WebP格式地址（根据上传路径生成）
        String webpKey = FileUtil.mainName(uploadPath) + ".webp";
        pictureUploadDTO.setWebpUrl(cosClientConfig.getHost() + "/" + webpKey);

        // 只有当图片大于20kb时才设置缩略图URL
        if (FileUtil.size(tempFile) > 20 * 1024) {
            String thumbnailKey = FileUtil.mainName(uploadPath) + "_thumbnail." + FileUtil.getSuffix(uploadPath);
            pictureUploadDTO.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailKey);
        }

        return pictureUploadDTO;
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
