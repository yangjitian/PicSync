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
        // 图片压缩(webp格式)
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setBucket(cosClientConfig.getBucketName());
        compressRule.setFileId(webpKey);
        compressRule.setRule("imageMogr2/format/webp");
        rules.add(compressRule);
        // 生成略缩图,仅仅对 > 20kb 图片进行处理
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
     * 删除 COS 中的图片及其衍生文件（原图、WebP、缩略图）
     *
     * @param fullKey COS 对象完整路径，如：project/public/1/20250802_a1b2c3d4.jpg
     */
    public void deletePictureObject(String fullKey) {
        if (StrUtil.isBlank(fullKey)) {
            log.warn("COS delete ignored: fullKey is blank");
            return;
        }

        String bucketName = cosClientConfig.getBucketName();
        String mainName = FileUtil.mainName(fullKey); // 包含路径的文件名（无后缀）
        String suffix = FileUtil.getSuffix(fullKey);

        // 明确构建三个文件的 key
        String webpKey = mainName + ".webp";
        String thumbnailKey = mainName + "_thumbnail." + suffix;

        List<String> keysToDelete = Arrays.asList(
                fullKey,
                webpKey,
                thumbnailKey
        );

        for (String key : keysToDelete) {
            try {
                cosClient.deleteObject(bucketName, key);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"Delete failed");
            }
        }
    }

}