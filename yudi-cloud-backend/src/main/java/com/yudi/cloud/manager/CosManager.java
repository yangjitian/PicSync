package com.yudi.cloud.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.yudi.cloud.config.CosClientConfig;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * COS文件操作工具类（封装上传、下载等操作）
 */
@Component
@Slf4j
public class CosManager {

    // 注入Spring管理的COSClient实例（单例，全局复用）
    @Resource
    private COSClient cosClient;

    // 注入配置参数（存储桶名称等）
    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 上传文件到COS存储桶
     *
     * @param key  存储桶内的文件路径（如/test/R-C.jpg）
     * @param file 本地文件（临时文件或本地文件）
     * @return
     */
    public PutObjectResult uploadObject(String key, File file) {
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    cosClientConfig.getBucketName(), key, file
            );
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
            log.info("file upload success, bucket:{}, path:{}", cosClientConfig.getBucketName(), key);
            return putObjectResult;
        } catch (Exception e) {
            log.error("file upload failed, bucket:{}, path:{}", cosClientConfig.getBucketName(), key, e);
            throw new RuntimeException("file upload COS failed", e);
        }
    }

    /**
     * 下载文件
     *
     * @param key
     */
    public COSObject downloadObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucketName(),key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传文件（图片）
     *
     * @param key
     * @param file
     * @return
     */
    public PutObjectResult uploadPictureObject(String key, File file) throws CosClientException {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucketName(), key, file);
        // 图片处理
        PicOperations picOperations = new PicOperations();
        // 返回原始信息
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>();
        
        // 生成WebP格式（用于优化显示，但不替换原图）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule webpRule = new PicOperations.Rule();
        webpRule.setBucket(cosClientConfig.getBucketName());
        webpRule.setFileId(webpKey);
        webpRule.setRule("imageMogr2/format/webp");
        rules.add(webpRule);
        
        // 生成缩略图,仅仅对 > 20kb 图片进行处理
        if (file.length() > 20 * 1024) {
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            thumbnailRule.setBucket(cosClientConfig.getBucketName());
            thumbnailRule.setFileId(thumbnailKey);
            // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 256, 256));
            rules.add(thumbnailRule);
        }
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除 COS 中的图片及其衍生文件（原图、WebP，保留缩略图）
     *
     * @param fullKey COS 对象完整路径，如：yudiPicSync/public/1/avatar.jpg
     */
    public void deletePictureObject(String fullKey) {
        ThrowUtils.throwIf(StrUtil.isBlank(fullKey),ErrorCode.PARAMETER_ERROR,"fullKey is blank");

        String bucketName = cosClientConfig.getBucketName();
        // 使用字符串处理提取路径和文件名，移除扩展名，保留完整路径
        String mainName = fullKey.substring(0, fullKey.lastIndexOf('.'));
        // yudiPicSync/public/1/avatar
        String webpKey = mainName + ".webp";
        List<String> keysToDelete = Arrays.asList(
                fullKey,        // 原图   yudiPicSync/public/1/avatar.jpg
                webpKey         // WebP版本  yudiPicSync/public/1/avatar.webp
        );

        for (String key : keysToDelete) {
            try {
                // 先检查文件是否存在
                boolean exists = cosClient.doesObjectExist(bucketName, key);
                if (exists) {
                    cosClient.deleteObject(bucketName, key);
                }
            } catch (Exception e) {
                log.error("Failed to delete COS file: {}, error: {}", key, e.getMessage(), e);
                // 不抛出异常，继续删除其他文件
            }
        }
    }

    /**
     * 强制删除 COS 中的图片及其衍生文件（不检查存在性，直接删除）
     * 用于处理WebP文件可能存在的异步生成问题
     *
     * @param fullKey COS 对象完整路径
     */
    public void forceDeletePictureObject(String fullKey) {
        ThrowUtils.throwIf(StrUtil.isBlank(fullKey),ErrorCode.PARAMETER_ERROR);

        String bucketName = cosClientConfig.getBucketName();
        String mainName = fullKey.substring(0, fullKey.lastIndexOf('.'));
        String webpKey = mainName + ".webp";
        List<String> keysToDelete = Arrays.asList(
                fullKey,
                webpKey
        );

        for (String key : keysToDelete) {
            try {
                cosClient.deleteObject(bucketName, key);
            } catch (Exception e) {
                // 如果是文件不存在的错误，记录为警告而不是错误
                if (e.getMessage() != null && e.getMessage().contains("NoSuchKey")) {
                    log.warn("File does not exist (NoSuchKey): {}", key);
                } else {
                    log.error("Failed to force delete COS file: {}, error: {}", key, e.getMessage(), e);
                }
            }
        }
    }

}